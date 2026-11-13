package com.dailyreport.service;

import com.dailyreport.web.dto.PerformanceConfigDto;
import com.dailyreport.web.dto.PerformanceContentDto;
import com.dailyreport.domain.PerformanceReview;
import com.dailyreport.mapper.PerformanceReviewMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

            applyConfigIdentityFields(sheet, config);

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

    /**
     * Overwrite signature / header identity cells so MonthPerformance template leftovers
     * (e.g. wrong 评估人) are replaced from current 绩效配置.
     */
    static void applyConfigIdentityFields(Sheet sheet, PerformanceConfigDto config) {
        String assessee = trimOrEmpty(config.getAssessee());
        String assessor = trimOrEmpty(config.getAssessor());
        String department = trimOrEmpty(config.getDepartment());
        String role = trimOrEmpty(config.getRole());

        Set<String> mergeAnchorsHandled = new HashSet<>();
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            int r = region.getFirstRow();
            int c = region.getFirstColumn();
            if (r > 60) {
                continue;
            }
            String anchor = r + ":" + c;
            if (mergeAnchorsHandled.contains(anchor)) {
                continue;
            }
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            String text = readCellAsString(row.getCell(c)).trim();
            if (!text.isEmpty() && touchesSignatureLabels(text)) {
                setCell(sheet, r, c, rewriteSignatureText(text, assessee, assessor));
                mergeAnchorsHandled.add(anchor);
            }
        }

        int lastRow = sheet.getLastRowNum();
        for (int r = 0; r <= lastRow && r < 60; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            short lastCell = row.getLastCellNum();
            for (int c = 0; c < lastCell && c < 24; c++) {
                if (isMergeTopLeftHandled(sheet, r, c, mergeAnchorsHandled)) {
                    continue;
                }
                String text = readCellAsString(row.getCell(c));
                if (text == null || text.isBlank()) {
                    continue;
                }
                String t = text.trim();
                if (!touchesSignatureLabels(t)) {
                    continue;
                }
                if (isCombinedSignatureLine(t)) {
                    setCell(sheet, r, c, rewriteSignatureText(t, assessee, assessor));
                } else if (t.contains("被评估人")) {
                    setCell(sheet, r, c, "被评估人：" + assessee);
                } else if (t.contains("评估人")) {
                    setCell(sheet, r, c, "评估人：" + assessor);
                } else if (t.contains("人事确认") || t.contains("人事审核")) {
                    setCell(sheet, r, c, t.contains("人事确认") ? "人事确认：" : "人事审核：");
                } else if (t.startsWith("日期：") || t.startsWith("日期:")) {
                    setCell(sheet, r, c, "日期：");
                }
            }
        }

        fillHeaderIdentityPairs(sheet, department, role, assessee);
    }

    private static boolean touchesSignatureLabels(String t) {
        return t.contains("被评估人") || t.contains("评估人")
                || t.contains("人事确认") || t.contains("人事审核")
                || t.startsWith("日期：") || t.startsWith("日期:");
    }

    private static boolean isCombinedSignatureLine(String t) {
        if (!t.contains("被评估人")) {
            return false;
        }
        return t.contains("评估人") || t.contains("人事确认") || t.contains("人事审核") || t.contains("日期");
    }

    /**
     * Patch a combined signature line in place (official template often merges B:H into one cell).
     */
    static String rewriteSignatureText(String raw, String assessee, String assessor) {
        if (raw == null) {
            return "";
        }
        String s = raw;
        s = setLabelValue(s, "被评估人", assessee);
        s = setAssessorValue(s, assessor);
        s = clearLabelValueIfNameOnly(s, "人事确认");
        s = clearLabelValueIfNameOnly(s, "人事审核");
        s = clearLabelValueIfNameOnly(s, "日期");
        return s;
    }

    private static String setLabelValue(String text, String label, String value) {
        int idx = text.indexOf(label);
        if (idx < 0) {
            return text;
        }
        return replaceValueAfterLabel(text, idx + label.length(), value);
    }

    private static String setAssessorValue(String text, String assessor) {
        int search = 0;
        while (true) {
            int idx = text.indexOf("评估人", search);
            if (idx < 0) {
                return text;
            }
            if (idx > 0 && text.charAt(idx - 1) == '被') {
                search = idx + 3;
                continue;
            }
            return replaceValueAfterLabel(text, idx + "评估人".length(), assessor);
        }
    }

    private static String replaceValueAfterLabel(String text, int fromLabelEnd, String value) {
        int colon = indexOfColon(text, fromLabelEnd);
        if (colon < 0) {
            return text;
        }
        int valueStart = colon + 1;
        while (valueStart < text.length() && Character.isWhitespace(text.charAt(valueStart))) {
            valueStart++;
        }
        int valueEnd = valueStart;
        while (valueEnd < text.length() && !Character.isWhitespace(text.charAt(valueEnd))) {
            valueEnd++;
        }
        return text.substring(0, valueStart) + value + text.substring(valueEnd);
    }

    /** Remove stray names after label; keep label and following section labels (e.g. 日期：). */
    private static String clearLabelValueIfNameOnly(String text, String label) {
        int idx = text.indexOf(label);
        if (idx < 0) {
            return text;
        }
        int from = idx + label.length();
        int colon = indexOfColon(text, from);
        if (colon < 0) {
            return text;
        }
        int valueStart = colon + 1;
        while (valueStart < text.length() && Character.isWhitespace(text.charAt(valueStart))) {
            valueStart++;
        }
        if (valueStart >= text.length()) {
            return text;
        }
        if (startsWithKnownLabel(text, valueStart)) {
            return text;
        }
        int valueEnd = valueStart;
        while (valueEnd < text.length() && !Character.isWhitespace(text.charAt(valueEnd))) {
            valueEnd++;
        }
        if (valueEnd == valueStart) {
            return text;
        }
        return text.substring(0, valueStart) + text.substring(valueEnd);
    }

    private static int indexOfColon(String text, int from) {
        int c1 = text.indexOf('：', from);
        int c2 = text.indexOf(':', from);
        if (c1 < 0) {
            return c2;
        }
        if (c2 < 0) {
            return c1;
        }
        return Math.min(c1, c2);
    }

    private static boolean startsWithKnownLabel(String text, int pos) {
        String rest = text.substring(pos);
        return rest.startsWith("被评估人") || rest.startsWith("评估人")
                || rest.startsWith("人事确认") || rest.startsWith("人事审核")
                || rest.startsWith("日期");
    }

    private static boolean isMergeTopLeftHandled(Sheet sheet, int row, int col, Set<String> handledAnchors) {
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.isInRange(row, col)) {
                String anchor = region.getFirstRow() + ":" + region.getFirstColumn();
                return handledAnchors.contains(anchor);
            }
        }
        return false;
    }

    private static void fillHeaderIdentityPairs(Sheet sheet, String department, String role, String assessee) {
        for (int r = 0; r <= 6; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (int c = 0; c < 12; c++) {
                String label = readCellAsString(row.getCell(c)).trim();
                if (label.equals("部门") || label.equals("部门：") || label.equals("部门:")) {
                    setCell(sheet, r, c + 1, department);
                } else if (label.equals("岗位") || label.equals("职位") || label.equals("岗位：") || label.equals("职位：")) {
                    setCell(sheet, r, c + 1, role);
                } else if (label.equals("姓名") || label.equals("姓名：")) {
                    setCell(sheet, r, c + 1, assessee);
                }
            }
        }
    }

    private static String readCellAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        yield String.valueOf(cell.getNumericCellValue());
                    } catch (Exception e2) {
                        yield "";
                    }
                }
            }
            default -> "";
        };
    }

    private static String trimOrEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
