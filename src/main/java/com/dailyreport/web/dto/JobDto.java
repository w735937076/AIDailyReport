package com.dailyreport.web.dto;

import lombok.Data;

import java.util.Map;

@Data
public class JobDto {
    private String jobId;
    private String type;
    /** PENDING | RUNNING | COMPLETED | FAILED */
    private String status;
    private String stage;
    private Integer percent;
    private String message;
    private String error;
    private Long startedAt;
    private Long updatedAt;
    private Map<String, Object> result;
}
