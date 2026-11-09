package com.dailyreport.web;

import com.dailyreport.service.SettingsService;
import com.dailyreport.web.dto.AiSettingsRequest;
import com.dailyreport.web.dto.AiSettingsResponse;
import com.dailyreport.web.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiSettingsController {

    private final SettingsService settingsService;

    public AiSettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ApiResponse<AiSettingsResponse> get() {
        return ApiResponse.ok(settingsService.getAiSettings());
    }

    @PutMapping
    public ApiResponse<AiSettingsResponse> save(@RequestBody AiSettingsRequest request) {
        return ApiResponse.ok(settingsService.saveAiSettings(request));
    }
}
