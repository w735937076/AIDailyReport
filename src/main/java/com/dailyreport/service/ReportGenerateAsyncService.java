package com.dailyreport.service;

import com.dailyreport.domain.DailyReport;
import com.dailyreport.domain.PeriodReport;
import com.dailyreport.domain.WorkItem;
import com.dailyreport.mapper.WorkItemMapper;
import com.dailyreport.web.dto.GenerateReportRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.dailyreport.util.WeekRangeUtil;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Service
public class ReportGenerateAsyncService {

    private final JobService jobService;
    private final ReportGenerateService reportGenerateService;
    private final ExcelExportService excelExportService;
    private final WorkItemMapper workItemMapper;
    private final Executor executor;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public ReportGenerateAsyncService(JobService jobService,
                                      ReportGenerateService reportGenerateService,
                                      ExcelExportService excelExportService,
                                      WorkItemMapper workItemMapper,
                                      @Qualifier("appTaskExecutor") Executor executor) {
        this.jobService = jobService;
        this.reportGenerateService = reportGenerateService;
        this.excelExportService = excelExportService;
        this.workItemMapper = workItemMapper;
        this.executor = executor;
    }

    public String startDaily(GenerateReportRequest req) {
        return enqueue("REPORT_DAILY", jobId -> runDaily(jobId, req));
    }

    public String startWeekly(GenerateReportRequest req) {
        return enqueue("REPORT_WEEKLY", jobId -> runWeekly(jobId, req));
    }

    public String startMonthly(GenerateReportRequest req) {
        return enqueue("REPORT_MONTHLY", jobId -> runMonthly(jobId, req));
    }

    public String startDailyExcelExport(String dateStr) {
        return enqueue("EXPORT_DAILY_EXCEL", jobId -> runDailyExcel(jobId, dateStr));
    }

    private String enqueue(String type, JobTask task) {
        String jobId = jobService.create(type);
        executor.execute(() -> {
            try {
                jobService.start(jobId, "正在准备…");
                task.run(jobId);
            } catch (Exception e) {
                jobService.fail(jobId, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        });
        return jobId;
    }

    private void runDaily(String jobId, GenerateReportRequest req) throws Exception {
        LocalDate date = req != null && req.getDate() != null
                ? LocalDate.parse(req.getDate(), DATE_FORMAT) : LocalDate.now();
        String tomorrow = req != null && req.getTomorrowPlan() != null ? req.getTomorrowPlan() : "待填写";
        String nonCode = req != null && req.getNonCodeWork() != null ? req.getNonCodeWork() : "";

        jobService.stage(jobId, "LOAD", 15, "正在加载工作项…");
        List<WorkItem> items = workItemMapper.findByDateOrdered(date);

        jobService.stage(jobId, "AI", 50, "正在调用 AI 生成日报…");
        DailyReport report = reportGenerateService.generateDailyReport(date, tomorrow, nonCode);

        jobService.stage(jobId, "EXPORT", 88, "正在写入导出文件…");
        Map<String, Object> result = new HashMap<>();
        result.put("reportDate", date.toString());
        result.put("workItemCount", items.size());
        if (report != null) {
            result.put("excelPath", report.getExcelPath());
            result.put("status", report.getStatus());
        }
        jobService.complete(jobId, report != null ? "日报生成完成" : "未生成日报（无工作内容）", result);
    }

    private void runWeekly(String jobId, GenerateReportRequest req) throws Exception {
        LocalDate start = req != null && req.getDate() != null
                ? WeekRangeUtil.mondayOfWeek(LocalDate.parse(req.getDate(), DATE_FORMAT))
                : WeekRangeUtil.currentWeekMondayInShanghai();
        jobService.stage(jobId, "LOAD", 20, "正在汇总本周工作项…");
        jobService.stage(jobId, "AI", 55, "正在调用 AI 生成周报…");
        PeriodReport report = reportGenerateService.generateWeeklyReport(start);
        jobService.stage(jobId, "EXPORT", 90, "正在导出周报…");
        Map<String, Object> result = resultForPeriod(report, start);
        jobService.complete(jobId, report != null ? "周报生成完成" : "本周无工作记录", result);
    }

    private void runMonthly(String jobId, GenerateReportRequest req) throws Exception {
        LocalDate start = req != null && req.getDate() != null
                ? LocalDate.parse(req.getDate(), DATE_FORMAT).withDayOfMonth(1)
                : LocalDate.now().withDayOfMonth(1);
        jobService.stage(jobId, "LOAD", 20, "正在汇总本月工作项…");
        jobService.stage(jobId, "AI", 55, "正在调用 AI 生成月报…");
        PeriodReport report = reportGenerateService.generateMonthlyReport(start);
        jobService.stage(jobId, "EXPORT", 90, "正在导出月报…");
        Map<String, Object> result = resultForPeriod(report, start);
        jobService.complete(jobId, report != null ? "月报生成完成" : "本月无工作记录", result);
    }

    private void runDailyExcel(String jobId, String dateStr) throws Exception {
        LocalDate d = LocalDate.parse(dateStr, DATE_FORMAT);
        jobService.stage(jobId, "LOAD", 25, "正在加载日报数据…");
        DailyReport report = reportGenerateService.getDailyReport(d);
        List<WorkItem> items = workItemMapper.findByDateOrdered(d);
        jobService.stage(jobId, "EXPORT", 75, "正在导出 Excel…");
        String path = excelExportService.exportDaily(d, items,
                report != null ? report.getTomorrowPlan() : "",
                report != null ? report.getRawDataJson() : "");
        jobService.complete(jobId, "Excel 导出完成", Map.of("path", path));
    }

    private static Map<String, Object> resultForPeriod(PeriodReport report, LocalDate start) {
        Map<String, Object> result = new HashMap<>();
        if (report != null) {
            result.put("startDate", report.getStartDate().toString());
            result.put("endDate", report.getEndDate().toString());
            result.put("excelPath", report.getExcelPath());
            result.put("status", report.getStatus());
        } else {
            result.put("startDate", start.toString());
            result.put("endDate", start.plusDays(6).toString());
        }
        return result;
    }

    @FunctionalInterface
    private interface JobTask {
        void run(String jobId) throws Exception;
    }
}
