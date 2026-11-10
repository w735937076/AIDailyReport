package com.dailyreport.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PerformanceReview {
    private Long id;
    private String reviewMonth;
    private String author;
    private String status;
    private String contentJson;
    private String commitStatsJson;
    private String excelPath;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
