package com.dailyreport.web.dto;

import lombok.Data;

@Data
public class GenerateReportRequest {
    /** yyyy-MM-dd */
    private String date;
    private String tomorrowPlan;
    private String nonCodeWork;
}
