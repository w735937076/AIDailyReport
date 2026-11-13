package com.dailyreport.domain;

import com.dailyreport.config.AppConfig;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiProvider {
    private Long id;
    private String name;
    private String baseUrl;
    private String apiKey;
    private String model;
    private Integer connectTimeoutMs;
    private Integer readTimeoutMs;
    private Boolean enabled;
    /** Lower value = higher priority (tried first). */
    private Integer priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AppConfig.AiProviderConfig toProviderConfig() {
        AppConfig.AiProviderConfig c = new AppConfig.AiProviderConfig();
        c.setApiKey(apiKey);
        c.setBaseUrl(baseUrl);
        c.setModel(model);
        c.setConnectTimeoutMs(connectTimeoutMs != null ? connectTimeoutMs : 5000);
        c.setReadTimeoutMs(readTimeoutMs != null ? readTimeoutMs : 60000);
        return c;
    }
}
