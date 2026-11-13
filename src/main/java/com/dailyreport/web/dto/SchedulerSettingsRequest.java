package com.dailyreport.web.dto;

import lombok.Data;

@Data
public class SchedulerSettingsRequest {
    private Boolean enabled;
    private String cron;
    private String timezone;
    private String gitFilterUsername;
}
