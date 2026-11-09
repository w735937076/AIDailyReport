package com.dailyreport.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SchedulerSettings {
    private Long id;
    private Boolean enabled;
    private String cron;
    private String timezone;
    private String gitFilterUsername;
    private LocalDateTime updatedAt;
}
