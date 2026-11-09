package com.dailyreport.web;

import com.dailyreport.service.AiProviderService;
import com.dailyreport.web.dto.AiPolicyDto;
import com.dailyreport.web.dto.AiProviderRequest;
import com.dailyreport.web.dto.AiProviderResponse;
import com.dailyreport.web.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiProviderController {

    private final AiProviderService aiProviderService;

    public AiProviderController(AiProviderService aiProviderService) {
        this.aiProviderService = aiProviderService;
    }

    @GetMapping("/providers")
    public ApiResponse<List<AiProviderResponse>> listProviders() {
        return ApiResponse.ok(aiProviderService.listForApi());
    }

    @PostMapping("/providers")
    public ApiResponse<AiProviderResponse> create(@RequestBody AiProviderRequest request) {
        return ApiResponse.ok(aiProviderService.create(request));
    }

    @PutMapping("/providers/{id}")
    public ApiResponse<AiProviderResponse> update(@PathVariable Long id, @RequestBody AiProviderRequest request) {
        return ApiResponse.ok(aiProviderService.update(id, request));
    }

    @DeleteMapping("/providers/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        aiProviderService.delete(id);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/providers/{id}/enabled")
    public ApiResponse<AiProviderResponse> setEnabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        return ApiResponse.ok(aiProviderService.setEnabled(id, enabled));
    }

    @GetMapping("/policy")
    public ApiResponse<AiPolicyDto> getPolicy() {
        return ApiResponse.ok(aiProviderService.getPolicyDto());
    }

    @PutMapping("/policy")
    public ApiResponse<AiPolicyDto> savePolicy(@RequestBody AiPolicyDto dto) {
        return ApiResponse.ok(aiProviderService.savePolicy(dto));
    }
}
