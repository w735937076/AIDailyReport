package com.dailyreport.domain;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class WorkItem {
    private Long id;
    private String commitId;
    private String repoName;
    private LocalDate workDate;
    private String type;
    private String module;
    private String plan;
    private String practice;
    private String estimate;
    private String progress;
    private BigDecimal confidence;
    private String aiRawResponse;
    private LocalDateTime createdAt;
}
