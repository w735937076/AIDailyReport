package com.dailyreport.web.dto;

import lombok.Data;

@Data
public class UpdateDailyReportRequest {
    private String content;
    private String tomorrowPlan;
}
