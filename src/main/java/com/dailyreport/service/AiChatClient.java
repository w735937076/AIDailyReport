package com.dailyreport.service;

import com.dailyreport.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.function.Function;

/** Shared transport and failover policy for every report/commit AI operation. */
@Slf4j
@Service
public class AiChatClient {
    private final AppConfig config;
    private final Clock clock;
    private final Function<AppConfig.AiProviderConfig, RestTemplate> clients;
    private final ObjectMapper mapper = new ObjectMapper();
    private int primaryFailures;
    private long primaryRetryAt;

    @Autowired
    public AiChatClient(AppConfig config) {
        this(config, Clock.systemUTC(), provider -> new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofMillis(Math.max(1, provider.getConnectTimeoutMs())))
                .setReadTimeout(Duration.ofMillis(Math.max(1, provider.getReadTimeoutMs())))
                .build());
    }

    AiChatClient(AppConfig config, Clock clock,
                 Function<AppConfig.AiProviderConfig, RestTemplate> clients) {
        this.config = config;
        this.clock = clock;
        this.clients = clients;
    }

    public String complete(String prompt) throws Exception {
        AppConfig.AiConfig ai = config.getAi();
        AppConfig.FallbackConfig fallback = ai.getFallback();
        boolean hasFallback = fallback != null && fallback.isEnabled() && hasKey(fallback);
        Exception primaryError = null;
        if (!hasFallback || primaryAvailable()) {
            try {
                // With a backup available, switch immediately after the first failure.
                String result = requestWithRetries(ai, prompt, hasFallback ? 1 : ai.getMaxRetries());
                primarySucceeded();
                return result;
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    throw e;
                }
                if (!canFailover(e)) {
                    throw e;
                }
                primaryFailed(ai);
                primaryError = e;
                if (!hasFallback) {
                    throw new IllegalStateException("主 AI 调用失败，且未配置可用的备用模型，请检查 ai.fallback 配置及密钥", e);
                }
                log.warn("主模型 {} 失败（{}），切换至 {}", ai.getModel(),
                        e.getClass().getSimpleName(), fallback.getModel());
            }
        } else {
            log.info("主模型 {} 冷却中，本次使用备用模型 {}", ai.getModel(), fallback.getModel());
        }
        try {
            return requestWithRetries(fallback, prompt, ai.getMaxRetries());
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                throw e;
            }
            IllegalStateException failure = new IllegalStateException(
                    "备用 AI 模型 " + fallback.getModel() + " 调用失败"
                            + (primaryError == null ? "（主模型冷却中）" : "（主模型也调用失败）"), e);
            if (primaryError != null) {
                failure.addSuppressed(primaryError);
            }
            throw failure;
        }
    }

    public static boolean hasKey(AppConfig.AiProviderConfig provider) {
        String key = provider.getApiKey();
        return key != null && !key.isBlank() && !key.startsWith("your-") && !key.startsWith("${");
    }

    private synchronized boolean primaryAvailable() {
        if (primaryRetryAt > 0 && clock.millis() >= primaryRetryAt) {
            log.info("主模型 {} 冷却结束，尝试恢复调用", config.getAi().getModel());
        }
        return clock.millis() >= primaryRetryAt;
    }

    private synchronized void primarySucceeded() {
        if (primaryFailures > 0) {
            log.info("主模型 {} 调用恢复成功，重置失败计数", config.getAi().getModel());
        }
        primaryFailures = 0;
        primaryRetryAt = 0;
    }

    private synchronized void primaryFailed(AppConfig.AiConfig ai) {
        if (++primaryFailures >= Math.max(1, ai.getFailureThreshold())) {
            primaryRetryAt = clock.millis() + Math.max(1, ai.getCooldownMs());
            log.warn("主模型进入冷却，{} ms 后尝试恢复", ai.getCooldownMs());
        }
    }

    private String requestWithRetries(AppConfig.AiProviderConfig provider, String prompt, int attempts)
            throws Exception {
        for (int attempt = 1; ; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("AI 调用已取消");
            }
            String role = provider == config.getAi() ? "主模型" : "备用模型";
            long startedAt = System.nanoTime();
            log.info("AI 请求开始：{}，model={}，尝试={}/{}，连接超时={}ms，读取超时={}ms",
                    role, provider.getModel(), attempt, Math.max(1, attempts),
                    provider.getConnectTimeoutMs(), provider.getReadTimeoutMs());
            try {
                String result = request(provider, prompt);
                log.info("AI 请求成功：{}，model={}，耗时={}ms，返回字符数={}",
                        role, provider.getModel(), (System.nanoTime() - startedAt) / 1_000_000, result.length());
                return result;
            } catch (Exception e) {
                String reason = e instanceof RestClientResponseException http
                        ? "HTTP " + http.getStatusCode().value()
                        : e.getClass().getSimpleName();
                log.warn("AI 请求失败：{}，model={}，尝试={}/{}，耗时={}ms，原因={}",
                        role, provider.getModel(), attempt, Math.max(1, attempts),
                        (System.nanoTime() - startedAt) / 1_000_000, reason);
                if (attempt >= Math.max(1, attempts) || !isTransient(e)) {
                    throw e;
                }
                log.warn("模型 {} 调用失败（{}），重试 {}/{}", provider.getModel(),
                        e.getClass().getSimpleName(), attempt, attempts);
                try {
                    Thread.sleep((long) Math.max(0, config.getAi().getRetryDelayMs()) * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw interrupted;
                }
            }
        }
    }

    private boolean isTransient(Exception e) {
        if (e instanceof ResourceAccessException || e instanceof InvalidResponseException) {
            return true;
        }
        if (e instanceof RestClientResponseException http) {
            int status = http.getStatusCode().value();
            return status == 408 || status == 429 || status >= 500;
        }
        return false;
    }

    private boolean canFailover(Exception e) {
        if (isTransient(e)) {
            return true;
        }
        if (e instanceof RestClientResponseException http) {
            int status = http.getStatusCode().value();
            return status == 401 || status == 402 || status == 403;
        }
        return false;
    }

    private String request(AppConfig.AiProviderConfig provider, String prompt) throws Exception {
        if (!hasKey(provider)) {
            throw new IllegalArgumentException("请配置模型 " + provider.getModel() + " 的 API Key");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(provider.getApiKey());
        Map<String, Object> body = Map.of("model", provider.getModel(), "messages",
                List.of(Map.of("role", "user", "content", prompt)));
        String url = provider.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";
        ResponseEntity<String> response = clients.apply(provider).exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        try {
            JsonNode content = mapper.readTree(response.getBody()).path("choices")
                    .path(0).path("message").path("content");
            if (content.isTextual() && !content.asText().isBlank()) {
                return content.asText();
            }
        } catch (Exception ignored) {
            // Do not include provider response bodies, prompts or credentials in error logs.
        }
        throw new InvalidResponseException();
    }

    private static class InvalidResponseException extends RuntimeException {
        InvalidResponseException() {
            super("AI 接口返回为空或缺少有效的 choices[0].message.content");
        }
    }
}
