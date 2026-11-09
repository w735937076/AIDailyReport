package com.dailyreport.domain;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PeriodReport {
    private Long id;
    private String type;
    private LocalDate startDate;
    private LocalDate endDate;
    private String content;
    private String rawDataJson;
    private String excelPath;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
