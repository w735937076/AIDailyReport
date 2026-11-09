package com.dailyreport.config;

import com.dailyreport.domain.SchedulerSettings;
import com.dailyreport.mapper.SchedulerSettingsMapper;
import com.dailyreport.service.CollectOrchestrationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

import java.util.TimeZone;

@Slf4j
@Configuration
@EnableScheduling
public class DynamicSchedulingConfig implements SchedulingConfigurer {

    private final SchedulerSettingsMapper schedulerSettingsMapper;
    private final CollectOrchestrationService collectOrchestrationService;

    public DynamicSchedulingConfig(SchedulerSettingsMapper schedulerSettingsMapper,
                                   CollectOrchestrationService collectOrchestrationService) {
        this.schedulerSettingsMapper = schedulerSettingsMapper;
        this.collectOrchestrationService = collectOrchestrationService;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(
                () -> {
                    SchedulerSettings settings = schedulerSettingsMapper.find();
                    if (settings != null && Boolean.TRUE.equals(settings.getEnabled())) {
                        log.info("定时 Git 采集触发 (cron={})", settings.getCron());
                        collectOrchestrationService.collectAll();
                    }
                },
                triggerContext -> {
                    SchedulerSettings settings = schedulerSettingsMapper.find();
                    String cron = settings != null && settings.getCron() != null
                            ? settings.getCron() : "0 0 18 * * ?";
                    String zone = settings != null && settings.getTimezone() != null
                            ? settings.getTimezone() : "Asia/Shanghai";
                    var next = new CronTrigger(cron, TimeZone.getTimeZone(zone))
                            .nextExecutionTime(triggerContext);
                    return next != null ? next.toInstant() : null;
                }
        );
    }
}
