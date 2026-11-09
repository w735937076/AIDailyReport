package com.dailyreport.service;

import com.dailyreport.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.*;

class AiConfigurationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties
    @Import(AppConfig.class)
    static class TestConfig { }

    @Test
    void bindsExistingPrimarySettingsAndResolvesBackupKeyPlaceholder() {
        new ApplicationContextRunner().withUserConfiguration(TestConfig.class)
                .withPropertyValues("ai.apiKey=primary-test-key", "ai.model=existing-model",
                        "ai.baseUrl=https://primary.example/v1", "MINIMAX_API_KEY=backup-test-key",
                        "ai.fallback.model=MiniMax-M3.1-Flash",
                        "ai.fallback.baseUrl=https://api.minimax.cn/v1",
                        "ai.fallback.apiKey=${MINIMAX_API_KEY:}")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    AppConfig.AiConfig ai = context.getBean(AppConfig.class).getAi();
                    assertEquals("primary-test-key", ai.getApiKey());
                    assertEquals("existing-model", ai.getModel());
                    assertEquals("https://primary.example/v1", ai.getBaseUrl());
                    assertEquals("backup-test-key", ai.getFallback().getApiKey());
                    assertEquals("MiniMax-M3.1-Flash", ai.getFallback().getModel());
                    assertEquals("https://api.minimax.cn/v1", ai.getFallback().getBaseUrl());
                    assertEquals(60000, ai.getFallback().getReadTimeoutMs());
                });
    }
}
