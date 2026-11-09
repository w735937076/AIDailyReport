package com.dailyreport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
@EnableScheduling
public class DailyReportApplication {

    public static void main(String[] args) {
        // 确保必要目录存在
        createDirectories();
        SpringApplication.run(DailyReportApplication.class, args);
    }

    private static void createDirectories() {
        try {
            Path logsDir = Paths.get("./logs");
            if (!Files.exists(logsDir)) {
                Files.createDirectories(logsDir);
            }

            Path templateDir = Paths.get("./templates");
            if (!Files.exists(templateDir)) {
                Files.createDirectories(templateDir);
            }

            Path outputDir = Paths.get("./output");
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }
        } catch (Exception e) {
            System.err.println("创建目录失败: " + e.getMessage());
        }
    }
}
