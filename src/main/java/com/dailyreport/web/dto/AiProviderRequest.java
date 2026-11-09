package com.dailyreport.web.dto;

import lombok.Data;

@Data
public class AiProviderRequest {
    private String name;
    private String apiKey;
    private String baseUrl;
    private String model;
    private Integer connectTimeoutMs;
    private Integer readTimeoutMs;
    private Boolean enabled;
    private Integer priority;
}
