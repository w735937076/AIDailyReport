package com.dailyreport.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DatabaseInitializer implements ApplicationRunner {

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        try {
            createDirectories();
        } catch (Exception e) {
            throw new RuntimeException("目录初始化失败", e);
        }
    }

    private void createDirectories() throws IOException {
        // 创建 logs 目录
        Path logsDir = Paths.get("./logs");
        if (!Files.exists(logsDir)) {
            Files.createDirectories(logsDir);
            System.out.println("创建目录: " + logsDir.toAbsolutePath());
        }

        // 创建 templates 目录
        Path templateDir = Paths.get("./templates");
        if (!Files.exists(templateDir)) {
            Files.createDirectories(templateDir);
        }

        // 创建 output 目录
        Path outputDir = Paths.get("./output");
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
    }
}
