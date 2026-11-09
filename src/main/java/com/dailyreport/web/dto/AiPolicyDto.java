package com.dailyreport.web.dto;

import lombok.Data;

@Data
public class AiPolicyDto {
    private Integer maxRetries;
    private Integer retryDelayMs;
    private Integer failureThreshold;
    private Long cooldownMs;
}
