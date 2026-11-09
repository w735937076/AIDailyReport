package com.dailyreport.service;

import com.dailyreport.config.AppConfig;
import com.dailyreport.domain.DailyReport;
import com.dailyreport.domain.PeriodReport;
import com.dailyreport.domain.WorkItem;
import com.dailyreport.mapper.DailyReportMapper;
import com.dailyreport.mapper.PeriodReportMapper;
import com.dailyreport.mapper.WorkItemMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ReportGenerateService {

    @Autowired
    private WorkItemMapper workItemMapper;

    @Autowired
    private DailyReportMapper dailyReportMapper;

    @Autowired
    private PeriodReportMapper periodReportMapper;

    @Autowired
    private AiParseService aiParseService;

    @Autowired
    private AppConfig appConfig;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public DailyReport generateDailyReport(LocalDate date, String tomorrowPlan) throws Exception {
        return generateDailyReport(date, tomorrowPlan, "");
    }

    public DailyReport generateDailyReport(LocalDate date, String tomorrowPlan, String nonCodeWork) throws Exception {
        List<WorkItem> workItems = workItemMapper.findByDateOrdered(date);
        String normalizedNonCodeWork = nonCodeWork == null ? "" : nonCodeWork.trim();

        if (workItems.isEmpty() && normalizedNonCodeWork.isBlank()) {
            log.info("日期 {} 没有工作记录，跳过生成", date);
            return null;
        }

        // 调用 AI 生成日报内容 (JSON格式)
        String aiContent = aiParseService.generateDailyReport(workItems, tomorrowPlan, normalizedNonCodeWork);

        // 解析 AI 返回的 JSON
        Map<String, Object> reportData = objectMapper.readValue(aiContent, Map.class);

        // 转换为原始数据 JSON
        Map<String, Object> rawData = new LinkedHashMap<>();
        rawData.put("codeWorkItems", workItems);
        rawData.put("nonCodeWork", normalizedNonCodeWork);
        rawData.put("tomorrowPlan", tomorrowPlan);
        String rawDataJson = objectMapper.writeValueAsString(rawData);

        // 生成 TXT 格式的日报
        String txtContent = formatDailyReportTxt(date, workItems, tomorrowPlan, normalizedNonCodeWork, reportData);

        // 检查是否已存在日报
        DailyReport existing = dailyReportMapper.findByDate(date);
        DailyReport report = new DailyReport();
        report.setReportDate(date);
        report.setContent(txtContent);
        report.setTomorrowPlan(tomorrowPlan);
        report.setRawDataJson(rawDataJson);
        report.setStatus("GENERATED");

        if (existing != null) {
            report.setId(existing.getId());
            dailyReportMapper.update(report);
        } else {
            dailyReportMapper.insert(report);
        }

        // 导出 TXT
        String txtPath = exportDailyTxt(date, txtContent);
        report.setExcelPath(txtPath);
        report.setStatus("EXPORTED");
        dailyReportMapper.update(report);

        return report;
    }

    private String formatDailyReportTxt(LocalDate date, List<WorkItem> workItems,
                                       String tomorrowPlan, String nonCodeWork, Map<String, Object> reportData) {
        StringBuilder sb = new StringBuilder();
        String dateStr = date.format(DATE_FORMAT);

        // 标题
        sb.append("日报 - ").append(dateStr).append("\n");
        sb.append("=".repeat(50)).append("\n\n");

        // 今日工作
        sb.append("今日工作\n");
        sb.append("-".repeat(30)).append("\n");

        // 第一部分：工作计划
        sb.append("【工作计划】\n");
        List<String> workPlans = (List<String>) reportData.getOrDefault("work_plans", new ArrayList<>());
        for (int i = 0; i < workPlans.size(); i++) {
            sb.append(i + 1).append("、").append(workPlans.get(i)).append("\n");
        }
        sb.append("\n");

        // 第二部分：具体做法
        sb.append("【具体做法】\n");
        List<String> practices = (List<String>) reportData.getOrDefault("practices", new ArrayList<>());
        for (int i = 0; i < practices.size(); i++) {
            sb.append(i + 1).append("、").append(practices.get(i)).append("\n");
        }
        sb.append("\n");

        // 第三部分：预计完成时间
        sb.append("【预计完成时间】\n");
        List<String> estimates = (List<String>) reportData.getOrDefault("estimates", new ArrayList<>());
        for (int i = 0; i < estimates.size(); i++) {
            sb.append(i + 1).append("、").append(estimates.get(i)).append("\n");
        }
        sb.append("\n");

        // 第四部分：完成进度
        sb.append("【完成进度】\n");
        List<String> progress = (List<String>) reportData.getOrDefault("progress", new ArrayList<>());
        for (int i = 0; i < progress.size(); i++) {
            sb.append(i + 1).append("、").append(progress.get(i)).append("\n");
        }
        sb.append("\n");

        // 明日计划 - 同样四个部分
        sb.append("明日计划\n");
        sb.append("-".repeat(30)).append("\n");

        // 明日工作计划
        sb.append("【工作计划】\n");
        List<String> tomorrowPlans = (List<String>) reportData.getOrDefault("tomorrow_plans", new ArrayList<>());
        if (!tomorrowPlans.isEmpty()) {
            for (int i = 0; i < tomorrowPlans.size(); i++) {
                sb.append(i + 1).append("、").append(tomorrowPlans.get(i)).append("\n");
            }
        } else {
            sb.append("暂无\n");
        }
        sb.append("\n");

        // 明日具体做法
        sb.append("【具体做法】\n");
        List<String> tomorrowPractices = (List<String>) reportData.getOrDefault("tomorrow_practices", new ArrayList<>());
        if (!tomorrowPractices.isEmpty()) {
            for (int i = 0; i < tomorrowPractices.size(); i++) {
                sb.append(i + 1).append("、").append(tomorrowPractices.get(i)).append("\n");
            }
        } else {
            sb.append("暂无\n");
        }
        sb.append("\n");

        // 明日预计完成时间
        sb.append("【预计完成时间】\n");
        List<String> tomorrowEstimates = (List<String>) reportData.getOrDefault("tomorrow_estimates", new ArrayList<>());
        if (!tomorrowEstimates.isEmpty()) {
            for (int i = 0; i < tomorrowEstimates.size(); i++) {
                sb.append(i + 1).append("、").append(tomorrowEstimates.get(i)).append("\n");
            }
        } else {
            sb.append("暂无\n");
        }
        sb.append("\n");

        // 明日完成进度
        sb.append("【完成进度】\n");
        List<String> tomorrowProgress = (List<String>) reportData.getOrDefault("tomorrow_progress", new ArrayList<>());
        if (!tomorrowProgress.isEmpty()) {
            for (int i = 0; i < tomorrowProgress.size(); i++) {
                sb.append(i + 1).append("、").append(tomorrowProgress.get(i)).append("\n");
            }
        } else {
            sb.append("暂无\n");
        }

        return sb.toString();
    }

    private String exportDailyTxt(LocalDate date, String content) throws Exception {
        String outputDir = appConfig.getReport().getOutputDir();
        Path outputPath = Paths.get(outputDir);
        Files.createDirectories(outputPath);

        String fileName = "daily_report_" + date.format(DATE_FORMAT) + ".txt";
        String filePath = outputDir + "/" + fileName;

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.print(content);
        }

        log.info("日报已导出: {}", filePath);
        return filePath;
    }

    public PeriodReport generateWeeklyReport(LocalDate weekStart) throws Exception {
        LocalDate weekEnd = weekStart.plusDays(6);
        return generatePeriodReport("WEEK", weekStart, weekEnd);
    }

    public PeriodReport generateMonthlyReport(LocalDate monthStart) throws Exception {
        LocalDate monthEnd = monthStart.with(TemporalAdjusters.lastDayOfMonth());
        return generatePeriodReport("MONTH", monthStart, monthEnd);
    }

    private PeriodReport generatePeriodReport(String type, LocalDate startDate, LocalDate endDate) throws Exception {
        List<WorkItem> workItems = workItemMapper.findByDateRange(startDate, endDate);

        if (workItems.isEmpty()) {
            log.info("周期 {} - {} 没有工作记录，跳过生成", startDate, endDate);
            return null;
        }

        // 去重和合并
        List<WorkItem> deduplicatedItems = deduplicateWorkItems(workItems);

        String content;
        // 周报和月报都使用 AI 生成
        if ("WEEK".equals(type)) {
            content = aiParseService.generateWeeklyReport(deduplicatedItems);
        } else {
            content = aiParseService.generateMonthlyReport(deduplicatedItems);
        }

        String rawDataJson = objectMapper.writeValueAsString(deduplicatedItems);

        // 检查是否已存在
        PeriodReport existing = periodReportMapper.findByTypeAndStartDate(type, startDate);
        PeriodReport report = new PeriodReport();
        report.setType(type);
        report.setStartDate(startDate);
        report.setEndDate(endDate);
        report.setContent(content);
        report.setRawDataJson(rawDataJson);
        report.setStatus("GENERATED");

        if (existing != null) {
            report.setId(existing.getId());
            periodReportMapper.update(report);
        } else {
            periodReportMapper.insert(report);
        }

        // 导出 TXT
        String txtPath;
        if ("WEEK".equals(type)) {
            txtPath = exportPeriodTxt("weekly", startDate, endDate, content);
        } else {
            txtPath = exportPeriodTxt("monthly", startDate, endDate, content);
        }

        report.setExcelPath(txtPath);
        report.setStatus("EXPORTED");
        periodReportMapper.update(report);

        return report;
    }

    private String exportPeriodTxt(String type, LocalDate startDate, LocalDate endDate, String content) throws Exception {
        String outputDir = appConfig.getReport().getOutputDir();
        Path outputPath = Paths.get(outputDir);
        Files.createDirectories(outputPath);

        String fileName = type + "_report_" + startDate.format(DATE_FORMAT) + "_" + endDate.format(DATE_FORMAT) + ".txt";
        String filePath = outputDir + "/" + fileName;

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.print(content);
        }

        log.info("{}报已导出: {}", "WEEK".equals(type) ? "周" : "月", filePath);
        return filePath;
    }

    private List<WorkItem> deduplicateWorkItems(List<WorkItem> items) {
        // 按 type + module + plan 分组，合并相似项
        Map<String, WorkItem> grouped = new LinkedHashMap<>();

        for (WorkItem item : items) {
            String key = item.getType() + "|" + item.getModule() + "|" + item.getPlan();
            if (grouped.containsKey(key)) {
                // 保留更详细的
                WorkItem existing = grouped.get(key);
                if (item.getPlan() != null && existing.getPlan() != null
                        && item.getPlan().length() > existing.getPlan().length()) {
                    grouped.put(key, item);
                }
            } else {
                grouped.put(key, item);
            }
        }

        return new ArrayList<>(grouped.values());
    }

    private String generatePeriodContent(String type, List<WorkItem> items) throws Exception {
        // 按类型分组
        Map<String, List<WorkItem>> byType = items.stream()
                .collect(Collectors.groupingBy(WorkItem::getType));

        StringBuilder sb = new StringBuilder();
        if ("WEEK".equals(type)) {
            sb.append("本周完成：\n");
        } else {
            sb.append("本月完成：\n");
        }

        for (Map.Entry<String, List<WorkItem>> entry : byType.entrySet()) {
            String typeName = getTypeName(entry.getKey());
            sb.append("- ").append(typeName).append("：\n");
            for (WorkItem item : entry.getValue()) {
                sb.append("  - ").append(item.getModule()).append("：")
                        .append(item.getPlan());
                if (item.getPractice() != null && !item.getPractice().isEmpty()) {
                    sb.append(" (").append(item.getPractice()).append(")");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private String getTypeName(String type) {
        return switch (type) {
            case "FEATURE" -> "功能开发";
            case "BUG_FIX" -> "Bug修复";
            case "REFACTOR" -> "重构优化";
            case "DOCS" -> "文档更新";
            default -> "其他";
        };
    }

    public List<DailyReport> getDailyReports(LocalDate startDate, LocalDate endDate) {
        return dailyReportMapper.findByDateRange(startDate, endDate);
    }

    public DailyReport getDailyReport(LocalDate date) {
        return dailyReportMapper.findByDate(date);
    }

    public List<PeriodReport> getPeriodReports(String type) {
        return periodReportMapper.findByType(type);
    }

    public String generateTechDoc(LocalDate startDate, LocalDate endDate) throws Exception {
        List<WorkItem> workItems = workItemMapper.findByDateRange(startDate, endDate);

        if (workItems.isEmpty()) {
            log.info("周期 {} - {} 没有工作记录，无法生成技术文档", startDate, endDate);
            return null;
        }

        // 调用 AI 生成技术文档（包含项目名和模块名）
        String content = aiParseService.generateTechDoc(workItems);

        // 从内容中提取项目名和模块名
        String[] names = extractProjectAndModule(content);
        String projectName = names[0];
        String moduleName = names[1];

        // 生成文件名: 项目名-功能名-技术文档-汪志立-【yyyy-MM-dd】.md
        String fileName = projectName + "-" + moduleName + "-技术文档-汪志立-【" + DATE_FORMAT.format(LocalDate.now()) + "】.md";

        // 导出 MD 文件
        String outputDir = appConfig.getReport().getOutputDir();
        Path outputPath = Paths.get(outputDir);
        Files.createDirectories(outputPath);
        String filePath = outputDir + "/" + fileName;

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.print(content);
        }

        log.info("技术文档已生成: {}", filePath);
        return filePath;
    }

    private String[] extractProjectAndModule(String content) {
        String projectName = "未命名项目";
        String moduleName = "未命名模块";

        // 从文档内容中提取项目名和模块名
        // 处理 | 项目名称   | xxx | 或 | 项目名称   | xxx | 格式
        java.util.regex.Pattern projectPattern = java.util.regex.Pattern.compile("\\|\\s*项目名称\\s*\\|\\s*([^|\\n]+?)\\s*(?:\\|\\s*$|$)", java.util.regex.Pattern.MULTILINE);
        java.util.regex.Pattern modulePattern = java.util.regex.Pattern.compile("\\|\\s*模块名称\\s*\\|\\s*([^|\\n]+?)\\s*(?:\\|\\s*$|$)", java.util.regex.Pattern.MULTILINE);

        java.util.regex.Matcher projectMatcher = projectPattern.matcher(content);
        if (projectMatcher.find()) {
            projectName = projectMatcher.group(1).trim();
        }

        java.util.regex.Matcher moduleMatcher = modulePattern.matcher(content);
        if (moduleMatcher.find()) {
            moduleName = moduleMatcher.group(1).trim();
        }

        // 清理文件名中的非法字符
        projectName = projectName.replaceAll("[\\\\/:*?\"<>|]", "").trim();
        moduleName = moduleName.replaceAll("[\\\\/:*?\"<>|]", "").trim();

        // 如果为空，设置默认值
        if (projectName.isEmpty()) projectName = "未命名项目";
        if (moduleName.isEmpty()) moduleName = "未命名模块";

        return new String[]{projectName, moduleName};
    }
}
