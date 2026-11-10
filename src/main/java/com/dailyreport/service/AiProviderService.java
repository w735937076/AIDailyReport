package com.dailyreport.service;

import com.dailyreport.config.AppConfig;
import com.dailyreport.domain.AiPolicy;
import com.dailyreport.domain.AiProvider;
import com.dailyreport.domain.AiSettings;
import com.dailyreport.mapper.AiProviderMapper;
import com.dailyreport.mapper.AiSettingsMapper;
import com.dailyreport.mapper.AppSettingMapper;
import com.dailyreport.web.dto.AiPolicyDto;
import com.dailyreport.web.dto.AiProviderRequest;
import com.dailyreport.web.dto.AiProviderResponse;
import com.dailyreport.web.dto.PageRequestUtil;
import com.dailyreport.web.dto.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiProviderService {

    public static final String UNCHANGED_SECRET = SettingsService.UNCHANGED_SECRET;
    private static final String MIGRATED_KEY = "ai_providers_migrated_v1";

    private final AiProviderMapper aiProviderMapper;
    private final AiSettingsMapper legacyAiMapper;
    private final AppSettingMapper appSettingMapper;
    private final AppConfig appConfig;

    public AiProviderService(AiProviderMapper aiProviderMapper,
                             AiSettingsMapper legacyAiMapper,
                             AppSettingMapper appSettingMapper,
                             AppConfig appConfig) {
        this.aiProviderMapper = aiProviderMapper;
        this.legacyAiMapper = legacyAiMapper;
        this.appSettingMapper = appSettingMapper;
        this.appConfig = appConfig;
    }

    public void migrateAndBootstrap() {
        try {
            migrateLegacyIfNeeded();
            bootstrapFromYamlIfEmpty();
        } catch (Exception e) {
            log.warn("AI provider bootstrap skipped: {}", e.getMessage());
        }
    }

    private void migrateLegacyIfNeeded() {
        if ("true".equals(appSettingMapper.get(MIGRATED_KEY))) {
            return;
        }
        if (aiProviderMapper.count() > 0) {
            appSettingMapper.put(MIGRATED_KEY, "true");
            return;
        }
        AiSettings legacy = legacyAiMapper.find();
        if (legacy == null) {
            appSettingMapper.put(MIGRATED_KEY, "true");
            return;
        }
        boolean imported = false;
        if (legacy.getPrimaryBaseUrl() != null && legacy.getPrimaryModel() != null) {
            AiProvider primary = new AiProvider();
            primary.setName("Primary");
            primary.setBaseUrl(legacy.getPrimaryBaseUrl());
            primary.setApiKey(legacy.getPrimaryApiKey());
            primary.setModel(legacy.getPrimaryModel());
            primary.setConnectTimeoutMs(nz(legacy.getPrimaryConnectTimeoutMs(), 5000));
            primary.setReadTimeoutMs(nz(legacy.getPrimaryReadTimeoutMs(), 60000));
            primary.setEnabled(true);
            primary.setPriority(10);
            aiProviderMapper.insert(primary);
            imported = true;
        }
        if (Boolean.TRUE.equals(legacy.getFallbackEnabled())
                && legacy.getFallbackModel() != null && !legacy.getFallbackModel().isBlank()) {
            AiProvider fallback = new AiProvider();
            fallback.setName("Fallback");
            fallback.setBaseUrl(blankOr(legacy.getFallbackBaseUrl(), "https://api.openai.com/v1"));
            fallback.setApiKey(legacy.getFallbackApiKey());
            fallback.setModel(legacy.getFallbackModel());
            fallback.setConnectTimeoutMs(nz(legacy.getFallbackConnectTimeoutMs(), 5000));
            fallback.setReadTimeoutMs(nz(legacy.getFallbackReadTimeoutMs(), 60000));
            fallback.setEnabled(true);
            fallback.setPriority(20);
            aiProviderMapper.insert(fallback);
            imported = true;
        }
        savePolicyFields(legacy.getMaxRetries(), legacy.getRetryDelayMs(),
                legacy.getFailureThreshold(), legacy.getCooldownMs());
        if (imported) {
            log.info("Migrated legacy app_ai_config into app_ai_provider");
        }
        appSettingMapper.put(MIGRATED_KEY, "true");
    }

    private void bootstrapFromYamlIfEmpty() {
        if (aiProviderMapper.count() > 0) {
            return;
        }
        AppConfig.AiConfig yamlAi = appConfig.getAi();
        AiProvider primary = new AiProvider();
        primary.setName("YAML Primary");
        primary.setBaseUrl(yamlAi.getBaseUrl());
        primary.setApiKey(resolveEnvPlaceholder(yamlAi.getApiKey()));
        primary.setModel(yamlAi.getModel());
        primary.setConnectTimeoutMs(yamlAi.getConnectTimeoutMs());
        primary.setReadTimeoutMs(yamlAi.getReadTimeoutMs());
        primary.setEnabled(true);
        primary.setPriority(10);
        aiProviderMapper.insert(primary);
        AppConfig.FallbackConfig fb = yamlAi.getFallback();
        if (fb != null && fb.isEnabled() && fb.getModel() != null) {
            AiProvider fallback = new AiProvider();
            fallback.setName("YAML Fallback");
            fallback.setBaseUrl(fb.getBaseUrl());
            fallback.setApiKey(resolveEnvPlaceholder(fb.getApiKey()));
            fallback.setModel(fb.getModel());
            fallback.setConnectTimeoutMs(fb.getConnectTimeoutMs());
            fallback.setReadTimeoutMs(fb.getReadTimeoutMs());
            fallback.setEnabled(true);
            fallback.setPriority(20);
            aiProviderMapper.insert(fallback);
        }
        savePolicyFields(yamlAi.getMaxRetries(), yamlAi.getRetryDelayMs(),
                yamlAi.getFailureThreshold(), yamlAi.getCooldownMs());
        log.info("Imported AI providers from config.yaml");
    }

    public List<AiProvider> listEnabledForRuntime() {
        return aiProviderMapper.findEnabled().stream()
                .filter(p -> AiChatClient.hasKey(p.toProviderConfig()))
                .sorted(Comparator.comparingInt(p -> p.getPriority() != null ? p.getPriority() : 100))
                .collect(Collectors.toList());
    }

    public boolean hasAnyConfiguredEnabledProvider() {
        return !listEnabledForRuntime().isEmpty();
    }

    public AiPolicy getPolicy() {
        AiPolicy policy = new AiPolicy();
        policy.setMaxRetries(parseInt(appSettingMapper.get("ai_max_retries"), 3));
        policy.setRetryDelayMs(parseInt(appSettingMapper.get("ai_retry_delay_ms"), 1000));
        policy.setFailureThreshold(parseInt(appSettingMapper.get("ai_failure_threshold"), 2));
        policy.setCooldownMs(parseLong(appSettingMapper.get("ai_cooldown_ms"), 120_000L));
        policy.setLongReadTimeoutMs(parseInt(appSettingMapper.get("ai_long_read_timeout_ms"), 300_000));
        return policy;
    }

    public AiPolicyDto getPolicyDto() {
        AiPolicy p = getPolicy();
        AiPolicyDto dto = new AiPolicyDto();
        dto.setMaxRetries(p.getMaxRetries());
        dto.setRetryDelayMs(p.getRetryDelayMs());
        dto.setFailureThreshold(p.getFailureThreshold());
        dto.setCooldownMs(p.getCooldownMs());
        dto.setLongReadTimeoutMs(p.getLongReadTimeoutMs());
        return dto;
    }

    @Transactional
    public AiPolicyDto savePolicy(AiPolicyDto dto) {
        if (dto.getMaxRetries() != null) {
            appSettingMapper.put("ai_max_retries", String.valueOf(dto.getMaxRetries()));
        }
        if (dto.getRetryDelayMs() != null) {
            appSettingMapper.put("ai_retry_delay_ms", String.valueOf(dto.getRetryDelayMs()));
        }
        if (dto.getFailureThreshold() != null) {
            appSettingMapper.put("ai_failure_threshold", String.valueOf(dto.getFailureThreshold()));
        }
        if (dto.getCooldownMs() != null) {
            appSettingMapper.put("ai_cooldown_ms", String.valueOf(dto.getCooldownMs()));
        }
        if (dto.getLongReadTimeoutMs() != null) {
            appSettingMapper.put("ai_long_read_timeout_ms", String.valueOf(dto.getLongReadTimeoutMs()));
        }
        return getPolicyDto();
    }

    public List<AiProviderResponse> listForApi() {
        return aiProviderMapper.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    public PageResult<AiProviderResponse> listForApiPage(int page, int size) {
        int s = PageRequestUtil.clampSize(size);
        int p = PageRequestUtil.clampPage(page);
        int offset = PageRequestUtil.offset(p, s);
        List<AiProviderResponse> items = aiProviderMapper.findPage(offset, s).stream()
                .map(this::toResponse).collect(Collectors.toList());
        return new PageResult<>(items, aiProviderMapper.count(), p, s);
    }

    @Transactional
    public AiProviderResponse create(AiProviderRequest req) {
        AiProvider p = fromRequest(req, new AiProvider());
        if (p.getPriority() == null) {
            p.setPriority(nextPriority());
        }
        p.setEnabled(req.getEnabled() == null || req.getEnabled());
        aiProviderMapper.insert(p);
        return toResponse(aiProviderMapper.findById(p.getId()));
    }

    @Transactional
    public AiProviderResponse update(Long id, AiProviderRequest req) {
        AiProvider existing = aiProviderMapper.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("AI 配置不存在");
        }
        AiProvider p = fromRequest(req, existing);
        p.setId(id);
        if (req.getEnabled() != null) {
            p.setEnabled(req.getEnabled());
        }
        if (req.getApiKey() == null || req.getApiKey().isBlank() || UNCHANGED_SECRET.equals(req.getApiKey())) {
            p.setApiKey(existing.getApiKey());
        }
        aiProviderMapper.update(p);
        return toResponse(aiProviderMapper.findById(id));
    }

    @Transactional
    public void delete(Long id) {
        aiProviderMapper.delete(id);
    }

    @Transactional
    public AiProviderResponse setEnabled(Long id, boolean enabled) {
        aiProviderMapper.updateEnabled(id, enabled);
        AiProvider p = aiProviderMapper.findById(id);
        if (p == null) {
            throw new IllegalArgumentException("AI 配置不存在");
        }
        return toResponse(p);
    }

    private int nextPriority() {
        return aiProviderMapper.findAll().stream()
                .map(AiProvider::getPriority)
                .filter(p -> p != null)
                .max(Integer::compareTo)
                .orElse(0) + 10;
    }

    private AiProviderResponse toResponse(AiProvider p) {
        AiProviderResponse r = new AiProviderResponse();
        r.setId(p.getId());
        r.setName(p.getName());
        r.setBaseUrl(p.getBaseUrl());
        r.setModel(p.getModel());
        r.setConnectTimeoutMs(p.getConnectTimeoutMs());
        r.setReadTimeoutMs(p.getReadTimeoutMs());
        r.setEnabled(Boolean.TRUE.equals(p.getEnabled()));
        r.setPriority(p.getPriority());
        r.setApiKeyConfigured(AiChatClient.hasKey(p.toProviderConfig()));
        return r;
    }

    private AiProvider fromRequest(AiProviderRequest req, AiProvider p) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new IllegalArgumentException("请填写名称");
        }
        p.setName(req.getName().trim());
        p.setBaseUrl(req.getBaseUrl() != null ? req.getBaseUrl().trim() : "https://api.openai.com/v1");
        p.setModel(req.getModel() != null ? req.getModel().trim() : "gpt-4o-mini");
        p.setConnectTimeoutMs(req.getConnectTimeoutMs() != null ? req.getConnectTimeoutMs() : 5000);
        p.setReadTimeoutMs(req.getReadTimeoutMs() != null ? req.getReadTimeoutMs() : 60000);
        if (req.getPriority() != null) {
            p.setPriority(req.getPriority());
        }
        if (req.getApiKey() != null && !req.getApiKey().isBlank() && !UNCHANGED_SECRET.equals(req.getApiKey())) {
            p.setApiKey(req.getApiKey().trim());
        }
        return p;
    }

    private void savePolicyFields(Integer maxRetries, Integer retryDelay, Integer threshold, Long cooldown) {
        appSettingMapper.put("ai_max_retries", String.valueOf(nz(maxRetries, 3)));
        appSettingMapper.put("ai_retry_delay_ms", String.valueOf(nz(retryDelay, 1000)));
        appSettingMapper.put("ai_failure_threshold", String.valueOf(nz(threshold, 2)));
        appSettingMapper.put("ai_cooldown_ms", String.valueOf(cooldown != null ? cooldown : 120_000L));
    }

    private static String resolveEnvPlaceholder(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.startsWith("${") && value.endsWith("}")) {
            String inner = value.substring(2, value.length() - 1);
            int colon = inner.indexOf(':');
            String envName = colon >= 0 ? inner.substring(0, colon) : inner;
            String defaultVal = colon >= 0 ? inner.substring(colon + 1) : "";
            String env = System.getenv(envName);
            return env != null && !env.isBlank() ? env : defaultVal;
        }
        return value;
    }

    private static int nz(Integer v, int d) {
        return v != null ? v : d;
    }

    private static String blankOr(String s, String d) {
        return s == null || s.isBlank() ? d : s;
    }

    private static int parseInt(String s, int d) {
        if (s == null || s.isBlank()) {
            return d;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return d;
        }
    }

    private static long parseLong(String s, long d) {
        if (s == null || s.isBlank()) {
            return d;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return d;
        }
    }
}
