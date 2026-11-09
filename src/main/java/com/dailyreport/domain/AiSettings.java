package com.dailyreport.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiSettings {
    private Long id;
    private String primaryApiKey;
    private String primaryBaseUrl;
    private String primaryModel;
    private Integer primaryConnectTimeoutMs;
    private Integer primaryReadTimeoutMs;
    private Integer maxRetries;
    private Integer retryDelayMs;
    private Integer failureThreshold;
    private Long cooldownMs;
    private Boolean fallbackEnabled;
    private String fallbackApiKey;
    private String fallbackBaseUrl;
    private String fallbackModel;
    private Integer fallbackConnectTimeoutMs;
    private Integer fallbackReadTimeoutMs;
    private LocalDateTime updatedAt;
}
