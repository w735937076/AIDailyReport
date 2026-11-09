package com.dailyreport.service;

import com.dailyreport.config.AppConfig;
import com.dailyreport.domain.GitProject;
import com.dailyreport.domain.SchedulerSettings;
import com.dailyreport.mapper.AppSettingMapper;
import com.dailyreport.mapper.GitProjectMapper;
import com.dailyreport.mapper.SchedulerSettingsMapper;
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
    private final SchedulerSettingsMapper schedulerSettingsMapper;
    private final AppSettingMapper appSettingMapper;

    public SettingsService(AppConfig appConfig,
                           GitProjectMapper gitProjectMapper,
                           SchedulerSettingsMapper schedulerSettingsMapper,
                           AppSettingMapper appSettingMapper) {
        this.appConfig = appConfig;
        this.gitProjectMapper = gitProjectMapper;
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

    public synchronized void applyToAppConfig() {
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
