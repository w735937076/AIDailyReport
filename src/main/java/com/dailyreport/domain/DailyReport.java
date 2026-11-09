package com.dailyreport.domain;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class DailyReport {
    private Long id;
    private LocalDate reportDate;
    private String content;
    private String tomorrowPlan;
    private String rawDataJson;
    private String excelPath;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
