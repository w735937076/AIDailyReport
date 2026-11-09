package com.dailyreport.service;

import com.dailyreport.config.AppConfig;
import com.dailyreport.domain.AiPolicy;
import com.dailyreport.domain.AiProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** OpenAI-compatible chat completions with priority-ordered multi-provider failover. */
@Slf4j
@Service
public class AiChatClient {

    private final AiProviderService providerService;
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentHashMap<Long, RestTemplate> clients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> failureCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> cooldownUntil = new ConcurrentHashMap<>();

    public AiChatClient(AiProviderService providerService) {
        this(providerService, Clock.systemUTC(), null);
    }

    /** Package-private for unit tests (injectable Clock / RestTemplate). */
    AiChatClient(AiProviderService providerService, Clock clock,
                 Map<Long, RestTemplate> testClients) {
        this.providerService = providerService;
        this.clock = clock;
        if (testClients != null) {
            this.clients.putAll(testClients);
        }
    }

    public String complete(String prompt) throws Exception {
        List<AiProvider> providers = providerService.listEnabledForRuntime();
        if (providers.isEmpty()) {
            throw new IllegalStateException("没有已启用且配置 API Key 的 AI 提供商，请在 Web「AI 设置」中添加");
        }
        AiPolicy policy = providerService.getPolicy();
        Exception lastError = null;

        for (int i = 0; i < providers.size(); i++) {
            AiProvider provider = providers.get(i);
            boolean hasMore = i < providers.size() - 1;
            if (shouldSkipForCooldown(provider, providers, i)) {
                log.info("提供商「{}」冷却中，跳过", provider.getName());
                continue;
            }
            int attempts = hasMore ? 1 : Math.max(1, policy.getMaxRetries());
            try {
                String result = requestWithRetries(provider, prompt, attempts, policy);
                providerSucceeded(provider.getId());
                return result;
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    throw e;
                }
                if (!canFailover(e)) {
                    throw e;
                }
                providerFailed(provider.getId(), policy);
                lastError = e;
                if (!hasMore) {
                    throw e;
                }
                log.warn("提供商「{}」失败（{}），尝试下一个", provider.getName(), e.getClass().getSimpleName());
            }
        }
        IllegalStateException failure = new IllegalStateException("所有已启用的 AI 提供商均调用失败", lastError);
        if (lastError != null) {
            failure.initCause(lastError);
        }
        throw failure;
    }

    private boolean shouldSkipForCooldown(AiProvider provider, List<AiProvider> all, int index) {
        if (providerAvailable(provider.getId())) {
            return false;
        }
        for (int j = index + 1; j < all.size(); j++) {
            if (providerAvailable(all.get(j).getId())) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasKey(AppConfig.AiProviderConfig provider) {
        String key = provider.getApiKey();
        return key != null && !key.isBlank() && !key.startsWith("your-") && !key.startsWith("${");
    }

    private boolean providerAvailable(Long id) {
        Long until = cooldownUntil.get(id);
        if (until != null && clock.millis() < until) {
            return false;
        }
        if (until != null && clock.millis() >= until) {
            log.info("提供商 id={} 冷却结束", id);
        }
        return true;
    }

    private void providerSucceeded(Long id) {
        failureCounts.remove(id);
        cooldownUntil.remove(id);
    }

    private void providerFailed(Long id, AiPolicy policy) {
        int failures = failureCounts.merge(id, 1, Integer::sum);
        if (failures >= Math.max(1, policy.getFailureThreshold())) {
            long until = clock.millis() + Math.max(1, policy.getCooldownMs());
            cooldownUntil.put(id, until);
            log.warn("提供商 id={} 进入冷却，{} ms 后恢复", id, policy.getCooldownMs());
        }
    }

    private String requestWithRetries(AiProvider provider, String prompt, int attempts, AiPolicy policy)
            throws Exception {
        AppConfig.AiProviderConfig cfg = provider.toProviderConfig();
        for (int attempt = 1; ; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("AI 调用已取消");
            }
            long startedAt = System.nanoTime();
            log.info("AI 请求开始：{}，model={}，尝试={}/{}，连接超时={}ms，读取超时={}ms",
                    provider.getName(), provider.getModel(), attempt, attempts,
                    cfg.getConnectTimeoutMs(), cfg.getReadTimeoutMs());
            try {
                String result = request(cfg, prompt, provider.getId());
                log.info("AI 请求成功：{}，model={}，耗时={}ms，返回字符数={}",
                        provider.getName(), provider.getModel(),
                        (System.nanoTime() - startedAt) / 1_000_000, result.length());
                return result;
            } catch (Exception e) {
                String reason = e instanceof RestClientResponseException http
                        ? "HTTP " + http.getStatusCode().value()
                        : e.getClass().getSimpleName();
                log.warn("AI 请求失败：{}，model={}，尝试={}/{}，耗时={}ms，原因={}",
                        provider.getName(), provider.getModel(), attempt, attempts,
                        (System.nanoTime() - startedAt) / 1_000_000, reason);
                if (attempt >= attempts || !isTransient(e)) {
                    throw e;
                }
                log.warn("提供商 {} 调用失败（{}），重试 {}/{}", provider.getName(),
                        e.getClass().getSimpleName(), attempt, attempts);
                Thread.sleep((long) Math.max(0, policy.getRetryDelayMs()) * attempt);
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

    private String request(AppConfig.AiProviderConfig provider, String prompt, Long providerId) throws Exception {
        if (!hasKey(provider)) {
            throw new IllegalArgumentException("请配置提供商的 API Key");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(provider.getApiKey());
        Map<String, Object> body = Map.of("model", provider.getModel(), "messages",
                List.of(Map.of("role", "user", "content", prompt)));
        String url = provider.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";
        RestTemplate client = clients.computeIfAbsent(providerId, id -> new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofMillis(Math.max(1, provider.getConnectTimeoutMs())))
                .setReadTimeout(Duration.ofMillis(Math.max(1, provider.getReadTimeoutMs())))
                .build());
        ResponseEntity<String> response = client.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        try {
            JsonNode content = mapper.readTree(response.getBody()).path("choices")
                    .path(0).path("message").path("content");
            if (content.isTextual() && !content.asText().isBlank()) {
                return content.asText();
            }
        } catch (Exception ignored) {
            // Do not log response bodies or credentials.
        }
        throw new InvalidResponseException();
    }

    private static class InvalidResponseException extends RuntimeException {
        InvalidResponseException() {
            super("AI 接口返回为空或缺少有效的 choices[0].message.content");
        }
    }
}
