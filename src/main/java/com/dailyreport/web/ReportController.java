package com.dailyreport.web;

import com.dailyreport.domain.DailyReport;
import com.dailyreport.domain.PeriodReport;
import com.dailyreport.domain.WorkItem;
import com.dailyreport.mapper.WorkItemMapper;
import com.dailyreport.service.ExcelExportService;
import com.dailyreport.service.ReportGenerateAsyncService;
import com.dailyreport.service.ReportGenerateService;
import com.dailyreport.web.dto.ApiResponse;
import com.dailyreport.web.dto.GenerateReportRequest;
import com.dailyreport.web.dto.PageResult;
import com.dailyreport.web.dto.StartJobResponse;
import com.dailyreport.web.dto.UpdateDailyReportRequest;
import com.dailyreport.web.dto.UpdatePeriodReportRequest;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportGenerateService reportGenerateService;
    private final WorkItemMapper workItemMapper;
    private final ExcelExportService excelExportService;
    private final ReportGenerateAsyncService reportGenerateAsyncService;
    private final AtomicReference<String> generateStatus = new AtomicReference<>("IDLE");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public ReportController(ReportGenerateService reportGenerateService,
                            WorkItemMapper workItemMapper,
                            ExcelExportService excelExportService,
                            ReportGenerateAsyncService reportGenerateAsyncService) {
        this.reportGenerateService = reportGenerateService;
        this.workItemMapper = workItemMapper;
        this.excelExportService = excelExportService;
        this.reportGenerateAsyncService = reportGenerateAsyncService;
    }

    @GetMapping("/daily")
    public ApiResponse<PageResult<DailyReport>> listDaily(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(reportGenerateService.getDailyReportsPage(page, size));
    }

    @GetMapping("/daily/id/{id}")
    public ApiResponse<DailyReport> getDailyById(@PathVariable Long id) {
        DailyReport report = reportGenerateService.getDailyReportById(id);
        if (report == null) {
            return ApiResponse.fail("日报不存在");
        }
        return ApiResponse.ok(report);
    }

    @GetMapping("/daily/{date}")
    public ApiResponse<DailyReport> getDaily(@PathVariable String date) {
        DailyReport report = reportGenerateService.getDailyReport(LocalDate.parse(date, DATE_FORMAT));
        if (report == null) {
            return ApiResponse.fail("日报不存在");
        }
        return ApiResponse.ok(report);
    }

    @DeleteMapping("/daily/{id}")
    public ApiResponse<Void> deleteDaily(@PathVariable Long id) {
        try {
            boolean deleted = reportGenerateService.deleteDailyReport(id);
            if (!deleted) {
                return ApiResponse.fail("日报不存在");
            }
            return ApiResponse.ok(null);
        } catch (Exception e) {
            return ApiResponse.fail("删除失败: " + e.getMessage());
        }
    }

    @PutMapping("/daily/{id}")
    public ApiResponse<DailyReport> updateDaily(@PathVariable Long id, @RequestBody UpdateDailyReportRequest req) {
        if (req == null) {
            return ApiResponse.fail("请求体不能为空");
        }
        DailyReport updated = reportGenerateService.updateDailyReportText(id, req.getContent(), req.getTomorrowPlan());
        if (updated == null) {
            return ApiResponse.fail("日报不存在");
        }
        return ApiResponse.ok(updated);
    }

    @GetMapping("/period/id/{id}")
    public ApiResponse<PeriodReport> getPeriodById(@PathVariable Long id) {
        PeriodReport report = reportGenerateService.getPeriodReportById(id);
        if (report == null) {
            return ApiResponse.fail("报告不存在");
        }
        return ApiResponse.ok(report);
    }

    @DeleteMapping("/period/{id}")
    public ApiResponse<Void> deletePeriod(@PathVariable Long id) {
        try {
            boolean deleted = reportGenerateService.deletePeriodReport(id);
            if (!deleted) {
                return ApiResponse.fail("报告不存在");
            }
            return ApiResponse.ok(null);
        } catch (Exception e) {
            return ApiResponse.fail("删除失败: " + e.getMessage());
        }
    }

    @PutMapping("/period/{id}")
    public ApiResponse<PeriodReport> updatePeriod(@PathVariable Long id, @RequestBody UpdatePeriodReportRequest req) {
        if (req == null) {
            return ApiResponse.fail("请求体不能为空");
        }
        PeriodReport updated = reportGenerateService.updatePeriodReportText(id, req.getContent());
        if (updated == null) {
            return ApiResponse.fail("报告不存在");
        }
        return ApiResponse.ok(updated);
    }

    @GetMapping("/period/{type}")
    public ApiResponse<PageResult<PeriodReport>> listPeriod(
            @PathVariable String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(reportGenerateService.getPeriodReportsPage(type, page, size));
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, String>> status() {
        return ApiResponse.ok(Map.of("status", generateStatus.get()));
    }

    @PostMapping("/daily/generate")
    public ApiResponse<StartJobResponse> generateDaily(@RequestBody(required = false) GenerateReportRequest req) {
        generateStatus.set("RUNNING");
        String jobId = reportGenerateAsyncService.startDaily(req);
        return ApiResponse.ok(new StartJobResponse(jobId));
    }

    @PostMapping("/weekly/generate")
    public ApiResponse<StartJobResponse> generateWeekly(@RequestBody(required = false) GenerateReportRequest req) {
        generateStatus.set("RUNNING");
        String jobId = reportGenerateAsyncService.startWeekly(req);
        return ApiResponse.ok(new StartJobResponse(jobId));
    }

    @PostMapping("/monthly/generate")
    public ApiResponse<StartJobResponse> generateMonthly(@RequestBody(required = false) GenerateReportRequest req) {
        generateStatus.set("RUNNING");
        String jobId = reportGenerateAsyncService.startMonthly(req);
        return ApiResponse.ok(new StartJobResponse(jobId));
    }

    @PostMapping("/daily/{date}/export-excel")
    public ApiResponse<StartJobResponse> exportDailyExcel(@PathVariable String date) {
        String jobId = reportGenerateAsyncService.startDailyExcelExport(date);
        return ApiResponse.ok(new StartJobResponse(jobId));
    }

    @PostMapping("/weekly/export-excel")
    public ApiResponse<Map<String, String>> exportWeeklyExcel(@RequestBody(required = false) GenerateReportRequest req) throws Exception {
        LocalDate start = req != null && req.getDate() != null
                ? LocalDate.parse(req.getDate(), DATE_FORMAT)
                : LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(6);
        List<WorkItem> items = workItemMapper.findByDateRange(start, end);
        PeriodReport report = reportGenerateService.getPeriodReports("WEEK").stream()
                .filter(r -> r.getStartDate().equals(start)).findFirst().orElse(null);
        String path = excelExportService.exportWeekly(start, end, items,
                report != null ? report.getContent() : "");
        return ApiResponse.ok(Map.of("path", path));
    }
}
