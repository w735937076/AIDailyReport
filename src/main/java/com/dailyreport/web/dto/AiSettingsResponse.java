package com.dailyreport.web.dto;

import lombok.Data;

@Data
public class AiSettingsResponse {
    private boolean primaryKeyConfigured;
    private String baseUrl;
    private String model;
    private Integer connectTimeoutMs;
    private Integer readTimeoutMs;
    private Integer maxRetries;
    private Integer retryDelayMs;
    private Integer failureThreshold;
    private Long cooldownMs;
    private boolean fallbackEnabled;
    private boolean fallbackKeyConfigured;
    private String fallbackBaseUrl;
    private String fallbackModel;
    private Integer fallbackConnectTimeoutMs;
    private Integer fallbackReadTimeoutMs;
}
