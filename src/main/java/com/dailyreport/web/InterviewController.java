package com.dailyreport.web;

import com.dailyreport.domain.PerformanceInterview;
import com.dailyreport.mapper.PerformanceInterviewMapper;
import com.dailyreport.service.InterviewConfigService;
import com.dailyreport.service.InterviewGenerateAsyncService;
import com.dailyreport.service.InterviewGenerateService;
import com.dailyreport.web.dto.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/interview")
public class InterviewController {

    private final PerformanceInterviewMapper interviewMapper;
    private final InterviewGenerateService interviewGenerateService;
    private final InterviewGenerateAsyncService interviewGenerateAsyncService;
    private final InterviewConfigService interviewConfigService;

    public InterviewController(PerformanceInterviewMapper interviewMapper,
                               InterviewGenerateService interviewGenerateService,
                               InterviewGenerateAsyncService interviewGenerateAsyncService,
                               InterviewConfigService interviewConfigService) {
        this.interviewMapper = interviewMapper;
        this.interviewGenerateService = interviewGenerateService;
        this.interviewGenerateAsyncService = interviewGenerateAsyncService;
        this.interviewConfigService = interviewConfigService;
    }

    @GetMapping
    public ApiResponse<PageResult<PerformanceInterview>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        int offset = Math.max(0, (page - 1) * size);
        List<PerformanceInterview> items = interviewMapper.findPage(offset, size);
        long total = interviewMapper.countAll();
        return ApiResponse.ok(new PageResult<>(items, total, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<PerformanceInterview> get(@PathVariable Long id) {
        PerformanceInterview row = interviewGenerateService.findById(id);
        if (row == null) {
            return ApiResponse.fail("面谈记录不存在");
        }
        return ApiResponse.ok(row);
    }

    @GetMapping("/exists")
    public ApiResponse<Boolean> exists(@RequestParam String yearMonth) {
        var config = interviewConfigService.get();
        String author = config.getAssessee();
        if (author == null || author.isBlank()) {
            return ApiResponse.ok(false);
        }
        return ApiResponse.ok(interviewMapper.findByMonthAndAuthor(yearMonth, author) != null);
    }

    @PutMapping("/{id}")
    public ApiResponse<PerformanceInterview> update(@PathVariable Long id, @RequestBody UpdateInterviewRequest req) {
        if (req == null) {
            return ApiResponse.fail("请求体不能为空");
        }
        try {
            PerformanceInterview updated = interviewGenerateService.updateContentAndExport(
                    id, interviewGenerateService.patchFromUpdate(req));
            if (updated == null) {
                return ApiResponse.fail("面谈记录不存在");
            }
            return ApiResponse.ok(updated);
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage() != null ? e.getMessage() : "保存失败");
        }
    }

    @PostMapping("/generate")
    public ApiResponse<StartJobResponse> generate(@RequestBody GenerateInterviewRequest req) {
        String jobId = interviewGenerateAsyncService.startGenerate(req);
        return ApiResponse.ok(new StartJobResponse(jobId));
    }

    @GetMapping("/config")
    public ApiResponse<InterviewConfigDto> getConfig() {
        return ApiResponse.ok(interviewConfigService.get());
    }

    @PutMapping("/config")
    public ApiResponse<InterviewConfigDto> saveConfig(@RequestBody InterviewConfigDto dto) {
        if (dto == null) {
            return ApiResponse.fail("请求体不能为空");
        }
        interviewConfigService.save(dto);
        return ApiResponse.ok(interviewConfigService.get());
    }

    @GetMapping("/local-files")
    public ApiResponse<List<String>> listLocalTemplateFiles() {
        String dir = interviewConfigService.get().getTemplateSearchDir();
        if (dir == null || dir.isBlank()) {
            return ApiResponse.ok(List.of());
        }
        Path root = Paths.get(dir);
        if (!Files.isDirectory(root)) {
            return ApiResponse.ok(List.of());
        }
        List<String> names = new ArrayList<>();
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(p -> p.toString().toLowerCase().endsWith(".xlsx"))
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .forEach(names::add);
        } catch (IOException e) {
            return ApiResponse.fail("无法读取目录: " + e.getMessage());
        }
        return ApiResponse.ok(names);
    }
}
