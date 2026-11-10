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
import java.util.List;
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
        ensurePerformanceReviewTable();
        migratePerformanceReviewMonthColumn();
    }

    private void ensurePerformanceReviewTable() {
        try {
            Integer tables = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TABLES " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'performance_review'",
                    Integer.class);
            if (tables != null && tables > 0) {
                return;
            }
            log.info("performance_review missing; applying bootstrap DDL");
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS performance_review (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        review_month VARCHAR(7) NOT NULL COMMENT 'yyyy-MM',
                        author VARCHAR(100) NOT NULL,
                        status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
                        content_json LONGTEXT,
                        commit_stats_json TEXT,
                        excel_path VARCHAR(500),
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        UNIQUE KEY uk_perf_month_author (review_month, author)
                    )""");
            try {
                jdbcTemplate.execute("CREATE INDEX idx_perf_review_month ON performance_review(review_month)");
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            log.warn("Could not ensure performance_review table: {}", e.getMessage());
        }
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

    /**
     * Splits a script on {@code ;} and removes full-line {@code --} comments from each chunk.
     * A chunk must not be dropped just because a leading comment line precedes real DDL.
     */
    static List<String> splitStatements(String sql) {
        return Arrays.stream(sql.split(";"))
                .map(SchemaInitializer::stripLineCommentsAndTrim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static String stripLineCommentsAndTrim(String chunk) {
        StringBuilder sb = new StringBuilder();
        for (String line : chunk.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(line);
        }
        return sb.toString().trim();
    }
}
