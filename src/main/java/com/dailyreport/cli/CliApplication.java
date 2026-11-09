package com.dailyreport.cli;

import com.dailyreport.config.AppConfig;
import com.dailyreport.domain.DailyReport;
import com.dailyreport.domain.GitCommitLog;
import com.dailyreport.domain.PeriodReport;
import com.dailyreport.domain.WorkItem;
import com.dailyreport.mapper.DailyReportMapper;
import com.dailyreport.mapper.PeriodReportMapper;
import com.dailyreport.mapper.WorkItemMapper;
import com.dailyreport.service.AiParseService;
import com.dailyreport.service.AiChatClient;
import com.dailyreport.service.GitCollectService;
import com.dailyreport.service.ReportGenerateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Scanner;

@Slf4j
@Component
public class CliApplication implements CommandLineRunner {

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private GitCollectService gitCollectService;

    @Autowired
    private AiParseService aiParseService;

    @Autowired
    private ReportGenerateService reportGenerateService;

    @Autowired
    private WorkItemMapper workItemMapper;

    @Autowired
    private DailyReportMapper dailyReportMapper;

    @Autowired
    private PeriodReportMapper periodReportMapper;

    private final Scanner scanner = new Scanner(System.in);
    private final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public void run(String... args) {
        // 检查配置
        if (!checkConfiguration()) {
            return;
        }

        // 显示菜单
        showMenu();
    }

    private boolean checkConfiguration() {
        AppConfig.AiConfig aiConfig = appConfig.getAi();
        if (!AiChatClient.hasKey(aiConfig)) {
            System.out.println("错误: 请先配置主模型 API Key");
            System.out.println("编辑 config.yaml 文件，填入您的 API Key");
            return false;
        }

        if (appConfig.getGit().getRepos() == null || appConfig.getGit().getRepos().isEmpty()) {
            System.out.println("警告: 未配置 Git 仓库");
            System.out.println("编辑 config.yaml 文件，配置您的 Git 仓库路径");
        }

        return true;
    }

    private void showMenu() {
        while (true) {
            System.out.println("\n===== DailyReport CLI =====");
            System.out.println("1. [配置] 查看当前配置");
            System.out.println("2. [采集] 拉取 Git 提交");
            System.out.println("3. [日报] 生成今日日报");
            System.out.println("4. [周报] 生成本周周报");
            System.out.println("5. [月报] 生成本月月报");
            System.out.println("6. [技术文档] 生成技术文档");
            System.out.println("7. [查询] 查看历史报告");
            System.out.println("8. [导出] 导出指定日期报告");
            System.out.println("9. [退出] 退出程序");
            System.out.print("\n请输入选项: ");

            String choice = scanner.nextLine().trim();

            try {
                switch (choice) {
                    case "1" -> showConfig();
                    case "2" -> collectGit();
                    case "3" -> generateDaily();
                    case "4" -> generateWeekly();
                    case "5" -> generateMonthly();
                    case "6" -> generateTechDoc();
                    case "7" -> queryReports();
                    case "8" -> exportReport();
                    case "9" -> {
                        System.out.println("再见!");
                        return;
                    }
                    default -> System.out.println("无效选项，请重新输入");
                }
            } catch (Exception e) {
                System.out.println("操作失败: " + e.getMessage());
                log.error("CLI 操作失败", e);
            }
        }
    }

    private void showConfig() {
        System.out.println("\n=== 当前配置 ===");
        System.out.println("AI 模型: " + appConfig.getAi().getModel());
        System.out.println("API URL: " + appConfig.getAi().getBaseUrl());
        AppConfig.FallbackConfig fallback = appConfig.getAi().getFallback();
        if (fallback != null && fallback.isEnabled()) {
            System.out.println("备用模型: " + fallback.getModel() + "（"
                    + (AiChatClient.hasKey(fallback) ? "已配置" : "未配置备用模型密钥") + "）");
        }

        if (appConfig.getGit().getRepos() != null) {
            System.out.println("\nGit 仓库配置:");
            for (AppConfig.RepoConfig repo : appConfig.getGit().getRepos()) {
                System.out.println("  - " + repo.getName() + ": " +
                        (repo.getPath() != null ? repo.getPath() : repo.getRemote()));
            }
        }

        System.out.println("\n输出目录: " + appConfig.getReport().getOutputDir());
        System.out.println("定时任务: " + (appConfig.getScheduler().isEnabled() ? "启用" : "禁用"));
        if (appConfig.getScheduler().isEnabled()) {
            System.out.println("Cron: " + appConfig.getScheduler().getCron());
        }
    }

    private void collectGit() {
        System.out.println("\n=== Git 采集 ===");

        List<AppConfig.RepoConfig> repos = appConfig.getGit().getRepos();
        if (repos == null || repos.isEmpty()) {
            System.out.println("未配置 Git 仓库，请先编辑 config.yaml");
            return;
        }

        int totalCollected = 0;
        int totalParsed = 0;

        for (AppConfig.RepoConfig repo : repos) {
            System.out.println("\n采集仓库: " + repo.getName() + "...");

            // 采集
            int collected = gitCollectService.collectFromRepo(repo);
            System.out.println("  新增 commit: " + collected);
            totalCollected += collected;

            // 解析未解析的 commit
            List<GitCommitLog> unparsed = gitCollectService.getUnparsedCommits();
            System.out.println("  待解析 commit: " + unparsed.size());

            if (!unparsed.isEmpty()) {
                int parsed = aiParseService.parseUnparsedCommits(unparsed);
                System.out.println("  解析成功: " + parsed);
                totalParsed += parsed;

                // 标记为已解析
                for (GitCommitLog commit : unparsed) {
                    gitCollectService.markAsParsed(commit.getId());
                }
            }
        }

        System.out.println("\n采集完成! 总计新增: " + totalCollected + " 条 commit, 解析: " + totalParsed + " 条 work_item");
    }

    private void generateDaily() {
        System.out.println("\n=== 生成日报 ===");
        LocalDate today = LocalDate.now();

        // 检查今日工作项
        List<WorkItem> workItems = workItemMapper.findByDateOrdered(today);
        if (workItems.isEmpty()) {
            System.out.println("\n今日没有检测到代码类工作项，可继续录入非代码类工作生成日报。");
        }

        // 显示今日工作项
        System.out.println("\n检测到今日工作项:");
        for (int i = 0; i < workItems.size(); i++) {
            WorkItem item = workItems.get(i);
            System.out.println((i + 1) + ". [" + getTypeName(item.getType()) + "] " +
                    item.getModule() + " - " + item.getPlan());
        }

        // 输入今日非代码类工作
        System.out.println("\n请输入【今日非代码类工作】(每行一条，输入空行结束；没有可直接回车):");
        String nonCodeWork = readMultilineInput();

        if (workItems.isEmpty() && nonCodeWork.isBlank()) {
            System.out.println("今日没有代码类工作项，也未录入非代码类工作，跳过日报生成。");
            return;
        }

        // 输入明日计划
        System.out.println("\n请输入【明日计划】(每行一条，输入空行结束):");
        String tomorrowPlan = readMultilineInput();

        // 生成日报
        System.out.println("\n正在生成日报...");
        try {
            DailyReport report = reportGenerateService.generateDailyReport(today, tomorrowPlan, nonCodeWork);
            if (report != null) {
                System.out.println("✅ 日报已生成: " + report.getExcelPath());
            } else {
                System.out.println("⚠️  未生成日报");
            }
        } catch (Exception e) {
            System.out.println("❌ 日报生成失败: " + e.getMessage());
            log.error("日报生成失败", e);
        }
    }

    private String readMultilineInput() {
        StringBuilder input = new StringBuilder();
        while (true) {
            String line = scanner.nextLine();
            if (line.isEmpty()) {
                break;
            }
            if (input.length() > 0) {
                input.append("\n");
            }
            input.append(line);
        }
        return input.toString();
    }

    private void generateWeekly() {
        System.out.println("\n=== 生成周报 ===");
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        System.out.println("周报周期: " + weekStart.format(DATE_FORMAT) + " ~ " + weekEnd.format(DATE_FORMAT));

        // 检查工作项
        List<WorkItem> workItems = workItemMapper.findByDateRange(weekStart, weekEnd);
        if (workItems.isEmpty()) {
            System.out.println("本周无工作记录");
            return;
        }

        System.out.println("本周工作项数量: " + workItems.size());

        System.out.println("\n正在生成周报...");
        try {
            PeriodReport report = reportGenerateService.generateWeeklyReport(weekStart);
            if (report != null) {
                System.out.println("✅ 周报已生成: " + report.getExcelPath());
            } else {
                System.out.println("⚠️  未生成周报");
            }
        } catch (Exception e) {
            System.out.println("❌ 周报生成失败: " + e.getMessage());
            log.error("周报生成失败", e);
        }
    }

    private void generateMonthly() {
        System.out.println("\n=== 生成月报 ===");
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);

        System.out.println("月报月份: " + monthStart.format(DateTimeFormatter.ofPattern("yyyy-MM")));

        // 检查工作项
        List<WorkItem> workItems = workItemMapper.findByDateRange(monthStart, today);
        if (workItems.isEmpty()) {
            System.out.println("本月无工作记录");
            return;
        }

        System.out.println("本月工作项数量: " + workItems.size());

        System.out.println("\n正在生成月报...");
        try {
            PeriodReport report = reportGenerateService.generateMonthlyReport(monthStart);
            if (report != null) {
                System.out.println("✅ 月报已生成: " + report.getExcelPath());
            } else {
                System.out.println("⚠️  未生成月报");
            }
        } catch (Exception e) {
            System.out.println("❌ 月报生成失败: " + e.getMessage());
            log.error("月报生成失败", e);
        }
    }

    private void generateTechDoc() {
        System.out.println("\n=== 生成技术文档 ===");
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        System.out.println("文档周期: " + weekStart.format(DATE_FORMAT) + " ~ " + weekEnd.format(DATE_FORMAT));

        // 检查工作项
        List<WorkItem> workItems = workItemMapper.findByDateRange(weekStart, weekEnd);
        if (workItems.isEmpty()) {
            System.out.println("本周无工作记录");
            return;
        }

        System.out.println("本周工作项数量: " + workItems.size());

        System.out.println("\n正在生成技术文档（AI 将自动识别项目名和模块名）...");
        try {
            String filePath = reportGenerateService.generateTechDoc(weekStart, weekEnd);
            if (filePath != null) {
                System.out.println("✅ 技术文档已生成: " + filePath);
            } else {
                System.out.println("⚠️  未生成技术文档");
            }
        } catch (Exception e) {
            System.out.println("❌ 技术文档生成失败: " + e.getMessage());
            log.error("技术文档生成失败", e);
        }
    }

    private void queryReports() {
        System.out.println("\n=== 查看历史报告 ===");
        System.out.println("1. 日报");
        System.out.println("2. 周报");
        System.out.println("3. 月报");
        System.out.print("请选择类型: ");

        String typeChoice = scanner.nextLine().trim();
        String type = switch (typeChoice) {
            case "1" -> "DAILY";
            case "2" -> "WEEK";
            case "3" -> "MONTH";
            default -> {
                System.out.println("无效选项");
                yield null;
            }
        };

        if (type == null) return;

        if ("DAILY".equals(type)) {
            List<DailyReport> reports = dailyReportMapper.findAll();
            System.out.println("\n日报列表:");
            for (DailyReport report : reports) {
                System.out.println("  " + report.getReportDate() + " - " +
                        ("EXPORTED".equals(report.getStatus()) ? "已导出" : report.getStatus()));
            }
        } else {
            List<PeriodReport> reports = periodReportMapper.findByType(type);
            System.out.println("\n" + ("WEEK".equals(type) ? "周报" : "月报") + "列表:");
            for (PeriodReport report : reports) {
                System.out.println("  " + report.getStartDate() + " ~ " + report.getEndDate() +
                        " - " + ("EXPORTED".equals(report.getStatus()) ? "已导出" : report.getStatus()));
            }
        }
    }

    private void exportReport() {
        System.out.println("\n=== 导出报告 ===");
        System.out.print("请输入日期 (yyyy-MM-dd): ");
        String dateStr = scanner.nextLine().trim();

        try {
            LocalDate date = LocalDate.parse(dateStr, DATE_FORMAT);
            DailyReport report = dailyReportMapper.findByDate(date);

            if (report != null && report.getExcelPath() != null) {
                System.out.println("Excel 路径: " + report.getExcelPath());
            } else {
                System.out.println("该日期没有可导出的报告");
            }
        } catch (Exception e) {
            System.out.println("日期格式错误，请使用 yyyy-MM-dd 格式");
        }
    }

    // 定时任务 - 每天 18:00 自动生成日报
    @Scheduled(cron = "${scheduler.cron:0 0 18 * * ?}")
    public void scheduledDailyReport() {
        if (!appConfig.getScheduler().isEnabled()) {
            return;
        }

        log.info("定时任务触发，开始生成日报");
        System.out.println("\n[定时任务] 开始生成日报...");

        try {
            LocalDate today = LocalDate.now();
            List<WorkItem> workItems = workItemMapper.findByDateOrdered(today);

            if (workItems.isEmpty()) {
                log.info("今日无工作记录，跳过日报生成");
                return;
            }

            // 定时任务使用默认的明日计划
            String tomorrowPlan = "待填写";
            DailyReport report = reportGenerateService.generateDailyReport(today, tomorrowPlan);

            if (report != null) {
                log.info("日报生成成功: {}", report.getExcelPath());
                System.out.println("[定时任务] ✅ 日报已生成: " + report.getExcelPath());
            }
        } catch (Exception e) {
            log.error("定时日报生成失败", e);
        }
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
