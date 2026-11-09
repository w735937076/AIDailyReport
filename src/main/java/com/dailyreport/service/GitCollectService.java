package com.dailyreport.service;

import com.dailyreport.config.AppConfig;
import com.dailyreport.domain.GitCommitLog;
import com.dailyreport.mapper.GitCommitLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class GitCollectService {

    @Autowired
    private GitCommitLogMapper commitLogMapper;

    @Autowired
    private AppConfig appConfig;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
            } else {
                return collectFromLocal(repoName, repoPath);
            }
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

        int count = 0;
        try (Git git = Git.open(repoDir)) {
            LogCommand logCmd = git.log().setMaxCount(100);

            Iterable<RevCommit> commits = logCmd.call();
            for (RevCommit commit : commits) {
                if (processCommit(git, repoName, commit)) {
                    count++;
                }
            }
        } catch (Exception e) {
            log.error("读取本地仓库 {} 失败: {}", repoPath, e.getMessage());
        }

        log.info("仓库 {} 采集完成，新增 {} 条记录", repoName, count);
        return count;
    }

    private int collectFromRemote(String repoName, String remoteUrl, String branch) {
        // 临时克隆远程仓库
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
            // 清理临时目录
            deleteDirectory(tempDir);
        }
    }

    private boolean processCommit(Git git, String repoName, RevCommit commit) {
        String commitId = commit.getId().getName();
        String authorName = commit.getAuthorIdent().getName();

        // 如果配置了用户过滤，只采集该用户的提交
        String filterUser = appConfig.getGit().getAccount().getUsername();
        if (filterUser != null && !filterUser.isEmpty()
                && !authorName.equalsIgnoreCase(filterUser)) {
            log.debug("跳过非用户 {} 的提交: {} by {}", filterUser, commitId, authorName);
            return false;
        }

        // 检查是否已存在
        GitCommitLog existing = commitLogMapper.findByCommitId(repoName, commitId);
        if (existing != null) {
            return false;
        }

        // 获取 diff
        String diffText = getCommitDiff(git, commit);

        // 转换为 LocalDateTime - 使用 commit.getCommitTime() 获取 Unix 时间戳
        int commitTimeSeconds = commit.getCommitTime();
        LocalDateTime commitTime = LocalDateTime.ofEpochSecond(
                commitTimeSeconds, 0, ZoneId.systemDefault().getRules().getOffset(java.time.Instant.EPOCH));

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

            // 获取父提交
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
                // 初始提交，获取所有文件
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
