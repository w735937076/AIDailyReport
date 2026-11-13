package com.dailyreport.service;

import com.dailyreport.domain.PerformanceInterview;
import com.dailyreport.mapper.PerformanceInterviewMapper;
import com.dailyreport.web.dto.InterviewConfigDto;
import com.dailyreport.web.dto.InterviewContentDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Service
public class InterviewExcelService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String SHEET_NAME = "绩效面谈";

    private static final String Q1 = "1、你觉得自己上一阶段做得比较好的有哪些方面？（要结合案例）";
    private static final String Q2 = "2、分享一下你的成功经验？（结合上一阶段完成较好的工作任务，对成功经验进行总结）（要结合案例）";
    private static final String Q3 = "3、你觉得自己在上一阶段哪些方面做得不是很好，需要进一步改善？（要结合案例）";
    private static final String Q4 = "4、你在工作中有遇到哪些困难？如何解决？（自己的解决方案，要结合案例）";
    private static final String Q5 = "5、改进措施（部门负责人需对未完成的工作目标分析原因并给出改进措施）";
    private static final String Q6 = "你接下来的工作目标、工作计划是怎么样的呢？";
    private static final String Q7 = "你对部门及公司机制还存在哪些疑问？";

    private final PerformanceInterviewMapper interviewMapper;

    public InterviewExcelService(PerformanceInterviewMapper interviewMapper) {
        this.interviewMapper = interviewMapper;
    }

    public String export(YearMonth ym, InterviewConfigDto config, InterviewContentDto content) throws IOException {
        Path outDir = Paths.get("./output/interview");
        Files.createDirectories(outDir);
        String filename = String.format("%s-%s-%s_%d月绩效面谈表.xlsx",
                safe(config.getDepartment()), safe(config.getRole()), safe(config.getAssessee()), ym.getMonthValue());
        Path outPath = outDir.resolve(filename);

        Path template = resolveTemplate(config, ym, config.getAssessee());
        boolean fromBlank = template.toString().contains("templates/interview");
        try (InputStream in = Files.newInputStream(template);
             Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = findInterviewSheet(wb);
            if (fromBlank || sheet.getPhysicalNumberOfRows() < 5) {
                buildBlankOfficialSheet(wb, sheet);
            } else {
                clearPreviousAnswers(sheet);
            }
            ensureHeaderLabels(sheet);
            fillHeaderRow(sheet, config, content);
            fillAnswers(sheet, content);
            renameSheet(wb, sheet, content.getSheetName());
            try (OutputStream os = Files.newOutputStream(outPath)) {
                wb.write(os);
            }
        }
        log.info("绩效面谈 Excel 已导出: {}", outPath.toAbsolutePath());
        return outPath.toAbsolutePath().normalize().toString();
    }

    private void fillHeaderRow(Sheet sheet, InterviewConfigDto config, InterviewContentDto content) {
        setCell(sheet, 1, 2, nullToEmpty(config.getAssessee()));
        setCell(sheet, 1, 4, nullToEmpty(config.getDepartment()));
        setCell(sheet, 1, 6, nullToEmpty(config.getRole()));
        LocalDate hire = parseDate(config.getHireDate());
        setLocalDate(sheet, 1, 8, hire);
        LocalDate interview = parseDate(content.getInterviewDate());
        setLocalDate(sheet, 1, 10, interview);
        setCell(sheet, 1, 9, "面谈时间");
    }

    private void fillAnswers(Sheet sheet, InterviewContentDto content) {
        setWrapped(sheet, 2, 5, content.getAnswerF3());
        setWrapped(sheet, 3, 5, content.getAnswerF4());
        setWrapped(sheet, 4, 5, content.getAnswerF5());
        setWrapped(sheet, 5, 5, content.getAnswerF6());
        setWrapped(sheet, 6, 5, "");
        setWrapped(sheet, 7, 5, content.getAnswerF8());
        setWrapped(sheet, 8, 5, content.getAnswerF9());
    }

    private void clearPreviousAnswers(Sheet sheet) {
        int[][] cells = {
                {1, 2}, {1, 4}, {1, 6}, {1, 8}, {1, 10},
                {2, 5}, {3, 5}, {4, 5}, {5, 5}, {6, 5}, {7, 5}, {8, 5},
        };
        for (int[] c : cells) {
            setCell(sheet, c[0], c[1], "");
        }
        setCell(sheet, 1, 9, "面谈时间");
    }

    private void ensureHeaderLabels(Sheet sheet) {
        setCell(sheet, 1, 0, "项目");
        setCell(sheet, 1, 1, "姓名：");
        setCell(sheet, 1, 3, "部门：");
        setCell(sheet, 1, 5, "职位：");
        setCell(sheet, 1, 7, "入职时间");
        setCell(sheet, 1, 9, "面谈时间");
    }

    private void buildBlankOfficialSheet(Workbook wb, Sheet sheet) {
        Styles styles = new Styles(wb);
        int idx = wb.getSheetIndex(sheet);
        wb.setSheetName(idx, SHEET_NAME);

        setCellStyled(sheet, 0, 0, "绩效面谈记录表", styles.title);
        merge(sheet, 0, 0, 0, 10);

        ensureHeaderLabels(sheet);
        applyLabelStyle(sheet, 1, 0, 10, styles.header);

        setCellStyled(sheet, 2, 0, "工作业绩", styles.header);
        merge(sheet, 2, 5, 0, 0);

        String[] questions = {Q1, Q2, Q3, Q4};
        for (int i = 0; i < questions.length; i++) {
            int r = 2 + i;
            setCellStyled(sheet, r, 1, questions[i], styles.label);
            merge(sheet, r, r, 1, 4);
            merge(sheet, r, r, 5, 10);
            applyBorderRange(sheet, r, r, 1, 10, styles.body);
        }

        setCellStyled(sheet, 6, 0, "改进措施", styles.header);
        setCellStyled(sheet, 6, 1, Q5, styles.label);
        merge(sheet, 6, 6, 1, 4);
        merge(sheet, 6, 6, 5, 10);

        setCellStyled(sheet, 7, 0, "新的目标", styles.header);
        setCellStyled(sheet, 7, 1, Q6, styles.label);
        merge(sheet, 7, 7, 1, 4);
        merge(sheet, 7, 7, 5, 10);

        setCellStyled(sheet, 8, 0, "意见建议", styles.header);
        setCellStyled(sheet, 8, 1, Q7, styles.label);
        merge(sheet, 8, 8, 1, 4);
        merge(sheet, 8, 8, 5, 10);

        setCellStyled(sheet, 9, 0, "部门负责人签字：", styles.label);
        merge(sheet, 9, 9, 0, 10);

        for (int r = 0; r <= 9; r++) {
            for (int c = 0; c <= 10; c++) {
                Cell cell = getOrCreateCell(getOrCreateRow(sheet, r), c);
                if (cell.getCellStyle() == null || cell.getCellStyle().getIndex() == 0) {
                    cell.setCellStyle(styles.body);
                }
            }
        }
        getOrCreateCell(getOrCreateRow(sheet, 0), 0).setCellStyle(styles.title);

        sheet.setColumnWidth(0, 12 * 256);
        sheet.setColumnWidth(1, 18 * 256);
        for (int c = 2; c <= 10; c++) {
            sheet.setColumnWidth(c, 11 * 256);
        }
    }

    private Path resolveTemplate(InterviewConfigDto config, YearMonth ym, String author) throws IOException {
        Path configured = Paths.get(config.getTemplatePath());
        if (Files.isRegularFile(configured)) {
            return copyToTempAndClear(configured);
        }
        String dir = config.getTemplateSearchDir();
        if (dir != null && !dir.isBlank()) {
            Path search = Paths.get(dir);
            if (Files.isDirectory(search)) {
                try (Stream<Path> files = Files.list(search)) {
                    Optional<Path> latest = files
                            .filter(p -> p.toString().toLowerCase().endsWith(".xlsx"))
                            .max(Comparator.comparing(p -> p.toFile().lastModified()));
                    if (latest.isPresent()) {
                        return latest.get();
                    }
                }
            }
        }
        if (author != null && !author.isBlank()) {
            String ymKey = String.format("%04d-%02d", ym.getYear(), ym.getMonthValue());
            PerformanceInterview prev = interviewMapper.findLatestBefore(author, ymKey);
            if (prev != null && prev.getExcelPath() != null) {
                Path prevFile = Paths.get(prev.getExcelPath());
                if (Files.isRegularFile(prevFile)) {
                    return prevFile;
                }
            }
        }
        Path fallback = Paths.get("./templates/interview/interview_template.xlsx");
        if (!Files.isRegularFile(fallback)) {
            Files.createDirectories(fallback.getParent());
            try (Workbook wb = new XSSFWorkbook()) {
                Sheet sheet = wb.createSheet(SHEET_NAME);
                buildBlankOfficialSheet(wb, sheet);
                try (OutputStream os = Files.newOutputStream(fallback)) {
                    wb.write(os);
                }
            }
            log.info("已创建默认面谈模板: {}", fallback);
        }
        return fallback;
    }

    private Path copyToTempAndClear(Path source) throws IOException {
        return source;
    }

    private Sheet findInterviewSheet(Workbook wb) {
        Sheet sheet = wb.getSheet(SHEET_NAME);
        if (sheet != null) {
            return sheet;
        }
        return wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : wb.createSheet(SHEET_NAME);
    }

    private static void renameSheet(Workbook wb, Sheet sheet, String name) {
        if (name != null && !name.isBlank()) {
            int idx = wb.getSheetIndex(sheet);
            wb.setSheetName(idx, name);
        }
    }

    private static void merge(Sheet sheet, int r1, int r2, int c1, int c2) {
        sheet.addMergedRegion(new CellRangeAddress(r1, r2, c1, c2));
    }

    private static void setCell(Sheet sheet, int rowIdx, int colIdx, String value) {
        Cell cell = getOrCreateCell(getOrCreateRow(sheet, rowIdx), colIdx);
        cell.setCellValue(value != null ? value : "");
    }

    private static void setCellStyled(Sheet sheet, int rowIdx, int colIdx, String value, CellStyle style) {
        Cell cell = getOrCreateCell(getOrCreateRow(sheet, rowIdx), colIdx);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private static void setWrapped(Sheet sheet, int rowIdx, int colIdx, String value) {
        setCell(sheet, rowIdx, colIdx, value);
        Row row = getOrCreateRow(sheet, rowIdx);
        if (value != null && !value.isBlank()) {
            int lines = Math.max(2, value.split("\n", -1).length);
            row.setHeightInPoints(Math.min(409, lines * 15f));
        }
        Cell cell = getOrCreateCell(row, colIdx);
        CellStyle style = cell.getCellStyle();
        if (style != null) {
            style.setWrapText(true);
            cell.setCellStyle(style);
        }
    }

    private static void setLocalDate(Sheet sheet, int rowIdx, int colIdx, LocalDate date) {
        if (date == null) {
            setCell(sheet, rowIdx, colIdx, "");
            return;
        }
        Cell cell = getOrCreateCell(getOrCreateRow(sheet, rowIdx), colIdx);
        cell.setCellValue(date);
    }

    private static Row getOrCreateRow(Sheet sheet, int rowIdx) {
        Row row = sheet.getRow(rowIdx);
        return row != null ? row : sheet.createRow(rowIdx);
    }

    private static Cell getOrCreateCell(Row row, int colIdx) {
        Cell cell = row.getCell(colIdx);
        return cell != null ? cell : row.createCell(colIdx);
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim(), ISO);
        } catch (Exception e) {
            return null;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String safe(String s) {
        return s == null ? "员工" : s.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static void applyLabelStyle(Sheet sheet, int row, int fromCol, int toCol, CellStyle style) {
        for (int c = fromCol; c <= toCol; c++) {
            getOrCreateCell(getOrCreateRow(sheet, row), c).setCellStyle(style);
        }
    }

    private static void applyBorderRange(Sheet sheet, int r1, int r2, int c1, int c2, CellStyle style) {
        for (int r = r1; r <= r2; r++) {
            for (int c = c1; c <= c2; c++) {
                Cell cell = getOrCreateCell(getOrCreateRow(sheet, r), c);
                cell.setCellStyle(style);
            }
        }
    }

    private static final class Styles {
        final CellStyle title;
        final CellStyle header;
        final CellStyle label;
        final CellStyle body;

        Styles(Workbook wb) {
            Font song = wb.createFont();
            song.setFontName("宋体");
            song.setFontHeightInPoints((short) 11);

            Font titleFont = wb.createFont();
            titleFont.setFontName("宋体");
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);

            Font bold = wb.createFont();
            bold.setFontName("宋体");
            bold.setBold(true);
            bold.setFontHeightInPoints((short) 11);

            title = wb.createCellStyle();
            title.setFont(titleFont);
            title.setAlignment(HorizontalAlignment.CENTER);
            title.setVerticalAlignment(VerticalAlignment.CENTER);
            applyThinBorders(title);

            header = wb.createCellStyle();
            header.setFont(bold);
            header.setAlignment(HorizontalAlignment.LEFT);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            applyThinBorders(header);

            label = wb.createCellStyle();
            label.setFont(song);
            label.setWrapText(true);
            label.setAlignment(HorizontalAlignment.LEFT);
            label.setVerticalAlignment(VerticalAlignment.TOP);
            applyThinBorders(label);

            body = wb.createCellStyle();
            body.setFont(song);
            body.setWrapText(true);
            body.setAlignment(HorizontalAlignment.LEFT);
            body.setVerticalAlignment(VerticalAlignment.TOP);
            applyThinBorders(body);
        }

        private static void applyThinBorders(CellStyle style) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        }
    }
}
