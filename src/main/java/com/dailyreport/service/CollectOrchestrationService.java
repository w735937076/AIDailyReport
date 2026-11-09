package com.dailyreport.service;

import com.dailyreport.config.AppConfig;
import com.dailyreport.domain.GitCommitLog;
import com.dailyreport.web.dto.CollectResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class CollectOrchestrationService {

    private final SettingsService settingsService;
    private final GitCollectService gitCollectService;
    private final AiParseService aiParseService;
    private final JobService jobService;
    private final Executor executor;
    private final AtomicReference<String> lastStatus = new AtomicReference<>("IDLE");
    private final AtomicReference<CollectResult> lastResult = new AtomicReference<>();

    public CollectOrchestrationService(SettingsService settingsService,
                                       GitCollectService gitCollectService,
                                       AiParseService aiParseService,
                                       JobService jobService,
                                       @Qualifier("appTaskExecutor") Executor executor) {
        this.settingsService = settingsService;
        this.gitCollectService = gitCollectService;
        this.aiParseService = aiParseService;
        this.jobService = jobService;
        this.executor = executor;
    }

    public String getStatus() {
        return lastStatus.get();
    }

    public CollectResult getLastResult() {
        return lastResult.get();
    }

    public String startCollectJob() {
        String jobId = jobService.create("COLLECT");
        executor.execute(() -> {
            try {
                jobService.start(jobId, "正在准备采集…");
                CollectResult result = collectAllWithProgress(jobId);
                lastResult.set(result);
                lastStatus.set(result.isSuccess() ? "COMPLETED" : "FAILED");
                Map<String, Object> payload = new HashMap<>();
                payload.put("reposProcessed", result.getReposProcessed());
                payload.put("commitsCollected", result.getCommitsCollected());
                payload.put("commitsParsed", result.getCommitsParsed());
                if (result.isSuccess()) {
                    jobService.complete(jobId, result.getMessage(), payload);
                } else {
                    jobService.fail(jobId, result.getMessage());
                }
            } catch (Exception e) {
                lastStatus.set("FAILED");
                jobService.fail(jobId, e.getMessage() != null ? e.getMessage() : "采集失败");
            }
        });
        return jobId;
    }

    /** Used by scheduled task — no job progress tracking. */
    public synchronized CollectResult collectAll() {
        return collectAllWithProgress(null);
    }

    private CollectResult collectAllWithProgress(String jobId) {
        lastStatus.set("RUNNING");
        CollectResult result = new CollectResult();
        result.setSuccess(true);
        settingsService.applyToAppConfig();
        List<AppConfig.RepoConfig> repos = settingsService.enabledRepoConfigs();
        if (repos.isEmpty()) {
            result.setSuccess(false);
            result.setMessage("没有已启用的 Git 项目，请在「项目」页添加");
            stage(jobId, "VALIDATE", 5, "未找到已启用的 Git 项目");
            lastStatus.set("FAILED");
            lastResult.set(result);
            return result;
        }

        stage(jobId, "GIT", 10, String.format("正在采集 Git（%d 个仓库）…", repos.size()));
        int totalCollected = 0;
        int totalParsed = 0;
        int repoIndex = 0;
        for (AppConfig.RepoConfig repo : repos) {
            repoIndex++;
            int gitPercent = 10 + (int) ((repoIndex - 1) * 40.0 / repos.size());
            stage(jobId, "GIT", gitPercent,
                    String.format("正在采集 Git：%s（%d/%d）…", repo.getName(), repoIndex, repos.size()));

            int collected = gitCollectService.collectFromRepo(repo);
            totalCollected += collected;

            List<GitCommitLog> unparsed = gitCollectService.getUnparsedCommitsForRepo(repo.getName());
            if (!unparsed.isEmpty()) {
                int aiPercent = 50 + (int) (repoIndex * 35.0 / repos.size());
                stage(jobId, "AI", aiPercent,
                        String.format("正在调用 AI 解析提交：%s（%d 条待解析）…", repo.getName(), unparsed.size()));
                int parsed = aiParseService.parseUnparsedCommits(unparsed);
                totalParsed += parsed;
                for (GitCommitLog commit : unparsed) {
                    gitCollectService.markAsParsed(commit.getId());
                }
            }
        }

        stage(jobId, "FINISH", 95, "正在整理采集结果…");
        result.setReposProcessed(repos.size());
        result.setCommitsCollected(totalCollected);
        result.setCommitsParsed(totalParsed);
        result.setMessage(String.format("采集完成：%d 个仓库，新增 %d 条提交，解析 %d 条工作项",
                repos.size(), totalCollected, totalParsed));
        lastStatus.set("COMPLETED");
        lastResult.set(result);
        return result;
    }

    private void stage(String jobId, String stage, int percent, String message) {
        if (jobId != null) {
            jobService.stage(jobId, stage, percent, message);
        }
    }
}
