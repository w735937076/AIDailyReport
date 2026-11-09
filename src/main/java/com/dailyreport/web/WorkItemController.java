package com.dailyreport.web;

import com.dailyreport.domain.WorkItem;
import com.dailyreport.service.WorkItemQueryService;
import com.dailyreport.web.dto.ApiResponse;
import com.dailyreport.web.dto.PageResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/work-items")
public class WorkItemController {

    private final WorkItemQueryService workItemQueryService;

    public WorkItemController(WorkItemQueryService workItemQueryService) {
        this.workItemQueryService = workItemQueryService;
    }

    @GetMapping
    public ApiResponse<PageResult<WorkItem>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workDate,
            @RequestParam(required = false) String repoName,
            @RequestParam(required = false) String type) {
        return ApiResponse.ok(workItemQueryService.search(page, size, workDate, repoName, type));
    }

    @GetMapping("/repos")
    public ApiResponse<Map<String, List<String>>> repos() {
        return ApiResponse.ok(Map.of("repos", workItemQueryService.listDistinctRepos()));
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkItem> get(@PathVariable Long id) {
        WorkItem item = workItemQueryService.getById(id);
        if (item == null) {
            return ApiResponse.fail("工作项不存在");
        }
        return ApiResponse.ok(item);
    }
}
