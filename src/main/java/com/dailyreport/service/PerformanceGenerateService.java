package com.dailyreport.service;

import com.dailyreport.domain.PerformanceReview;
import com.dailyreport.mapper.PerformanceReviewMapper;
import com.dailyreport.util.PromptTemplate;
import com.dailyreport.web.dto.PerformanceConfigDto;
import com.dailyreport.web.dto.PerformanceContentDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class PerformanceGenerateService {

    private final AiChatClient aiChatClient;
    private final PerformanceDataService performanceDataService;
    private final PerformanceExcelService performanceExcelService;
    private final PerformanceReviewMapper performanceReviewMapper;
    private final PerformanceConfigService performanceConfigService;
    private final AiProviderService aiProviderService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");

    public PerformanceGenerateService(AiChatClient aiChatClient,
                                      PerformanceDataService performanceDataService,
                                      PerformanceExcelService performanceExcelService,
                                      PerformanceReviewMapper performanceReviewMapper,
                                      PerformanceConfigService performanceConfigService,
                                      AiProviderService aiProviderService) {
        this.aiChatClient = aiChatClient;
        this.performanceDataService = performanceDataService;
        this.performanceExcelService = performanceExcelService;
        this.performanceReviewMapper = performanceReviewMapper;
        this.performanceConfigService = performanceConfigService;
        this.aiProviderService = aiProviderService;
    }

    public PerformanceReview generate(String yearMonthStr, boolean replaceExisting) throws Exception {
        YearMonth ym = YearMonth.parse(yearMonthStr, YM);
        PerformanceConfigDto config = performanceConfigService.get();
        String author = config.getAssessee();
        if (author == null || author.isBlank()) {
            throw new IllegalStateException("请先在绩效配置或定时采集中设置被评估人（Git 作者名）");
        }

        PerformanceReview existing = performanceReviewMapper.findByMonthAndAuthor(yearMonthStr, author);
        if (existing != null && !replaceExisting) {
            throw new IllegalStateException("该月份绩效已存在，请确认覆盖后重新生成");
        }

        PerformanceDataService.MonthData data = performanceDataService.loadMonth(author, ym);
        String statsJson = performanceDataService.statsToJson(data.stats());
        String evidence = performanceDataService.buildEvidenceSummary(data, 40);
        String style = buildStyleExcerpt(author, yearMonthStr);

        String prompt = PromptTemplate.getPerformanceReviewPrompt(
                config.getDepartment(),
                config.getRole(),
                author,
                nullToEmpty(config.getAssessor()),
                formatYearMonthLabel(ym),
                statsJson,
                evidence,
                style);

        int longReadMs = aiProviderService.getPolicy().getLongReadTimeoutMs();
        String aiResponse = aiChatClient.complete(prompt, longReadMs);
        PerformanceContentDto content = parseAiContent(aiResponse);
        applyMeta(content, ym, config);
        applyDefaultScores(content, config);

        String excelPath = performanceExcelService.export(ym, config, content);
        String contentJson = objectMapper.writeValueAsString(content);

        PerformanceReview review = existing != null ? existing : new PerformanceReview();
        review.setReviewMonth(yearMonthStr);
        review.setAuthor(author);
        review.setStatus("COMPLETED");
        review.setContentJson(contentJson);
        review.setCommitStatsJson(statsJson);
        review.setExcelPath(excelPath);
        if (existing == null) {
            performanceReviewMapper.insert(review);
        } else {
            performanceReviewMapper.update(review);
        }
        return review;
    }

    public PerformanceReview updateContentAndExport(Long id, PerformanceContentDto patch) throws Exception {
        PerformanceReview review = performanceReviewMapper.findById(id);
        if (review == null) {
            return null;
        }
        PerformanceContentDto content = objectMapper.readValue(review.getContentJson(), PerformanceContentDto.class);
        if (patch.getKpiD5() != null) content.setKpiD5(patch.getKpiD5());
        if (patch.getAuxiliaryD12() != null) content.setAuxiliaryD12(patch.getAuxiliaryD12());
        if (patch.getAttitudeD15() != null) content.setAttitudeD15(patch.getAttitudeD15());
        if (patch.getOverallB38() != null) content.setOverallB38(patch.getOverallB38());
        if (patch.getA1Title() != null) content.setA1Title(patch.getA1Title());
        if (patch.getSheetName() != null) content.setSheetName(patch.getSheetName());
        if (patch.getScores() != null && !patch.getScores().isEmpty()) content.setScores(patch.getScores());

        PerformanceConfigDto config = performanceConfigService.get();
        YearMonth ym = YearMonth.parse(review.getReviewMonth(), YM);
        String excelPath = performanceExcelService.export(ym, config, content);
        review.setContentJson(objectMapper.writeValueAsString(content));
        review.setExcelPath(excelPath);
        review.setStatus("COMPLETED");
        performanceReviewMapper.update(review);
        return review;
    }

    public PerformanceReview findById(Long id) {
        return performanceReviewMapper.findById(id);
    }

    private String buildStyleExcerpt(String author, String yearMonth) {
        PerformanceReview prev = performanceReviewMapper.findLatestBefore(author, yearMonth);
        if (prev == null || prev.getContentJson() == null) {
            return "";
        }
        try {
            PerformanceContentDto c = objectMapper.readValue(prev.getContentJson(), PerformanceContentDto.class);
            StringBuilder sb = new StringBuilder();
            appendTruncated(sb, "KPI样例", c.getKpiD5(), 600);
            appendTruncated(sb, "辅助绩效样例", c.getAuxiliaryD12(), 400);
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static void appendTruncated(StringBuilder sb, String label, String text, int max) {
        if (text == null || text.isBlank()) return;
        sb.append("【").append(label).append("】\n");
        String t = text.trim();
        sb.append(t.length() <= max ? t : t.substring(0, max)).append("…\n\n");
    }

    private PerformanceContentDto parseAiContent(String response) throws Exception {
        String json = extractJsonObject(response);
        Map<?, ?> map = objectMapper.readValue(json, Map.class);
        PerformanceContentDto dto = new PerformanceContentDto();
        dto.setKpiD5(str(map.get("kpiD5")));
        dto.setAuxiliaryD12(str(map.get("auxiliaryD12")));
        dto.setAttitudeD15(str(map.get("attitudeD15")));
        dto.setOverallB38(str(map.get("overallB38")));
        return dto;
    }

    private static String extractJsonObject(String response) {
        String cleaned = response.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        throw new IllegalStateException("AI 未返回有效 JSON");
    }

    private static void applyMeta(PerformanceContentDto content, YearMonth ym, PerformanceConfigDto config) {
        String yy = String.format("%02d", ym.getYear() % 100);
        String label = yy + "年" + ym.getMonthValue() + "月";
        content.setA1Title(label + "综合评估表");
        content.setSheetName(label);
    }

    private static void applyDefaultScores(PerformanceContentDto content, PerformanceConfigDto config) {
        Map<String, Integer> scores = new LinkedHashMap<>(PerformanceContentDto.defaultScores());
        scores.put("h5", config.getScoreKpi());
        scores.put("h12", config.getScoreAuxiliary());
        scores.put("h15", config.getScoreAttitude());
        scores.put("h33", config.getScoreOther());
        content.setScores(scores);
    }

    private static String formatYearMonthLabel(YearMonth ym) {
        return ym.getYear() + "年" + ym.getMonthValue() + "月";
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
