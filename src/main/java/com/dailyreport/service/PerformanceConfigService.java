package com.dailyreport.service;

import com.dailyreport.config.AppConfig;
import com.dailyreport.mapper.AppSettingMapper;
import com.dailyreport.mapper.SchedulerSettingsMapper;
import com.dailyreport.web.dto.PerformanceConfigDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.stereotype.Service;

@Service
public class PerformanceConfigService {

    public static final String SETTING_KEY = "performance_config";

    private final AppSettingMapper appSettingMapper;
    private final SchedulerSettingsMapper schedulerSettingsMapper;
    private final AppConfig appConfig;
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    public PerformanceConfigService(AppSettingMapper appSettingMapper,
                                    SchedulerSettingsMapper schedulerSettingsMapper,
                                    AppConfig appConfig) {
        this.appSettingMapper = appSettingMapper;
        this.schedulerSettingsMapper = schedulerSettingsMapper;
        this.appConfig = appConfig;
    }

    public PerformanceConfigDto get() {
        PerformanceConfigDto dto = readStored();
        mergeYamlDefaults(dto);
        if (dto.getAssessee() == null || dto.getAssessee().isBlank()) {
            var sched = schedulerSettingsMapper.find();
            if (sched != null && sched.getGitFilterUsername() != null) {
                dto.setAssessee(sched.getGitFilterUsername());
            }
        }
        return dto;
    }

    private void mergeYamlDefaults(PerformanceConfigDto dto) {
        AppConfig.PerformanceConfig y = appConfig.getPerformance();
        if (y == null) return;
        if (isBlank(dto.getDepartment())) dto.setDepartment(y.getDepartment());
        if (isBlank(dto.getRole())) dto.setRole(y.getRole());
        if (isBlank(dto.getAssessee())) dto.setAssessee(y.getAssessee());
        if (isBlank(dto.getAssessor())) dto.setAssessor(y.getAssessor());
        if (isBlank(dto.getTemplatePath())) dto.setTemplatePath(y.getTemplatePath());
        if (isBlank(dto.getTemplateSearchDir())) dto.setTemplateSearchDir(y.getTemplateSearchDir());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public void save(PerformanceConfigDto dto) {
        try {
            appSettingMapper.put(SETTING_KEY, objectMapper.writeValueAsString(dto));
        } catch (Exception e) {
            throw new IllegalStateException("保存绩效配置失败", e);
        }
    }

    private PerformanceConfigDto readStored() {
        try {
            String json = appSettingMapper.get(SETTING_KEY);
            if (json != null && !json.isBlank()) {
                return objectMapper.readValue(json, PerformanceConfigDto.class);
            }
        } catch (Exception ignored) {
        }
        return new PerformanceConfigDto();
    }
}
