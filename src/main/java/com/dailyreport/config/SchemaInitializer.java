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
