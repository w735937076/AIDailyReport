package com.dailyreport.web;

import com.dailyreport.domain.GitProject;
import com.dailyreport.service.SettingsService;
import com.dailyreport.web.dto.ApiResponse;
import com.dailyreport.web.dto.GitProjectRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final SettingsService settingsService;

    public ProjectController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ApiResponse<List<GitProject>> list() {
        return ApiResponse.ok(settingsService.listProjects());
    }

    @PostMapping
    public ApiResponse<GitProject> create(@RequestBody GitProjectRequest request) {
        return ApiResponse.ok(settingsService.createProject(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<GitProject> update(@PathVariable Long id, @RequestBody GitProjectRequest request) {
        return ApiResponse.ok(settingsService.updateProject(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        settingsService.deleteProject(id);
        return ApiResponse.ok(null);
    }
}
