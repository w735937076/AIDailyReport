package com.dailyreport.web;

import com.dailyreport.domain.SchedulerSettings;
import com.dailyreport.service.SettingsService;
import com.dailyreport.web.dto.ApiResponse;
import com.dailyreport.web.dto.SchedulerSettingsRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scheduler")
public class SchedulerController {

    private final SettingsService settingsService;

    public SchedulerController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ApiResponse<SchedulerSettings> get() {
        return ApiResponse.ok(settingsService.getSchedulerSettings());
    }

    @PutMapping
    public ApiResponse<SchedulerSettings> save(@RequestBody SchedulerSettingsRequest request) {
        return ApiResponse.ok(settingsService.saveSchedulerSettings(request));
    }
}
