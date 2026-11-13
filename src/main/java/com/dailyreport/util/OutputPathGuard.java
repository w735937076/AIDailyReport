package com.dailyreport.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Safe delete for report export files under ./output (excludes performance exports).
 */
@Slf4j
public final class OutputPathGuard {

    private OutputPathGuard() {
    }

    public static Path outputRoot() {
        return new File("./output").getAbsoluteFile().toPath().normalize();
    }

    public static boolean isUnderOutputDirectory(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        Path normalized = new File(path).getAbsoluteFile().toPath().normalize();
        return normalized.startsWith(outputRoot());
    }

    public static boolean isReportOutputFile(String path) {
        if (!isUnderOutputDirectory(path)) {
            return false;
        }
        Path normalized = new File(path).getAbsoluteFile().toPath().normalize();
        Path performanceDir = outputRoot().resolve("performance");
        Path interviewDir = outputRoot().resolve("interview");
        return !normalized.startsWith(performanceDir) && !normalized.startsWith(interviewDir);
    }

    public static void deleteReportOutputFileIfAllowed(String path) throws IOException {
        if (path == null || path.isBlank()) {
            return;
        }
        if (!isReportOutputFile(path)) {
            log.warn("Skip deleting path outside report output: {}", path);
            return;
        }
        Files.deleteIfExists(Path.of(path));
    }
}
