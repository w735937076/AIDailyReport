package com.dailyreport.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public SchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        runScript("schema.sql");
        runScript("schema_web.sql");
        migratePerformanceReviewMonthColumn();
    }

    /** MySQL treats YEAR_MONTH as reserved; rename legacy year_month column if present. */
    private void migratePerformanceReviewMonthColumn() {
        try {
            Integer legacyCols = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'performance_review' AND COLUMN_NAME = 'year_month'",
                    Integer.class);
            if (legacyCols == null || legacyCols == 0) {
                return;
            }
            jdbcTemplate.execute(
                    "ALTER TABLE performance_review CHANGE COLUMN year_month review_month VARCHAR(7) NOT NULL COMMENT 'yyyy-MM'");
            try {
                jdbcTemplate.execute("DROP INDEX idx_perf_year_month ON performance_review");
            } catch (Exception ignored) {
            }
            try {
                jdbcTemplate.execute("CREATE INDEX idx_perf_review_month ON performance_review(review_month)");
            } catch (Exception ignored) {
            }
            log.info("Migrated performance_review.year_month to review_month");
        } catch (Exception e) {
            log.warn("performance_review column migration failed: {}", e.getMessage());
        }
    }

    private void runScript(String resourceName) {
        try {
            ClassPathResource resource = new ClassPathResource(resourceName);
            if (!resource.exists()) {
                return;
            }
            String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            for (String statement : splitStatements(sql)) {
                try {
                    jdbcTemplate.execute(statement);
                } catch (Exception e) {
                    log.debug("Schema statement skipped ({}): {}", resourceName, e.getMessage());
                }
            }
            log.info("Applied schema script: {}", resourceName);
        } catch (Exception e) {
            log.warn("Could not apply {} (database may be unavailable yet): {}", resourceName, e.getMessage());
        }
    }

    private static Iterable<String> splitStatements(String sql) {
        return Arrays.stream(sql.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.startsWith("--"))
                .collect(Collectors.toList());
    }
}
