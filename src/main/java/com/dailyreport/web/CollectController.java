package com.dailyreport.web;

import com.dailyreport.service.CollectOrchestrationService;
import com.dailyreport.web.dto.ApiResponse;
import com.dailyreport.web.dto.StartJobResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/collect")
public class CollectController {

    private final CollectOrchestrationService collectOrchestrationService;

    public CollectController(CollectOrchestrationService collectOrchestrationService) {
        this.collectOrchestrationService = collectOrchestrationService;
    }

    @PostMapping("/now")
    public ApiResponse<StartJobResponse> collectNow() {
        String jobId = collectOrchestrationService.startCollectJob();
        return ApiResponse.ok(new StartJobResponse(jobId));
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        var last = collectOrchestrationService.getLastResult();
        return ApiResponse.ok(Map.of(
                "status", collectOrchestrationService.getStatus(),
                "lastResult", last != null ? last : Map.of()
        ));
    }
}
