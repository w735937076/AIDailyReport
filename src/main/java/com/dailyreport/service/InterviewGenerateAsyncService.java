package com.dailyreport.service;

import com.dailyreport.web.dto.GenerateInterviewRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

@Service
public class InterviewGenerateAsyncService {

    private final JobService jobService;
    private final InterviewGenerateService interviewGenerateService;
    private final Executor executor;

    public InterviewGenerateAsyncService(JobService jobService,
                                       InterviewGenerateService interviewGenerateService,
                                       @Qualifier("appTaskExecutor") Executor executor) {
        this.jobService = jobService;
        this.interviewGenerateService = interviewGenerateService;
        this.executor = executor;
    }

    public String startGenerate(GenerateInterviewRequest req) {
        String jobId = jobService.create("INTERVIEW_GENERATE");
        executor.execute(() -> {
            try {
                jobService.start(jobId, "正在准备绩效面谈…");
                String ym = req != null && req.getYearMonth() != null ? req.getYearMonth().trim() : null;
                if (ym == null || ym.isBlank()) {
                    throw new IllegalArgumentException("请选择月份 (yyyy-MM)");
                }
                boolean replace = req != null && req.getReplaceExisting() != null && req.getReplaceExisting();
                String interviewDate = req != null ? req.getInterviewDate() : null;
                jobService.stage(jobId, "LOAD", 25, "正在汇总工作证据…");
                jobService.stage(jobId, "AI", 55, "正在 AI 撰写面谈答案…");
                var row = interviewGenerateService.generate(ym, replace, interviewDate);
                jobService.stage(jobId, "EXPORT", 88, "正在写入 Excel…");
                Map<String, Object> result = new HashMap<>();
                result.put("id", row.getId());
                result.put("interviewMonth", row.getInterviewMonth());
                result.put("excelPath", row.getExcelPath());
                jobService.complete(jobId, "绩效面谈生成完成", result);
            } catch (Exception e) {
                jobService.fail(jobId, AiUserMessages.from(e));
            }
        });
        return jobId;
    }
}
