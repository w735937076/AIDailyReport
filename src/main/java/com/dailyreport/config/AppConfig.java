package com.dailyreport.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "")
@PropertySource(value = "file:./config.yaml", factory = YamlPropertySourceFactory.class, ignoreResourceNotFound = true)
public class AppConfig {

    private GitConfig git = new GitConfig();
    private AiConfig ai = new AiConfig();
    private ReportConfig report = new ReportConfig();
    private SchedulerConfig scheduler = new SchedulerConfig();

    @Data
    public static class GitConfig {
        private List<RepoConfig> repos;
        private AccountConfig account = new AccountConfig();
    }

    @Data
    public static class RepoConfig {
        private String name;
        private String path;
        private String remote;
        private String branch = "main";
    }

    @Data
    public static class AccountConfig {
        private String username;
        private String password;
    }

    @Data
    public static class AiProviderConfig {
        private String apiKey;
        private String baseUrl = "https://api.deepseek.com";
        private String model = "deepseek-v4-pro";
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 60000;
    }

    @Data
    @lombok.EqualsAndHashCode(callSuper = true)
    public static class AiConfig extends AiProviderConfig {
        private int maxRetries = 3;
        private int retryDelayMs = 1000;
        private int failureThreshold = 2;
        private long cooldownMs = 120000;
        private FallbackConfig fallback = new FallbackConfig();
    }

    @Data
    @lombok.EqualsAndHashCode(callSuper = true)
    public static class FallbackConfig extends AiProviderConfig {
        private boolean enabled = true;

        public FallbackConfig() {
            setBaseUrl("https://api.minimax.cn/v1");
            setModel("MiniMax-M3.1-Flash");
            setApiKey(System.getenv("MINIMAX_API_KEY"));
        }
    }

    @Data
    public static class ReportConfig {
        private String templateDir = "./templates";
        private String outputDir = "./output";
        private String dailyTemplate = "daily_template.xlsx";
        private String weeklyTemplate = "weekly_template.xlsx";
        private String monthlyTemplate = "monthly_template.xlsx";
    }

    @Data
    public static class SchedulerConfig {
        private boolean enabled = true;
        private String cron = "0 0 18 * * ?";
    }
}
