package com.dailyreport.web.dto;

import lombok.Data;

@Data
public class AiPolicyDto {
    private Integer maxRetries;
    private Integer retryDelayMs;
    private Integer failureThreshold;
    private Long cooldownMs;
    /** Long-form generation (绩效评估等)，默认 300000 ms */
    private Integer longReadTimeoutMs;
}
