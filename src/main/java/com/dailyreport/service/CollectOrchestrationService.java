package com.dailyreport.service;

import com.dailyreport.config.AppConfig;
import com.dailyreport.domain.GitCommitLog;
import com.dailyreport.web.dto.CollectResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class CollectOrchestrationService {

    private final SettingsService settingsService;
    private final GitCollectService gitCollectService;
    private final AiParseService aiParseService;
    private final AtomicReference<String> lastStatus = new AtomicReference<>("IDLE");
    private final AtomicReference<CollectResult> lastResult = new AtomicReference<>();

    public CollectOrchestrationService(SettingsService settingsService,
                                       GitCollectService gitCollectService,
                                       AiParseService aiParseService) {
        this.settingsService = settingsService;
        this.gitCollectService = gitCollectService;
        this.aiParseService = aiParseService;
    }

    public String getStatus() {
        return lastStatus.get();
    }

    public CollectResult getLastResult() {
        return lastResult.get();
    }

    public synchronized CollectResult collectAll() {
        lastStatus.set("RUNNING");
        CollectResult result = new CollectResult();
        result.setSuccess(true);
        settingsService.applyToAppConfig();
        List<AppConfig.RepoConfig> repos = settingsService.enabledRepoConfigs();
        if (repos.isEmpty()) {
            result.setSuccess(false);
            result.setMessage("没有已启用的 Git 项目，请在「项目」页添加");
            lastStatus.set("FAILED");
            lastResult.set(result);
            return result;
        }
        int totalCollected = 0;
        int totalParsed = 0;
        for (AppConfig.RepoConfig repo : repos) {
            int collected = gitCollectService.collectFromRepo(repo);
            totalCollected += collected;
            List<GitCommitLog> unparsed = gitCollectService.getUnparsedCommitsForRepo(repo.getName());
            if (!unparsed.isEmpty()) {
                int parsed = aiParseService.parseUnparsedCommits(unparsed);
                totalParsed += parsed;
                for (GitCommitLog commit : unparsed) {
                    gitCollectService.markAsParsed(commit.getId());
                }
            }
        }
        result.setReposProcessed(repos.size());
        result.setCommitsCollected(totalCollected);
        result.setCommitsParsed(totalParsed);
        result.setMessage(String.format("采集完成：%d 个仓库，新增 %d 条提交，解析 %d 条工作项",
                repos.size(), totalCollected, totalParsed));
        lastStatus.set("COMPLETED");
        lastResult.set(result);
        log.info(result.getMessage());
        return result;
    }
}
