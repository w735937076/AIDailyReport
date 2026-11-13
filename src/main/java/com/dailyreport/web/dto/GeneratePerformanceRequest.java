package com.dailyreport.web.dto;

import lombok.Data;

@Data
public class GeneratePerformanceRequest {
    /** yyyy-MM */
    private String yearMonth;
    private Boolean replaceExisting;
}
