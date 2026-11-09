package com.dailyreport.web.dto;

import lombok.Data;

@Data
public class AiProviderResponse {
    private Long id;
    private String name;
    private String baseUrl;
    private String model;
    private Integer connectTimeoutMs;
    private Integer readTimeoutMs;
    private boolean enabled;
    private Integer priority;
    private boolean apiKeyConfigured;
}
