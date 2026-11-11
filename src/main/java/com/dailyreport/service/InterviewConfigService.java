package com.dailyreport.service;

import com.dailyreport.config.AppConfig;
import com.dailyreport.mapper.AppSettingMapper;
import com.dailyreport.mapper.SchedulerSettingsMapper;
import com.dailyreport.web.dto.InterviewConfigDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.stereotype.Service;

@Service
public class InterviewConfigService {

    public static final String SETTING_KEY = "interview_config";

    private final AppSettingMapper appSettingMapper;
    private final SchedulerSettingsMapper schedulerSettingsMapper;
    private final PerformanceConfigService performanceConfigService;
    private final AppConfig appConfig;
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    public InterviewConfigService(AppSettingMapper appSettingMapper,
                                  SchedulerSettingsMapper schedulerSettingsMapper,
                                  PerformanceConfigService performanceConfigService,
                                  AppConfig appConfig) {
        this.appSettingMapper = appSettingMapper;
        this.schedulerSettingsMapper = schedulerSettingsMapper;
        this.performanceConfigService = performanceConfigService;
        this.appConfig = appConfig;
    }

    public InterviewConfigDto get() {
        InterviewConfigDto dto = readStored();
        mergeYamlDefaults(dto);
        if (isBlank(dto.getAssessee())) {
            dto.setAssessee(performanceConfigService.get().getAssessee());
        }
        if (isBlank(dto.getDepartment())) {
            dto.setDepartment(performanceConfigService.get().getDepartment());
        }
        if (isBlank(dto.getRole())) {
            dto.setRole(performanceConfigService.get().getRole());
        }
        if (isBlank(dto.getAssessee())) {
            var sched = schedulerSettingsMapper.find();
            if (sched != null && sched.getGitFilterUsername() != null) {
                dto.setAssessee(sched.getGitFilterUsername());
            }
        }
        return dto;
    }

    private void mergeYamlDefaults(InterviewConfigDto dto) {
        AppConfig.InterviewConfig y = appConfig.getInterview();
        if (y == null) return;
        if (isBlank(dto.getDepartment())) dto.setDepartment(y.getDepartment());
        if (isBlank(dto.getRole())) dto.setRole(y.getRole());
        if (isBlank(dto.getAssessee())) dto.setAssessee(y.getAssessee());
        if (isBlank(dto.getHireDate())) dto.setHireDate(y.getHireDate());
        if (isBlank(dto.getTemplatePath())) dto.setTemplatePath(y.getTemplatePath());
        if (isBlank(dto.getTemplateSearchDir())) dto.setTemplateSearchDir(y.getTemplateSearchDir());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public void save(InterviewConfigDto dto) {
        try {
            appSettingMapper.put(SETTING_KEY, objectMapper.writeValueAsString(dto));
        } catch (Exception e) {
            throw new IllegalStateException("保存面谈配置失败", e);
        }
    }

    private InterviewConfigDto readStored() {
        try {
            String json = appSettingMapper.get(SETTING_KEY);
            if (json != null && !json.isBlank()) {
                return objectMapper.readValue(json, InterviewConfigDto.class);
            }
        } catch (Exception ignored) {
        }
        return new InterviewConfigDto();
    }
}
