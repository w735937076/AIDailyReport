package com.dailyreport.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PerformanceInterview {
    private Long id;
    private String interviewMonth;
    private String author;
    private String status;
    private String contentJson;
    private String commitStatsJson;
    private String excelPath;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
