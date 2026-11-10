package com.dailyreport.service;

import com.dailyreport.web.dto.PerformanceConfigDto;
import com.dailyreport.web.dto.PerformanceContentDto;
import com.dailyreport.domain.PerformanceReview;
import com.dailyreport.mapper.PerformanceReviewMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.Map;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Service
public class PerformanceExcelService {

    private final PerformanceReviewMapper performanceReviewMapper;

    public PerformanceExcelService(PerformanceReviewMapper performanceReviewMapper) {
        this.performanceReviewMapper = performanceReviewMapper;
    }

    public String export(YearMonth ym, PerformanceConfigDto config, PerformanceContentDto content) throws IOException {
        Path outDir = Paths.get("./output/performance");
        Files.createDirectories(outDir);
        String assessee = safe(config.getAssessee());
        String filename = String.format("%s-%s-%s_%d月绩效评估表.xlsx",
                config.getDepartment(), config.getRole(), assessee, ym.getMonthValue());
        Path outPath = outDir.resolve(filename);

        Path template = resolveTemplate(config, ym, config.getAssessee());
        try (InputStream in = Files.newInputStream(template);
             Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : wb.createSheet("Sheet1");
            if (content.getSheetName() != null && !content.getSheetName().isBlank()) {
                int idx = wb.getSheetIndex(sheet);
                wb.setSheetName(idx, content.getSheetName());
            }
            setCell(sheet, 0, 0, content.getA1Title());
            setCell(sheet, 4, 3, content.getKpiD5());
            setCell(sheet, 11, 3, content.getAuxiliaryD12());
            setCell(sheet, 14, 3, content.getAttitudeD15());
            setCell(sheet, 37, 1, content.getOverallB38());

            Map<String, Integer> scores = content.getScores() != null ? content.getScores() : PerformanceContentDto.defaultScores();
            setNumeric(sheet, 4, 7, scores.getOrDefault("h5", config.getScoreKpi()));
            setNumeric(sheet, 11, 7, scores.getOrDefault("h12", config.getScoreAuxiliary()));
            setNumeric(sheet, 14, 7, scores.getOrDefault("h15", config.getScoreAttitude()));
            setNumeric(sheet, 32, 7, scores.getOrDefault("h33", config.getScoreOther()));

            Row totalRow = getOrCreateRow(sheet, 35);
            Cell sumCell = getOrCreateCell(totalRow, 7);
            sumCell.setCellFormula("SUM(H5:H35)");

            adjustRowHeight(sheet, 4, content.getKpiD5());
            adjustRowHeight(sheet, 11, content.getAuxiliaryD12());
            adjustRowHeight(sheet, 14, content.getAttitudeD15());

            try (OutputStream os = Files.newOutputStream(outPath)) {
                wb.write(os);
            }
        }
        log.info("绩效 Excel 已导出: {}", outPath.toAbsolutePath());
        return outPath.toAbsolutePath().normalize().toString();
    }

    private Path resolveTemplate(PerformanceConfigDto config, YearMonth ym, String author) throws IOException {
        Path configured = Paths.get(config.getTemplatePath());
        if (Files.isRegularFile(configured)) {
            return configured;
        }
        String dir = config.getTemplateSearchDir();
        if (dir != null && !dir.isBlank()) {
            Path search = Paths.get(dir);
            if (Files.isDirectory(search)) {
                try (Stream<Path> files = Files.list(search)) {
                    Optional<Path> latest = files
                            .filter(p -> p.toString().endsWith(".xlsx"))
                            .max(Comparator.comparing(p -> p.toFile().lastModified()));
                    if (latest.isPresent()) {
                        return latest.get();
                    }
                }
            }
        }
        if (author != null && !author.isBlank()) {
            String ymKey = String.format("%04d-%02d", ym.getYear(), ym.getMonthValue());
            PerformanceReview prev = performanceReviewMapper.findLatestBefore(author, ymKey);
            if (prev != null && prev.getExcelPath() != null) {
                Path prevFile = Paths.get(prev.getExcelPath());
                if (Files.isRegularFile(prevFile)) {
                    return prevFile;
                }
            }
        }
        Path fallback = Paths.get("./templates/performance/performance_template.xlsx");
        if (!Files.isRegularFile(fallback)) {
            createDefaultTemplate(fallback);
        }
        return fallback;
    }

    private void createDefaultTemplate(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("模板");
            setCell(sheet, 0, 0, "XX年X月综合评估表");
            setCell(sheet, 4, 3, "（KPI 内容 D5）");
            setCell(sheet, 11, 3, "（辅助绩效 D12）");
            setCell(sheet, 14, 3, "（工作态度 D15）");
            setCell(sheet, 37, 1, "（综合评估 B38）");
            setNumeric(sheet, 4, 7, 55);
            setNumeric(sheet, 11, 7, 18);
            setNumeric(sheet, 14, 7, 20);
            setNumeric(sheet, 32, 7, 0);
            getOrCreateCell(getOrCreateRow(sheet, 35), 7).setCellFormula("SUM(H5:H35)");
            sheet.setColumnWidth(3, 80 * 256);
            try (OutputStream os = Files.newOutputStream(path)) {
                wb.write(os);
            }
        }
        log.info("已创建默认绩效模板: {}", path);
    }

    private static void setCell(Sheet sheet, int rowIdx, int colIdx, String value) {
        Row row = getOrCreateRow(sheet, rowIdx);
        Cell cell = getOrCreateCell(row, colIdx);
        cell.setCellValue(value != null ? value : "");
    }

    private static void setNumeric(Sheet sheet, int rowIdx, int colIdx, int value) {
        Cell cell = getOrCreateCell(getOrCreateRow(sheet, rowIdx), colIdx);
        cell.setCellValue(value);
    }

    private static Row getOrCreateRow(Sheet sheet, int rowIdx) {
        Row row = sheet.getRow(rowIdx);
        return row != null ? row : sheet.createRow(rowIdx);
    }

    private static Cell getOrCreateCell(Row row, int colIdx) {
        Cell cell = row.getCell(colIdx);
        return cell != null ? cell : row.createCell(colIdx);
    }

    private static void adjustRowHeight(Sheet sheet, int rowIdx, String text) {
        if (text == null || text.isBlank()) return;
        Row row = getOrCreateRow(sheet, rowIdx);
        int lines = Math.max(3, text.split("\n", -1).length);
        row.setHeightInPoints(Math.min(409, lines * 15f));
    }

    private static String safe(String s) {
        return s == null ? "员工" : s.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
