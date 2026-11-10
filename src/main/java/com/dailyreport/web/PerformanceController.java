package com.dailyreport.web;

import com.dailyreport.domain.PerformanceReview;
import com.dailyreport.mapper.PerformanceReviewMapper;
import com.dailyreport.service.PerformanceConfigService;
import com.dailyreport.service.PerformanceGenerateAsyncService;
import com.dailyreport.service.PerformanceGenerateService;
import com.dailyreport.web.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/performance")
public class PerformanceController {

    private final PerformanceReviewMapper performanceReviewMapper;
    private final PerformanceGenerateService performanceGenerateService;
    private final PerformanceGenerateAsyncService performanceGenerateAsyncService;
    private final PerformanceConfigService performanceConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PerformanceController(PerformanceReviewMapper performanceReviewMapper,
                                 PerformanceGenerateService performanceGenerateService,
                                 PerformanceGenerateAsyncService performanceGenerateAsyncService,
                                 PerformanceConfigService performanceConfigService) {
        this.performanceReviewMapper = performanceReviewMapper;
        this.performanceGenerateService = performanceGenerateService;
        this.performanceGenerateAsyncService = performanceGenerateAsyncService;
        this.performanceConfigService = performanceConfigService;
    }

    @GetMapping
    public ApiResponse<PageResult<PerformanceReview>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        int offset = Math.max(0, (page - 1) * size);
        List<PerformanceReview> items = performanceReviewMapper.findPage(offset, size);
        long total = performanceReviewMapper.countAll();
        return ApiResponse.ok(new PageResult<>(items, total, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<PerformanceReview> get(@PathVariable Long id) {
        PerformanceReview review = performanceGenerateService.findById(id);
        if (review == null) {
            return ApiResponse.fail("绩效记录不存在");
        }
        return ApiResponse.ok(review);
    }

    @GetMapping("/exists")
    public ApiResponse<Boolean> exists(@RequestParam String yearMonth) {
        var config = performanceConfigService.get();
        String author = config.getAssessee();
        if (author == null || author.isBlank()) {
            return ApiResponse.ok(false);
        }
        return ApiResponse.ok(performanceReviewMapper.findByMonthAndAuthor(yearMonth, author) != null);
    }

    @PutMapping("/{id}")
    public ApiResponse<PerformanceReview> update(@PathVariable Long id, @RequestBody UpdatePerformanceRequest req) {
        if (req == null) {
            return ApiResponse.fail("请求体不能为空");
        }
        try {
            PerformanceContentDto patch = new PerformanceContentDto();
            patch.setKpiD5(req.getKpiD5());
            patch.setAuxiliaryD12(req.getAuxiliaryD12());
            patch.setAttitudeD15(req.getAttitudeD15());
            patch.setOverallB38(req.getOverallB38());
            patch.setA1Title(req.getA1Title());
            patch.setSheetName(req.getSheetName());
            PerformanceReview updated = performanceGenerateService.updateContentAndExport(id, patch);
            if (updated == null) {
                return ApiResponse.fail("绩效记录不存在");
            }
            return ApiResponse.ok(updated);
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage() != null ? e.getMessage() : "保存失败");
        }
    }

    @PostMapping("/generate")
    public ApiResponse<StartJobResponse> generate(@RequestBody GeneratePerformanceRequest req) {
        String jobId = performanceGenerateAsyncService.startGenerate(req);
        return ApiResponse.ok(new StartJobResponse(jobId));
    }

    @GetMapping("/config")
    public ApiResponse<PerformanceConfigDto> getConfig() {
        return ApiResponse.ok(performanceConfigService.get());
    }

    @PutMapping("/config")
    public ApiResponse<PerformanceConfigDto> saveConfig(@RequestBody PerformanceConfigDto dto) {
        if (dto == null) {
            return ApiResponse.fail("请求体不能为空");
        }
        performanceConfigService.save(dto);
        return ApiResponse.ok(performanceConfigService.get());
    }

    @GetMapping("/local-files")
    public ApiResponse<List<String>> listLocalTemplateFiles() {
        String dir = performanceConfigService.get().getTemplateSearchDir();
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
