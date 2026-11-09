package com.dailyreport.web;

import com.dailyreport.service.JobService;
import com.dailyreport.web.dto.ApiResponse;
import com.dailyreport.web.dto.JobDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping("/{jobId}")
    public ApiResponse<JobDto> get(@PathVariable String jobId) {
        JobDto job = jobService.get(jobId);
        if (job == null) {
            return ApiResponse.fail("任务不存在或已过期");
        }
        return ApiResponse.ok(job);
    }
}
