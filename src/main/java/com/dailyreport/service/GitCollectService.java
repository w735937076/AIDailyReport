package com.dailyreport.service;

import com.dailyreport.config.AppConfig;
import com.dailyreport.domain.GitCommitLog;
import com.dailyreport.mapper.GitCommitLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class GitCollectService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private GitCommitLogMapper commitLogMapper;

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private SettingsService settingsService;

    public int collectFromRepo(AppConfig.RepoConfig repoConfig) {
        String repoName = repoConfig.getName();
        String repoPath = repoConfig.getPath();
        String remote = repoConfig.getRemote();

        log.info("开始采集仓库: {} ({})", repoName, repoPath != null ? repoPath : remote);

        if (repoPath == null && remote == null) {
            log.warn("仓库 {} 缺少 path 或 remote 配置，跳过", repoName);
            return 0;
        }

        try {
            if (remote != null && !remote.isEmpty()) {
                return collectFromRemote(repoName, remote, repoConfig.getBranch());
            }
            return collectFromLocal(repoName, repoPath);
        } catch (Exception e) {
            log.error("采集仓库 {} 失败: {}", repoName, e.getMessage(), e);
            return 0;
        }
    }

    private int collectFromLocal(String repoName, String repoPath) {
        File repoDir = new File(repoPath);
        if (!repoDir.exists() || !repoDir.isDirectory()) {
            log.warn("仓库路径不存在: {}", repoPath);
            return 0;
        }

        ZonedDateTime startOfToday = ZonedDateTime.now(SHANGHAI).toLocalDate().atStartOfDay(SHANGHAI);
        Date since = Date.from(startOfToday.toInstant());
        String filterUser = resolveFilterUsername();

        int totalNew = 0;
        try (Git git = Git.open(repoDir)) {
            List<Ref> localBranches = git.branchList()
                    .setListMode(ListBranchCommand.ListMode.ALL)
                    .call();

            List<String> activeBranches = new ArrayList<>();
            for (Ref ref : localBranches) {
                String fullName = ref.getName();
                if (!fullName.startsWith("refs/heads/")) {
                    continue;
                }
                String branchName = fullName.substring("refs/heads/".length());
                if (branchHasAuthorCommitsSince(git, ref, since, filterUser)) {
                    activeBranches.add(branchName);
                }
            }

            if (activeBranches.isEmpty()) {
                log.info("仓库 {} 今日（{} 起）无匹配作者「{}」的本地分支提交，新增 0 条",
                        repoName, startOfToday.format(FORMATTER), filterUser == null ? "（全部）" : filterUser);
                return 0;
            }

            log.info("仓库 {} 今日活跃本地分支: {}", repoName, activeBranches);

            for (Ref ref : localBranches) {
                String fullName = ref.getName();
                if (!fullName.startsWith("refs/heads/")) {
                    continue;
                }
                String branchName = fullName.substring("refs/heads/".length());
                if (!activeBranches.contains(branchName)) {
                    continue;
                }

                int branchNew = 0;
                LogCommand logCmd = git.log()
                        .add(ref.getObjectId())
                        .setRevFilter(sinceRevFilter(since));
                for (RevCommit commit : logCmd.call()) {
                    if (processCommit(git, repoName, commit, filterUser)) {
                        branchNew++;
                        totalNew++;
                    }
                }
                log.info("仓库 {} 分支 {} 新增 {} 条提交", repoName, branchName, branchNew);
            }
        } catch (Exception e) {
            log.error("读取本地仓库 {} 失败: {}", repoPath, e.getMessage());
        }

        log.info("仓库 {} 采集完成，新增 {} 条记录", repoName, totalNew);
        return totalNew;
    }

    private RevFilter sinceRevFilter(Date since) {
        return CommitTimeRevFilter.after(since);
    }

    private boolean branchHasAuthorCommitsSince(Git git, Ref branchRef, Date since, String filterUser) {
        try {
            for (RevCommit commit : git.log()
                    .add(branchRef.getObjectId())
                    .setRevFilter(sinceRevFilter(since))
                    .call()) {
                if (authorMatches(commit, filterUser)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("检查分支 {} 活跃度失败: {}", branchRef.getName(), e.getMessage());
        }
        return false;
    }

    private String resolveFilterUsername() {
        String filterUser = settingsService.getGitFilterUsername();
        if (filterUser == null || filterUser.isBlank()) {
            filterUser = appConfig.getGit().getAccount().getUsername();
        }
        if (filterUser != null && filterUser.isBlank()) {
            return null;
        }
        return filterUser;
    }

    private boolean authorMatches(RevCommit commit, String filterUser) {
        if (filterUser == null || filterUser.isEmpty()) {
            return true;
        }
        return commit.getAuthorIdent().getName().equalsIgnoreCase(filterUser);
    }

    private int collectFromRemote(String repoName, String remoteUrl, String branch) {
        File tempDir = new File("./data/temp-repo-" + System.currentTimeMillis());
        try {
            log.info("克隆远程仓库: {}", remoteUrl);

            try (Git git = Git.cloneRepository()
                    .setURI(remoteUrl)
                    .setDirectory(tempDir)
                    .setBranch(branch != null ? branch : "main")
                    .setCloneAllBranches(false)
                    .call()) {
                // clone successful
            }

            int count = collectFromLocal(repoName, tempDir.getAbsolutePath());

            log.info("远程仓库 {} 采集完成，新增 {} 条记录", repoName, count);
            return count;
        } catch (Exception e) {
            log.error("克隆远程仓库 {} 失败: {}", remoteUrl, e.getMessage());
            return 0;
        } finally {
            deleteDirectory(tempDir);
        }
    }

    private boolean processCommit(Git git, String repoName, RevCommit commit, String filterUser) {
        String commitId = commit.getId().getName();
        if (!authorMatches(commit, filterUser)) {
            log.debug("跳过非用户 {} 的提交: {} by {}", filterUser, commitId, commit.getAuthorIdent().getName());
            return false;
        }

        GitCommitLog existing = commitLogMapper.findByCommitId(repoName, commitId);
        if (existing != null) {
            return false;
        }

        String diffText = getCommitDiff(git, commit);

        LocalDateTime commitTime = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(commit.getCommitTime()), SHANGHAI);

        GitCommitLog logEntry = new GitCommitLog();
        logEntry.setRepoName(repoName);
        logEntry.setCommitId(commitId);
        logEntry.setAuthor(commit.getAuthorIdent().getName());
        logEntry.setAuthorEmail(commit.getAuthorIdent().getEmailAddress());
        logEntry.setCommitTime(commitTime);
        logEntry.setMessage(commit.getFullMessage());
        logEntry.setDiffText(diffText);
        logEntry.setParsed(false);

        commitLogMapper.insert(logEntry);
        return true;
    }

    private String getCommitDiff(Git git, RevCommit commit) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             DiffFormatter formatter = new DiffFormatter(out)) {

            formatter.setRepository(git.getRepository());
            formatter.setDetectRenames(true);

            RevCommit parent = null;
            if (commit.getParentCount() > 0) {
                try (RevWalk rw = new RevWalk(git.getRepository())) {
                    parent = rw.parseCommit(commit.getParent(0).getId());
                }
            }

            List<DiffEntry> diffs;
            if (parent != null) {
                diffs = formatter.scan(parent.getTree(), commit.getTree());
            } else {
                diffs = formatter.scan(null, commit.getTree());
            }

            StringBuilder sb = new StringBuilder();
            for (DiffEntry entry : diffs) {
                formatter.format(entry);
                sb.append(entry.getNewPath()).append("\n");
                sb.append(out.toString("UTF-8"));
                sb.append("\n---\n");
                out.reset();
            }

            return sb.toString();
        } catch (Exception e) {
            log.warn("获取 commit {} diff 失败: {}", commit.getId().getName(), e.getMessage());
            return "";
        }
    }

    public List<GitCommitLog> getUnparsedCommits() {
        return commitLogMapper.findUnparsed();
    }

    public List<GitCommitLog> getUnparsedCommitsForRepo(String repoName) {
        return commitLogMapper.findUnparsedByRepo(repoName);
    }

    public void markAsParsed(Long id) {
        commitLogMapper.markAsParsed(id);
    }

    private void deleteDirectory(File dir) {
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            dir.delete();
        }
    }
}
