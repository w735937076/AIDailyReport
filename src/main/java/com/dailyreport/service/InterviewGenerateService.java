package com.dailyreport.service;

import com.dailyreport.domain.PerformanceInterview;
import com.dailyreport.domain.PerformanceReview;
import com.dailyreport.mapper.PerformanceInterviewMapper;
import com.dailyreport.mapper.PerformanceReviewMapper;
import com.dailyreport.util.PromptTemplate;
import com.dailyreport.web.dto.InterviewConfigDto;
import com.dailyreport.web.dto.InterviewContentDto;
import com.dailyreport.web.dto.PerformanceContentDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Service
public class InterviewGenerateService {

    private final AiChatClient aiChatClient;
    private final PerformanceDataService performanceDataService;
    private final InterviewExcelService interviewExcelService;
    private final PerformanceInterviewMapper interviewMapper;
    private final PerformanceReviewMapper performanceReviewMapper;
    private final InterviewConfigService interviewConfigService;
    private final AiProviderService aiProviderService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    public InterviewGenerateService(AiChatClient aiChatClient,
                                  PerformanceDataService performanceDataService,
                                  InterviewExcelService interviewExcelService,
                                  PerformanceInterviewMapper interviewMapper,
                                  PerformanceReviewMapper performanceReviewMapper,
                                  InterviewConfigService interviewConfigService,
                                  AiProviderService aiProviderService) {
        this.aiChatClient = aiChatClient;
        this.performanceDataService = performanceDataService;
        this.interviewExcelService = interviewExcelService;
        this.interviewMapper = interviewMapper;
        this.performanceReviewMapper = performanceReviewMapper;
        this.interviewConfigService = interviewConfigService;
        this.aiProviderService = aiProviderService;
    }

    public PerformanceInterview generate(String yearMonthStr, boolean replaceExisting, String interviewDateStr)
            throws Exception {
        YearMonth ym = YearMonth.parse(yearMonthStr, YM);
        InterviewConfigDto config = interviewConfigService.get();
        String author = config.getAssessee();
        if (author == null || author.isBlank()) {
            throw new IllegalStateException("请先在面谈配置或定时采集中设置姓名（Git 作者名）");
        }

        PerformanceInterview existing = interviewMapper.findByMonthAndAuthor(yearMonthStr, author);
        if (existing != null && !replaceExisting) {
            throw new IllegalStateException("该月份面谈记录已存在，请确认覆盖后重新生成");
        }

        PerformanceDataService.MonthData data = performanceDataService.loadMonth(author, ym);
        String statsJson = performanceDataService.statsToJson(data.stats());
        String evidence = performanceDataService.buildEvidenceSummary(data, 35);
        String perfExcerpt = buildPerformanceExcerpt(author, yearMonthStr);

        String prompt = PromptTemplate.getPerformanceInterviewPrompt(
                config.getDepartment(),
                config.getRole(),
                author,
                formatYearMonthLabel(ym),
                statsJson,
                evidence,
                perfExcerpt);

        int longReadMs = aiProviderService.getPolicy().getLongReadTimeoutMs();
        String aiResponse = aiChatClient.complete(prompt, longReadMs);
        InterviewContentDto content = parseAiContent(aiResponse);
        LocalDate interviewDate = resolveInterviewDate(ym, interviewDateStr);
        content.setInterviewDate(interviewDate.format(ISO_DATE));
        applyDefaults(content);

        String excelPath = interviewExcelService.export(ym, config, content);
        String contentJson = objectMapper.writeValueAsString(content);

        PerformanceInterview row = existing != null ? existing : new PerformanceInterview();
        row.setInterviewMonth(yearMonthStr);
        row.setAuthor(author);
        row.setStatus("COMPLETED");
        row.setContentJson(contentJson);
        row.setCommitStatsJson(statsJson);
        row.setExcelPath(excelPath);
        if (existing == null) {
            interviewMapper.insert(row);
        } else {
            interviewMapper.update(row);
        }
        return row;
    }

    public PerformanceInterview updateContentAndExport(Long id, InterviewContentDto patch) throws Exception {
        PerformanceInterview row = interviewMapper.findById(id);
        if (row == null) {
            return null;
        }
        InterviewContentDto content = objectMapper.readValue(row.getContentJson(), InterviewContentDto.class);
        mergePatch(content, patch);
        applyDefaults(content);
        InterviewConfigDto config = interviewConfigService.get();
        YearMonth ym = YearMonth.parse(row.getInterviewMonth(), YM);
        String excelPath = interviewExcelService.export(ym, config, content);
        row.setContentJson(objectMapper.writeValueAsString(content));
        row.setExcelPath(excelPath);
        row.setStatus("COMPLETED");
        interviewMapper.update(row);
        return row;
    }

    public PerformanceInterview findById(Long id) {
        return interviewMapper.findById(id);
    }

    public InterviewContentDto patchFromUpdate(com.dailyreport.web.dto.UpdateInterviewRequest req) {
        InterviewContentDto p = new InterviewContentDto();
        if (req == null) return p;
        p.setAnswerF3(req.getAnswerF3());
        p.setAnswerF4(req.getAnswerF4());
        p.setAnswerF5(req.getAnswerF5());
        p.setAnswerF6(req.getAnswerF6());
        p.setAnswerF8(req.getAnswerF8());
        p.setAnswerF9(req.getAnswerF9());
        return p;
    }

    private void mergePatch(InterviewContentDto content, InterviewContentDto patch) {
        if (patch.getAnswerF3() != null) content.setAnswerF3(patch.getAnswerF3());
        if (patch.getAnswerF4() != null) content.setAnswerF4(patch.getAnswerF4());
        if (patch.getAnswerF5() != null) content.setAnswerF5(patch.getAnswerF5());
        if (patch.getAnswerF6() != null) content.setAnswerF6(patch.getAnswerF6());
        if (patch.getAnswerF8() != null) content.setAnswerF8(patch.getAnswerF8());
        if (patch.getAnswerF9() != null) content.setAnswerF9(patch.getAnswerF9());
        if (patch.getInterviewDate() != null) content.setInterviewDate(patch.getInterviewDate());
    }

    private static LocalDate resolveInterviewDate(YearMonth ym, String interviewDateStr) {
        if (interviewDateStr != null && !interviewDateStr.isBlank()) {
            return LocalDate.parse(interviewDateStr.trim(), ISO_DATE);
        }
        return ym.atEndOfMonth();
    }

    private String buildPerformanceExcerpt(String author, String yearMonth) {
        PerformanceReview review = performanceReviewMapper.findByMonthAndAuthor(yearMonth, author);
        if (review == null || review.getContentJson() == null || review.getContentJson().isBlank()) {
            return "";
        }
        try {
            PerformanceContentDto c = objectMapper.readValue(review.getContentJson(), PerformanceContentDto.class);
            StringBuilder sb = new StringBuilder();
            appendTruncated(sb, c.getKpiD5(), 500);
            appendTruncated(sb, c.getOverallB38(), 300);
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static void appendTruncated(StringBuilder sb, String text, int max) {
        if (text == null || text.isBlank()) return;
        String t = text.trim();
        sb.append(t.length() <= max ? t : t.substring(0, max)).append("…\n");
    }

    private InterviewContentDto parseAiContent(String response) throws Exception {
        String json = extractJsonObject(response);
        Map<?, ?> map = objectMapper.readValue(json, Map.class);
        InterviewContentDto dto = new InterviewContentDto();
        dto.setAnswerF3(str(map.get("answerF3")));
        dto.setAnswerF4(str(map.get("answerF4")));
        dto.setAnswerF5(str(map.get("answerF5")));
        dto.setAnswerF6(str(map.get("answerF6")));
        dto.setAnswerF8(str(map.get("answerF8")));
        dto.setAnswerF9(str(map.get("answerF9")));
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

    private static void applyDefaults(InterviewContentDto content) {
        if (content.getAnswerF9() == null || content.getAnswerF9().isBlank()) {
            content.setAnswerF9("无");
        }
        if (content.getSheetName() == null || content.getSheetName().isBlank()) {
            content.setSheetName("绩效面谈");
        }
    }

    private static String formatYearMonthLabel(YearMonth ym) {
        return ym.getYear() + "年" + ym.getMonthValue() + "月";
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
