package com.dailyreport.service;

import com.dailyreport.web.dto.GeneratePerformanceRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

@Service
public class PerformanceGenerateAsyncService {

    private final JobService jobService;
    private final PerformanceGenerateService performanceGenerateService;
    private final Executor executor;

    public PerformanceGenerateAsyncService(JobService jobService,
                                           PerformanceGenerateService performanceGenerateService,
                                           @Qualifier("appTaskExecutor") Executor executor) {
        this.jobService = jobService;
        this.performanceGenerateService = performanceGenerateService;
        this.executor = executor;
    }

    public String startGenerate(GeneratePerformanceRequest req) {
        String jobId = jobService.create("PERFORMANCE_GENERATE");
        executor.execute(() -> {
            try {
                jobService.start(jobId, "正在准备绩效评估…");
                String ym = req != null && req.getYearMonth() != null ? req.getYearMonth().trim() : null;
                if (ym == null || ym.isBlank()) {
                    throw new IllegalArgumentException("请选择月份 (yyyy-MM)");
                }
                boolean replace = req.getReplaceExisting() != null && req.getReplaceExisting();
                jobService.stage(jobId, "LOAD", 20, "正在汇总提交与工作项…");
                jobService.stage(jobId, "AI", 55, "正在 AI 撰写绩效长文…");
                var review = performanceGenerateService.generate(ym, replace);
                jobService.stage(jobId, "EXPORT", 88, "正在写入 Excel…");
                Map<String, Object> result = new HashMap<>();
                result.put("id", review.getId());
                result.put("reviewMonth", review.getReviewMonth());
                result.put("excelPath", review.getExcelPath());
                jobService.complete(jobId, "绩效评估生成完成", result);
            } catch (Exception e) {
                jobService.fail(jobId, AiUserMessages.from(e));
            }
        });
        return jobId;
    }
}
