package com.dailyreport.web.dto;

import lombok.Data;

@Data
public class AiSettingsRequest {
    private String apiKey;
    private String baseUrl;
    private String model;
    private Integer connectTimeoutMs;
    private Integer readTimeoutMs;
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
}
