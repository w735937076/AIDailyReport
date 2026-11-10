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
    private PerformanceConfig performance = new PerformanceConfig();

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

    /** 绩效评估默认项（可与 Web 配置合并；勿在仓库中存放真实姓名模板） */
    @Data
    public static class PerformanceConfig {
        private String department = "技术部";
        private String role = "java";
        private String assessee = "";
        private String assessor = "";
        private String templatePath = "./templates/performance/performance_template.xlsx";
        /** 本地 MonthPerformance 等目录，用于选用最新 xlsx 作样式模板（不提交到 git） */
        private String templateSearchDir = "";
        private int scoreKpi = 55;
        private int scoreAuxiliary = 18;
        private int scoreAttitude = 20;
        private int scoreOther = 0;
    }
}
