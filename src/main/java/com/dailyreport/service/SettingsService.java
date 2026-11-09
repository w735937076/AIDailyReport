package com.dailyreport.service;

import com.dailyreport.config.AppConfig;
import com.dailyreport.domain.AiSettings;
import com.dailyreport.domain.GitProject;
import com.dailyreport.domain.SchedulerSettings;
import com.dailyreport.mapper.AiSettingsMapper;
import com.dailyreport.mapper.AppSettingMapper;
import com.dailyreport.mapper.GitProjectMapper;
import com.dailyreport.mapper.SchedulerSettingsMapper;
import com.dailyreport.web.dto.AiSettingsRequest;
import com.dailyreport.web.dto.AiSettingsResponse;
import com.dailyreport.web.dto.GitProjectRequest;
import com.dailyreport.web.dto.SchedulerSettingsRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class SettingsService {

    public static final String UNCHANGED_SECRET = "__UNCHANGED__";

    private final AppConfig appConfig;
    private final GitProjectMapper gitProjectMapper;
    private final AiSettingsMapper aiSettingsMapper;
    private final SchedulerSettingsMapper schedulerSettingsMapper;
    private final AppSettingMapper appSettingMapper;

    public SettingsService(AppConfig appConfig,
                           GitProjectMapper gitProjectMapper,
                           AiSettingsMapper aiSettingsMapper,
                           SchedulerSettingsMapper schedulerSettingsMapper,
                           AppSettingMapper appSettingMapper) {
        this.appConfig = appConfig;
        this.gitProjectMapper = gitProjectMapper;
        this.aiSettingsMapper = aiSettingsMapper;
        this.schedulerSettingsMapper = schedulerSettingsMapper;
        this.appSettingMapper = appSettingMapper;
    }

  public void bootstrapFromYamlIfEmpty() {
        try {
            if (gitProjectMapper.count() == 0 && appConfig.getGit().getRepos() != null) {
                for (AppConfig.RepoConfig repo : appConfig.getGit().getRepos()) {
                    GitProject project = new GitProject();
                    project.setName(repo.getName());
                    project.setLocalPath(repo.getPath());
                    project.setRemoteUrl(repo.getRemote());
                    project.setBranch(repo.getBranch() != null ? repo.getBranch() : "main");
                    project.setEnabled(true);
                    gitProjectMapper.insert(project);
                }
                log.info("Imported {} git projects from config.yaml", appConfig.getGit().getRepos().size());
            }
            AiSettings ai = aiSettingsMapper.find();
            if (ai != null && !hasStoredPrimaryKey(ai)) {
                syncYamlToAiDb(ai);
            }
            SchedulerSettings sched = schedulerSettingsMapper.find();
            if (sched != null && sched.getGitFilterUsername() == null
                    && appConfig.getGit().getAccount().getUsername() != null) {
                sched.setGitFilterUsername(appConfig.getGit().getAccount().getUsername());
                schedulerSettingsMapper.update(sched);
            }
            applyToAppConfig();
        } catch (Exception e) {
            log.warn("Settings bootstrap skipped: {}", e.getMessage());
        }
    }

    private boolean hasStoredPrimaryKey(AiSettings ai) {
        String key = ai.getPrimaryApiKey();
        return key != null && !key.isBlank();
    }

    private void syncYamlToAiDb(AiSettings ai) {
        AppConfig.AiConfig yamlAi = appConfig.getAi();
        ai.setPrimaryApiKey(resolveEnvPlaceholder(yamlAi.getApiKey()));
        ai.setPrimaryBaseUrl(yamlAi.getBaseUrl());
        ai.setPrimaryModel(yamlAi.getModel());
        ai.setPrimaryConnectTimeoutMs(yamlAi.getConnectTimeoutMs());
        ai.setPrimaryReadTimeoutMs(yamlAi.getReadTimeoutMs());
        ai.setMaxRetries(yamlAi.getMaxRetries());
        ai.setRetryDelayMs(yamlAi.getRetryDelayMs());
        ai.setFailureThreshold(yamlAi.getFailureThreshold());
        ai.setCooldownMs(yamlAi.getCooldownMs());
        AppConfig.FallbackConfig fb = yamlAi.getFallback();
        if (fb != null) {
            ai.setFallbackEnabled(fb.isEnabled());
            ai.setFallbackApiKey(resolveEnvPlaceholder(fb.getApiKey()));
            ai.setFallbackBaseUrl(fb.getBaseUrl());
            ai.setFallbackModel(fb.getModel());
            ai.setFallbackConnectTimeoutMs(fb.getConnectTimeoutMs());
            ai.setFallbackReadTimeoutMs(fb.getReadTimeoutMs());
        }
        aiSettingsMapper.update(ai);
    }

    private static String resolveEnvPlaceholder(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.startsWith("${") && value.endsWith("}")) {
            String inner = value.substring(2, value.length() - 1);
            int colon = inner.indexOf(':');
            String envName = colon >= 0 ? inner.substring(0, colon) : inner;
            String defaultVal = colon >= 0 ? inner.substring(colon + 1) : "";
            String env = System.getenv(envName);
            return env != null && !env.isBlank() ? env : defaultVal;
        }
        return value;
    }

    public synchronized void applyToAppConfig() {
        AiSettings ai = aiSettingsMapper.find();
        if (ai != null) {
            AppConfig.AiConfig target = appConfig.getAi();
            target.setApiKey(ai.getPrimaryApiKey());
            target.setBaseUrl(ai.getPrimaryBaseUrl());
            target.setModel(ai.getPrimaryModel());
            target.setConnectTimeoutMs(nz(ai.getPrimaryConnectTimeoutMs(), 5000));
            target.setReadTimeoutMs(nz(ai.getPrimaryReadTimeoutMs(), 60000));
            target.setMaxRetries(nz(ai.getMaxRetries(), 3));
            target.setRetryDelayMs(nz(ai.getRetryDelayMs(), 1000));
            target.setFailureThreshold(nz(ai.getFailureThreshold(), 2));
            target.setCooldownMs(ai.getCooldownMs() != null ? ai.getCooldownMs() : 120000L);
            AppConfig.FallbackConfig fb = target.getFallback();
            fb.setEnabled(Boolean.TRUE.equals(ai.getFallbackEnabled()));
            fb.setApiKey(ai.getFallbackApiKey());
            fb.setBaseUrl(ai.getFallbackBaseUrl());
            fb.setModel(ai.getFallbackModel());
            fb.setConnectTimeoutMs(nz(ai.getFallbackConnectTimeoutMs(), 5000));
            fb.setReadTimeoutMs(nz(ai.getFallbackReadTimeoutMs(), 60000));
        }
        SchedulerSettings sched = schedulerSettingsMapper.find();
        if (sched != null) {
            appConfig.getScheduler().setEnabled(Boolean.TRUE.equals(sched.getEnabled()));
            appConfig.getScheduler().setCron(sched.getCron());
            if (sched.getGitFilterUsername() != null) {
                appConfig.getGit().getAccount().setUsername(sched.getGitFilterUsername());
            }
        }
        List<GitProject> enabled = gitProjectMapper.findEnabled();
        List<AppConfig.RepoConfig> repos = new ArrayList<>();
        for (GitProject p : enabled) {
            AppConfig.RepoConfig r = new AppConfig.RepoConfig();
            r.setName(p.getName());
            r.setPath(p.getLocalPath());
            r.setRemote(p.getRemoteUrl());
            r.setBranch(p.getBranch());
            repos.add(r);
        }
        appConfig.getGit().setRepos(repos);
    }

    public List<GitProject> listProjects() {
        return gitProjectMapper.findAll();
    }

    public GitProject getProject(Long id) {
        return gitProjectMapper.findById(id);
    }

    @Transactional
    public GitProject createProject(GitProjectRequest req) {
        GitProject p = toEntity(req, new GitProject());
        p.setEnabled(req.getEnabled() == null || req.getEnabled());
        gitProjectMapper.insert(p);
        applyToAppConfig();
        return p;
    }

    @Transactional
    public GitProject updateProject(Long id, GitProjectRequest req) {
        GitProject existing = gitProjectMapper.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("项目不存在");
        }
        GitProject p = toEntity(req, existing);
        p.setId(id);
        if (req.getEnabled() != null) {
            p.setEnabled(req.getEnabled());
        }
        gitProjectMapper.update(p);
        applyToAppConfig();
        return gitProjectMapper.findById(id);
    }

    @Transactional
    public void deleteProject(Long id) {
        gitProjectMapper.delete(id);
        applyToAppConfig();
    }

    public List<AppConfig.RepoConfig> enabledRepoConfigs() {
        return appConfig.getGit().getRepos() != null ? appConfig.getGit().getRepos() : List.of();
    }

    public String getGitFilterUsername() {
        SchedulerSettings s = schedulerSettingsMapper.find();
        return s != null ? s.getGitFilterUsername() : null;
    }

    public AiSettingsResponse getAiSettings() {
        AiSettings ai = aiSettingsMapper.find();
        AiSettingsResponse resp = new AiSettingsResponse();
        if (ai == null) {
            return resp;
        }
        resp.setBaseUrl(ai.getPrimaryBaseUrl());
        resp.setModel(ai.getPrimaryModel());
        resp.setConnectTimeoutMs(ai.getPrimaryConnectTimeoutMs());
        resp.setReadTimeoutMs(ai.getPrimaryReadTimeoutMs());
        resp.setMaxRetries(ai.getMaxRetries());
        resp.setRetryDelayMs(ai.getRetryDelayMs());
        resp.setFailureThreshold(ai.getFailureThreshold());
        resp.setCooldownMs(ai.getCooldownMs());
        resp.setPrimaryKeyConfigured(AiChatClient.hasKey(maskToProvider(ai.getPrimaryApiKey())));
        resp.setFallbackEnabled(Boolean.TRUE.equals(ai.getFallbackEnabled()));
        resp.setFallbackBaseUrl(ai.getFallbackBaseUrl());
        resp.setFallbackModel(ai.getFallbackModel());
        resp.setFallbackConnectTimeoutMs(ai.getFallbackConnectTimeoutMs());
        resp.setFallbackReadTimeoutMs(ai.getFallbackReadTimeoutMs());
        resp.setFallbackKeyConfigured(AiChatClient.hasKey(maskToProvider(ai.getFallbackApiKey())));
        return resp;
    }

    private static AppConfig.AiProviderConfig maskToProvider(String apiKey) {
        AppConfig.AiProviderConfig p = new AppConfig.AiProviderConfig();
        p.setApiKey(apiKey);
        return p;
    }

    @Transactional
    public AiSettingsResponse saveAiSettings(AiSettingsRequest req) {
        AiSettings ai = aiSettingsMapper.find();
        if (ai == null) {
            throw new IllegalStateException("AI 配置未初始化");
        }
        ai.setPrimaryBaseUrl(req.getBaseUrl());
        ai.setPrimaryModel(req.getModel());
        ai.setPrimaryConnectTimeoutMs(req.getConnectTimeoutMs());
        ai.setPrimaryReadTimeoutMs(req.getReadTimeoutMs());
        ai.setMaxRetries(req.getMaxRetries());
        ai.setRetryDelayMs(req.getRetryDelayMs());
        ai.setFailureThreshold(req.getFailureThreshold());
        ai.setCooldownMs(req.getCooldownMs());
        if (req.getApiKey() != null && !req.getApiKey().isBlank() && !UNCHANGED_SECRET.equals(req.getApiKey())) {
            ai.setPrimaryApiKey(req.getApiKey().trim());
        }
        ai.setFallbackEnabled(Boolean.TRUE.equals(req.getFallbackEnabled()));
        ai.setFallbackBaseUrl(req.getFallbackBaseUrl());
        ai.setFallbackModel(req.getFallbackModel());
        ai.setFallbackConnectTimeoutMs(req.getFallbackConnectTimeoutMs());
        ai.setFallbackReadTimeoutMs(req.getFallbackReadTimeoutMs());
        if (req.getFallbackApiKey() != null && !req.getFallbackApiKey().isBlank()
                && !UNCHANGED_SECRET.equals(req.getFallbackApiKey())) {
            ai.setFallbackApiKey(req.getFallbackApiKey().trim());
        }
        aiSettingsMapper.update(ai);
        applyToAppConfig();
        return getAiSettings();
    }

    public SchedulerSettings getSchedulerSettings() {
        return schedulerSettingsMapper.find();
    }

    @Transactional
    public SchedulerSettings saveSchedulerSettings(SchedulerSettingsRequest req) {
        SchedulerSettings s = schedulerSettingsMapper.find();
        if (s == null) {
            throw new IllegalStateException("调度配置未初始化");
        }
        if (req.getEnabled() != null) {
            s.setEnabled(req.getEnabled());
        }
        if (req.getCron() != null) {
            s.setCron(req.getCron());
        }
        if (req.getTimezone() != null) {
            s.setTimezone(req.getTimezone());
        }
        if (req.getGitFilterUsername() != null) {
            s.setGitFilterUsername(req.getGitFilterUsername());
        }
        schedulerSettingsMapper.update(s);
        applyToAppConfig();
        return schedulerSettingsMapper.find();
    }

    public boolean isOpenBrowserOnStart() {
        String v = appSettingMapper.get("open_browser_on_start");
        return v == null || "true".equalsIgnoreCase(v);
    }

    public void setOpenBrowserOnStart(boolean value) {
        appSettingMapper.put("open_browser_on_start", String.valueOf(value));
    }

    private static GitProject toEntity(GitProjectRequest req, GitProject p) {
        p.setName(req.getName());
        p.setLocalPath(blankToNull(req.getLocalPath()));
        p.setRemoteUrl(blankToNull(req.getRemoteUrl()));
        p.setBranch(req.getBranch() != null ? req.getBranch() : "main");
        if (p.getLocalPath() == null && p.getRemoteUrl() == null) {
            throw new IllegalArgumentException("请填写本地路径或远程 URL");
        }
        return p;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static int nz(Integer v, int d) {
        return v != null ? v : d;
    }

    public boolean importYamlConfig() throws Exception {
        Path yaml = Path.of("./config.yaml");
        if (!Files.exists(yaml)) {
            Path example = Path.of("./config.example.yaml");
            if (Files.exists(example)) {
                return false;
            }
            throw new IllegalStateException("未找到 config.yaml");
        }
        bootstrapFromYamlIfEmpty();
        return true;
    }
}
