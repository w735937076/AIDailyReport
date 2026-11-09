package com.dailyreport.service;

import com.dailyreport.config.AppConfig;
import com.dailyreport.domain.WorkItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
public class ExcelExportService {

    @Autowired
    private AppConfig appConfig;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public String exportDaily(LocalDate date, List<WorkItem> workItems, String tomorrowPlan, String reportContent) throws Exception {
        String templatePath = getTemplatePath(appConfig.getReport().getDailyTemplate());
        String outputPath = getOutputPath("daily_" + date.format(DATE_FORMAT) + ".xlsx");

        Workbook workbook;
        if (Files.exists(Paths.get(templatePath))) {
            workbook = new XSSFWorkbook(new FileInputStream(templatePath));
        } else {
            workbook = createDefaultDailyTemplate();
        }

        Sheet sheet = workbook.getSheetAt(0);

        // 填充日期
        setCellValue(sheet, 0, 1, "日期: " + date.format(DATE_FORMAT));

        // 填充工作项
        int row = 3;
        for (WorkItem item : workItems) {
            setCellValue(sheet, row, 0, getTypeName(item.getType()));
            setCellValue(sheet, row, 1, item.getModule());
            setCellValue(sheet, row, 2, item.getPlan());
            setCellValue(sheet, row, 3, item.getPractice() != null ? item.getPractice() : "");
            row++;
        }

        // 填充明日计划
        row = Math.max(row + 1, workItems.size() + 5);
        setCellValue(sheet, row, 0, "明日计划:");
        setCellValue(sheet, row, 1, tomorrowPlan != null ? tomorrowPlan : "");

        // 尝试解析并填充 AI 生成的内容
        try {
            JsonNode content = objectMapper.readTree(reportContent);
            if (content.has("summary")) {
                setCellValue(sheet, 1, 3, "概况: " + content.get("summary").asText());
            }
            if (content.has("issues")) {
                setCellValue(sheet, 2, 3, "问题: " + content.get("issues").asText());
            }
        } catch (Exception e) {
            log.debug("日报内容解析失败，使用默认格式: {}", e.getMessage());
        }

        saveWorkbook(workbook, outputPath);
        log.info("日报已导出: {}", outputPath);
        return outputPath;
    }

    public String exportWeekly(LocalDate weekStart, LocalDate weekEnd, List<WorkItem> workItems, String content) throws Exception {
        String templatePath = getTemplatePath(appConfig.getReport().getWeeklyTemplate());
        String outputPath = getOutputPath("weekly_" + weekStart.format(DATE_FORMAT) + "_" + weekEnd.format(DATE_FORMAT) + ".xlsx");

        Workbook workbook;
        if (Files.exists(Paths.get(templatePath))) {
            workbook = new XSSFWorkbook(new FileInputStream(templatePath));
        } else {
            workbook = createDefaultWeeklyTemplate();
        }

        Sheet sheet = workbook.getSheetAt(0);

        // 填充周期信息
        setCellValue(sheet, 0, 1, "周期: " + weekStart.format(DATE_FORMAT) + " ~ " + weekEnd.format(DATE_FORMAT));

        // 填充工作项
        int row = 3;
        setCellValue(sheet, row, 0, "类型");
        setCellValue(sheet, row, 1, "模块");
        setCellValue(sheet, row, 2, "描述");
        setCellValue(sheet, row, 3, "价值");
        row++;

        for (WorkItem item : workItems) {
            setCellValue(sheet, row, 0, getTypeName(item.getType()));
            setCellValue(sheet, row, 1, item.getModule());
            setCellValue(sheet, row, 2, item.getPlan());
            setCellValue(sheet, row, 3, item.getPractice() != null ? item.getPractice() : "");
            row++;
        }

        // 填充汇总内容
        row = Math.max(row + 1, workItems.size() + 6);
        setCellValue(sheet, row, 0, "周报汇总:");
        setCellValue(sheet, row, 1, content);

        saveWorkbook(workbook, outputPath);
        log.info("周报已导出: {}", outputPath);
        return outputPath;
    }

    public String exportMonthly(LocalDate monthStart, LocalDate monthEnd, List<WorkItem> workItems, String content) throws Exception {
        String templatePath = getTemplatePath(appConfig.getReport().getMonthlyTemplate());
        String monthStr = monthStart.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String outputPath = getOutputPath("monthly_" + monthStr + ".xlsx");

        Workbook workbook;
        if (Files.exists(Paths.get(templatePath))) {
            workbook = new XSSFWorkbook(new FileInputStream(templatePath));
        } else {
            workbook = createDefaultMonthlyTemplate();
        }

        Sheet sheet = workbook.getSheetAt(0);

        // 填充月份信息
        setCellValue(sheet, 0, 1, "月份: " + monthStr);

        // 填充工作项
        int row = 3;
        setCellValue(sheet, row, 0, "类型");
        setCellValue(sheet, row, 1, "模块");
        setCellValue(sheet, row, 2, "描述");
        setCellValue(sheet, row, 3, "价值");
        row++;

        for (WorkItem item : workItems) {
            setCellValue(sheet, row, 0, getTypeName(item.getType()));
            setCellValue(sheet, row, 1, item.getModule());
            setCellValue(sheet, row, 2, item.getPlan());
            setCellValue(sheet, row, 3, item.getPractice() != null ? item.getPractice() : "");
            row++;
        }

        // 填充汇总内容
        row = Math.max(row + 1, workItems.size() + 6);
        setCellValue(sheet, row, 0, "月报汇总:");
        setCellValue(sheet, row, 1, content);

        saveWorkbook(workbook, outputPath);
        log.info("月报已导出: {}", outputPath);
        return outputPath;
    }

    private Workbook createDefaultDailyTemplate() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("日报");

        // 创建样式
        CellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        // 表头
        Row headerRow = sheet.createRow(0);
        Cell cell = headerRow.createCell(0);
        cell.setCellValue("日报");
        cell.setCellStyle(titleStyle);

        headerRow = sheet.createRow(1);
        headerRow.createCell(0).setCellValue("日期:");
        headerRow.createCell(3).setCellValue("概况:");

        headerRow = sheet.createRow(2);
        headerRow.createCell(0).setCellValue("类型");
        headerRow.createCell(1).setCellValue("模块");
        headerRow.createCell(2).setCellValue("描述");
        headerRow.createCell(3).setCellValue("价值/影响");

        // 设置列宽
        sheet.setColumnWidth(0, 4000);
        sheet.setColumnWidth(1, 4000);
        sheet.setColumnWidth(2, 10000);
        sheet.setColumnWidth(3, 6000);

        return workbook;
    }

    private Workbook createDefaultWeeklyTemplate() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("周报");

        CellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);

        Row headerRow = sheet.createRow(0);
        Cell cell = headerRow.createCell(0);
        cell.setCellValue("周报");
        cell.setCellStyle(titleStyle);

        headerRow = sheet.createRow(1);
        headerRow.createCell(0).setCellValue("周期:");

        headerRow = sheet.createRow(2);
        headerRow.createCell(0).setCellValue("类型");
        headerRow.createCell(1).setCellValue("模块");
        headerRow.createCell(2).setCellValue("描述");
        headerRow.createCell(3).setCellValue("价值/影响");

        sheet.setColumnWidth(0, 4000);
        sheet.setColumnWidth(1, 4000);
        sheet.setColumnWidth(2, 10000);
        sheet.setColumnWidth(3, 6000);

        return workbook;
    }

    private Workbook createDefaultMonthlyTemplate() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("月报");

        CellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);

        Row headerRow = sheet.createRow(0);
        Cell cell = headerRow.createCell(0);
        cell.setCellValue("月报");
        cell.setCellStyle(titleStyle);

        headerRow = sheet.createRow(1);
        headerRow.createCell(0).setCellValue("月份:");

        headerRow = sheet.createRow(2);
        headerRow.createCell(0).setCellValue("类型");
        headerRow.createCell(1).setCellValue("模块");
        headerRow.createCell(2).setCellValue("描述");
        headerRow.createCell(3).setCellValue("价值/影响");

        sheet.setColumnWidth(0, 4000);
        sheet.setColumnWidth(1, 4000);
        sheet.setColumnWidth(2, 10000);
        sheet.setColumnWidth(3, 6000);

        return workbook;
    }

    private void setCellValue(Sheet sheet, int row, int col, String value) {
        Row rowObj = sheet.getRow(row);
        if (rowObj == null) {
            rowObj = sheet.createRow(row);
        }
        Cell cell = rowObj.getCell(col);
        if (cell == null) {
            cell = rowObj.createCell(col);
        }
        cell.setCellValue(value);
    }

    private String getTemplatePath(String templateName) {
        return appConfig.getReport().getTemplateDir() + "/" + templateName;
    }

    private String getOutputPath(String fileName) {
        String outputDir = appConfig.getReport().getOutputDir();
        return outputDir + "/" + fileName;
    }

    private void saveWorkbook(Workbook workbook, String path) throws IOException {
        Path outputPath = Paths.get(path);
        Files.createDirectories(outputPath.getParent());

        try (FileOutputStream fos = new FileOutputStream(path)) {
            workbook.write(fos);
        }
        workbook.close();
    }

    private String getTypeName(String type) {
        if (type == null) return "其他";
        return switch (type) {
            case "FEATURE" -> "新功能";
            case "BUG_FIX" -> "Bug修复";
            case "REFACTOR" -> "重构";
            case "DOCS" -> "文档";
            default -> "其他";
        };
    }
}
