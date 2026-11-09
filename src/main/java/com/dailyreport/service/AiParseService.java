package com.dailyreport.service;

import com.dailyreport.domain.GitCommitLog;
import com.dailyreport.domain.WorkItem;
import com.dailyreport.mapper.WorkItemMapper;
import com.dailyreport.util.PromptTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiParseService {

    @Autowired
    private AiChatClient aiChatClient;

    @Autowired
    private WorkItemMapper workItemMapper;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public int parseUnparsedCommits(List<GitCommitLog> commits) {
        int count = 0;
        for (GitCommitLog commit : commits) {
            try {
                if (parseSingleCommit(commit)) {
                    count++;
                }
            } catch (Exception e) {
                log.error("解析 commit {} 失败: {}", commit.getCommitId(), e.getMessage());
            }
        }
        return count;
    }

    public boolean parseSingleCommit(GitCommitLog commit) {
        String message = commit.getMessage();
        if (message == null || message.isBlank()) {
            log.debug("Commit {} 无 message 内容，跳过", commit.getCommitId());
            return false;
        }

        try {
            List<WorkItem> items = callAiParse(message);

            // 保存工作项
            LocalDate workDate = commit.getCommitTime().toLocalDate();
            for (WorkItem item : items) {
                item.setCommitId(commit.getCommitId());
                item.setRepoName(commit.getRepoName());
                item.setWorkDate(workDate);
                workItemMapper.insert(item);
            }

            return !items.isEmpty();
        } catch (Exception e) {
            System.out.println("===== AI 解析失败 =====");
            System.out.println("Commit: " + commit.getCommitId());
            System.out.println("Message: " + commit.getMessage());
            System.out.println("Exception: " + e.getClass().getName());
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
            log.error("AI 解析 commit {} 失败: {}", commit.getCommitId(), e.getMessage());
            return false;
        }
    }

    private List<WorkItem> callAiParse(String message) throws Exception {
        String prompt = PromptTemplate.getDiffParsePrompt(message);
        return parseWorkItemsResponse(aiChatClient.complete(prompt), message);
    }

    private List<WorkItem> parseWorkItemsResponse(String response, String originalMessage) {
        List<WorkItem> items = new ArrayList<>();

        try {
            // 尝试提取 JSON
            String jsonStr = extractJson(response);
            System.out.println("提取的 JSON: " + jsonStr);
            log.debug("提取的 JSON: {}", jsonStr);

            // 尝试解析为数组
            try {
                List<Map<String, Object>> parsed = objectMapper.readValue(
                        jsonStr, new TypeReference<List<Map<String, Object>>>() {});

                log.debug("解析到 {} 个工作项", parsed.size());

                for (Map<String, Object> item : parsed) {
                    WorkItem workItem = new WorkItem();
                    workItem.setType((String) item.getOrDefault("type", "OTHER"));
                    workItem.setModule((String) item.getOrDefault("module", "其他"));
                    workItem.setPlan((String) item.getOrDefault("plan", ""));
                    workItem.setPractice((String) item.getOrDefault("practice", ""));
                    workItem.setConfidence(new BigDecimal("0.80"));
                    workItem.setAiRawResponse(response);
                    items.add(workItem);
                }
            } catch (Exception e1) {
                // 如果不是数组，尝试解析为单个对象
                try {
                    Map<String, Object> parsed = objectMapper.readValue(jsonStr, Map.class);
                    WorkItem workItem = new WorkItem();
                    workItem.setType((String) parsed.getOrDefault("type", "OTHER"));
                    workItem.setModule((String) parsed.getOrDefault("module", "其他"));
                    workItem.setPlan((String) parsed.getOrDefault("plan", ""));
                    workItem.setPractice((String) parsed.getOrDefault("practice", ""));
                    workItem.setConfidence(new BigDecimal("0.80"));
                    workItem.setAiRawResponse(response);
                    items.add(workItem);
                    System.out.println("解析为单个对象成功");
                } catch (Exception e2) {
                    throw new Exception("无法解析为数组或对象: " + e1.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println("解析 AI 响应失败: " + e.getMessage());
            System.out.println("AI 原始响应: " + response);
            log.warn("解析 AI 响应失败: {}", e.getMessage());
            log.warn("AI 原始响应: {}", response);
            // 返回一个默认的 OTHER 项
            WorkItem workItem = new WorkItem();
            workItem.setType("OTHER");
            workItem.setModule("其他");
            workItem.setPlan("AI 解析失败，请手动确认");
            workItem.setPractice("未知");
            workItem.setConfidence(new BigDecimal("0.00"));
            workItem.setAiRawResponse(response);
            items.add(workItem);
        }

        return items;
    }

    private String extractJson(String response) {
        // 移除 markdown 代码块标记
        String cleaned = response.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

        // 尝试找到 JSON 数组的开始和结束
        int arrayStart = cleaned.indexOf('[');
        int arrayEnd = cleaned.lastIndexOf(']');

        // 尝试找到 JSON 对象的开始和结束
        int objStart = cleaned.indexOf('{');
        int objEnd = cleaned.lastIndexOf('}');

        // 如果数组在对象之前，且数组更完整，使用数组
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            if (objStart < 0 || arrayStart < objStart) {
                return cleaned.substring(arrayStart, arrayEnd + 1);
            }
        }

        // 使用对象
        if (objStart >= 0 && objEnd > objStart) {
            return cleaned.substring(objStart, objEnd + 1);
        }

        System.out.println("无法提取 JSON，原始内容: " + response);
        return "{}";
    }

    public String generateDailyReport(List<WorkItem> workItems, String tomorrowPlan) throws Exception {
        return generateDailyReport(workItems, tomorrowPlan, "");
    }

    public String generateDailyReport(List<WorkItem> workItems, String tomorrowPlan, String nonCodeWork) throws Exception {
        String workItemsJson = objectMapper.writeValueAsString(workItems);
        String prompt = PromptTemplate.getDailyReportPrompt(workItemsJson, nonCodeWork, tomorrowPlan);

        return extractJsonResponse(aiChatClient.complete(prompt));
    }

    public String generateWeeklyReport(List<WorkItem> workItems) throws Exception {
        String workItemsJson = objectMapper.writeValueAsString(workItems);
        String prompt = PromptTemplate.getWeeklyReportPrompt(workItemsJson);

        return formatWeeklyReportTxt(aiChatClient.complete(prompt));
    }

    private String formatWeeklyReportTxt(String aiContent) {
        try {
            String jsonStr = extractJson(aiContent);
            System.out.println("提取的周报 JSON: " + jsonStr);
            Map<String, Object> data = objectMapper.readValue(jsonStr, Map.class);
            StringBuilder sb = new StringBuilder();

            sb.append("本周工作\n");
            sb.append("=".repeat(50)).append("\n\n");

            // 工作计划
            sb.append("【工作计划】\n");
            List<String> workPlans = (List<String>) data.getOrDefault("work_plans", new ArrayList<>());
            for (int i = 0; i < workPlans.size(); i++) {
                sb.append(i + 1).append("、").append(workPlans.get(i)).append("\n");
            }
            sb.append("\n");

            // 具体做法
            sb.append("【具体做法】\n");
            List<String> practices = (List<String>) data.getOrDefault("practices", new ArrayList<>());
            for (int i = 0; i < practices.size(); i++) {
                sb.append(i + 1).append("、").append(practices.get(i)).append("\n");
            }
            sb.append("\n");

            // 预计完成时间
            sb.append("【预计完成时间】\n");
            List<String> estimates = (List<String>) data.getOrDefault("estimates", new ArrayList<>());
            for (int i = 0; i < estimates.size(); i++) {
                sb.append(i + 1).append("、").append(estimates.get(i)).append("\n");
            }
            sb.append("\n");

            // 完成进度
            sb.append("【完成进度】\n");
            List<String> progress = (List<String>) data.getOrDefault("progress", new ArrayList<>());
            for (int i = 0; i < progress.size(); i++) {
                sb.append(i + 1).append("、").append(progress.get(i)).append("\n");
            }
            sb.append("\n");

            // 下周计划
            sb.append("下周计划\n");
            sb.append("-".repeat(30)).append("\n");

            sb.append("【工作计划】\n");
            List<String> nextPlans = (List<String>) data.getOrDefault("next_plans", new ArrayList<>());
            for (int i = 0; i < nextPlans.size(); i++) {
                sb.append(i + 1).append("、").append(nextPlans.get(i)).append("\n");
            }
            sb.append("\n");

            sb.append("【具体做法】\n");
            List<String> nextPractices = (List<String>) data.getOrDefault("next_practices", new ArrayList<>());
            for (int i = 0; i < nextPractices.size(); i++) {
                sb.append(i + 1).append("、").append(nextPractices.get(i)).append("\n");
            }
            sb.append("\n");

            sb.append("【预计完成时间】\n");
            List<String> nextEstimates = (List<String>) data.getOrDefault("next_estimates", new ArrayList<>());
            for (int i = 0; i < nextEstimates.size(); i++) {
                sb.append(i + 1).append("、").append(nextEstimates.get(i)).append("\n");
            }
            sb.append("\n");

            sb.append("【完成进度】\n");
            List<String> nextProgress = (List<String>) data.getOrDefault("next_progress", new ArrayList<>());
            for (int i = 0; i < nextProgress.size(); i++) {
                sb.append(i + 1).append("、").append(nextProgress.get(i)).append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            System.out.println("格式化周报失败: " + e.getMessage());
            System.out.println("原始内容: " + aiContent);
            log.warn("格式化周报失败: {}", e.getMessage());
            return aiContent;
        }
    }

    public String generateMonthlyReport(List<WorkItem> workItems) throws Exception {
        String workItemsJson = objectMapper.writeValueAsString(workItems);
        String prompt = PromptTemplate.getMonthlyReportPrompt(workItemsJson);

        return formatMonthlyReportTxt(aiChatClient.complete(prompt));
    }

    private String formatMonthlyReportTxt(String aiContent) {
        try {
            String jsonStr = extractJson(aiContent);
            System.out.println("提取的月报 JSON: " + jsonStr);
            Map<String, Object> data = objectMapper.readValue(jsonStr, Map.class);
            StringBuilder sb = new StringBuilder();

            sb.append("本月工作\n");
            sb.append("=".repeat(50)).append("\n\n");

            // 工作计划
            sb.append("【工作计划】\n");
            List<String> workPlans = (List<String>) data.getOrDefault("work_plans", new ArrayList<>());
            for (int i = 0; i < workPlans.size(); i++) {
                sb.append(i + 1).append("、").append(workPlans.get(i)).append("\n");
            }
            sb.append("\n");

            // 具体做法
            sb.append("【具体做法】\n");
            List<String> practices = (List<String>) data.getOrDefault("practices", new ArrayList<>());
            for (int i = 0; i < practices.size(); i++) {
                sb.append(i + 1).append("、").append(practices.get(i)).append("\n");
            }
            sb.append("\n");

            // 预计完成时间
            sb.append("【预计完成时间】\n");
            List<String> estimates = (List<String>) data.getOrDefault("estimates", new ArrayList<>());
            for (int i = 0; i < estimates.size(); i++) {
                sb.append(i + 1).append("、").append(estimates.get(i)).append("\n");
            }
            sb.append("\n");

            // 完成进度
            sb.append("【完成进度】\n");
            List<String> progress = (List<String>) data.getOrDefault("progress", new ArrayList<>());
            for (int i = 0; i < progress.size(); i++) {
                sb.append(i + 1).append("、").append(progress.get(i)).append("\n");
            }
            sb.append("\n");

            // 下月计划
            sb.append("下月计划\n");
            sb.append("-".repeat(30)).append("\n");

            sb.append("【工作计划】\n");
            List<String> nextPlans = (List<String>) data.getOrDefault("next_plans", new ArrayList<>());
            for (int i = 0; i < nextPlans.size(); i++) {
                sb.append(i + 1).append("、").append(nextPlans.get(i)).append("\n");
            }
            sb.append("\n");

            sb.append("【具体做法】\n");
            List<String> nextPractices = (List<String>) data.getOrDefault("next_practices", new ArrayList<>());
            for (int i = 0; i < nextPractices.size(); i++) {
                sb.append(i + 1).append("、").append(nextPractices.get(i)).append("\n");
            }
            sb.append("\n");

            sb.append("【预计完成时间】\n");
            List<String> nextEstimates = (List<String>) data.getOrDefault("next_estimates", new ArrayList<>());
            for (int i = 0; i < nextEstimates.size(); i++) {
                sb.append(i + 1).append("、").append(nextEstimates.get(i)).append("\n");
            }
            sb.append("\n");

            sb.append("【完成进度】\n");
            List<String> nextProgress = (List<String>) data.getOrDefault("next_progress", new ArrayList<>());
            for (int i = 0; i < nextProgress.size(); i++) {
                sb.append(i + 1).append("、").append(nextProgress.get(i)).append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            System.out.println("格式化月报失败: " + e.getMessage());
            System.out.println("原始内容: " + aiContent);
            log.warn("格式化月报失败: {}", e.getMessage());
            return aiContent;
        }
    }

    public String generateTechDoc(List<WorkItem> workItems) throws Exception {
        String workItemsJson = objectMapper.writeValueAsString(workItems);
        String prompt = PromptTemplate.getTechDocPrompt(workItemsJson);

        return formatTechDocTxt(aiChatClient.complete(prompt));
    }

    private String formatTechDocTxt(String aiContent) {
        try {
            String jsonStr = extractJson(aiContent);
            System.out.println("提取的技术文档 JSON: " + jsonStr);
            Map<String, Object> data = objectMapper.readValue(jsonStr, Map.class);
            StringBuilder sb = new StringBuilder();

            String projectName = (String) data.getOrDefault("projectName", "");
            String moduleName = (String) data.getOrDefault("moduleName", "");
            String date = (String) data.getOrDefault("date", "");

            sb.append("# ").append(moduleName).append(" 技术文档\n");
            sb.append("\n");
            sb.append("## 基本信息\n");
            sb.append("\n");
            sb.append("| 项目       | 内容       |\n");
            sb.append("| :--------- | :--------- |\n");
            sb.append("| 项目名称   | ").append(escapeMarkdown(projectName)).append(" |\n");
            sb.append("| 模块名称   | ").append(escapeMarkdown(moduleName)).append(" |\n");
            sb.append("| 作者       | 汪志立     |\n");
            sb.append("| 日期       | ").append(date).append(" |\n");
            sb.append("\n");

            sb.append("---\n");
            sb.append("\n");

            sb.append("## 一、需求概述\n");
            sb.append("\n");
            sb.append(formatAsBulletPoints((String) data.getOrDefault("requirementSummary", "无"))).append("\n");
            sb.append("\n");

            sb.append("---\n");
            sb.append("\n");

            sb.append("## 二、数据库设计\n");
            sb.append("\n");

            List<Map<String, Object>> tables = (List<Map<String, Object>>) data.getOrDefault("tables", new ArrayList<>());
            if (tables.isEmpty()) {
                sb.append("无\n");
            } else {
                for (int t = 0; t < tables.size(); t++) {
                    Map<String, Object> table = tables.get(t);
                    String tableName = (String) table.getOrDefault("tableName", "未命名表");
                    sb.append("### 2.").append(t + 1).append(" ").append(tableName).append("\n");
                    sb.append("\n");
                    sb.append("| 字段名     | 类型       | 说明       |\n");
                    sb.append("| :--------- | :--------- | :--------- |\n");
                    List<Map<String, String>> fields = (List<Map<String, String>>) table.getOrDefault("fields", new ArrayList<>());
                    if (fields.isEmpty()) {
                        sb.append("| -          | -          | -          |\n");
                    } else {
                        for (Map<String, String> field : fields) {
                            sb.append("| ")
                               .append(escapeMarkdown(field.getOrDefault("name", "")))
                               .append(" | ")
                               .append(escapeMarkdown(field.getOrDefault("type", "")))
                               .append(" | ")
                               .append(escapeMarkdown(field.getOrDefault("desc", "")))
                               .append(" |\n");
                        }
                    }
                    sb.append("\n");
                }

                sb.append("**索引设计**：\n");
                sb.append(formatAsBulletPoints((String) data.getOrDefault("indexes", "无"))).append("\n");
            }
            sb.append("\n");

            sb.append("---\n");
            sb.append("\n");

            sb.append("## 三、接口设计\n");
            sb.append("\n");

            List<Map<String, Object>> interfaces = (List<Map<String, Object>>) data.getOrDefault("interfaces", new ArrayList<>());
            if (interfaces.isEmpty()) {
                sb.append("无\n");
            } else {
                sb.append("| 接口名称   | 方法   | 请求路径              | 说明       |\n");
                sb.append("| :--------- | :----- | :------------------- | :--------- |\n");
                for (Map<String, Object> iface : interfaces) {
                    sb.append("| ")
                       .append(escapeMarkdown((String) iface.getOrDefault("name", "")))
                       .append(" | ")
                       .append(escapeMarkdown((String) iface.getOrDefault("method", "")))
                       .append(" | ")
                       .append(escapeMarkdown((String) iface.getOrDefault("path", "")))
                       .append(" | ")
                       .append(escapeMarkdown((String) iface.getOrDefault("desc", "")))
                       .append(" |\n");
                }
            }
            sb.append("\n");

            sb.append("**请求/响应示例**：\n");
            sb.append("\n");
            sb.append("```json\n");
            String requestExample = (String) data.getOrDefault("requestExample", "{}");
            String responseExample = (String) data.getOrDefault("responseExample", "{}");
            sb.append("// 请求\n");
            sb.append(formatJson(requestExample)).append("\n");
            sb.append("\n");
            sb.append("// 响应\n");
            sb.append(formatJson(responseExample)).append("\n");
            sb.append("```\n");
            sb.append("\n");

            sb.append("---\n");
            sb.append("\n");

            sb.append("## 四、架构设计\n");
            sb.append("\n");

            sb.append("### 4.1 整体架构\n");
            sb.append("\n");
            sb.append(formatAsBulletPoints((String) data.getOrDefault("architecture", "无"))).append("\n");
            sb.append("\n");

            sb.append("### 4.2 核心流程\n");
            sb.append("\n");
            sb.append(formatAsBulletPoints((String) data.getOrDefault("coreFlow", "无"))).append("\n");
            sb.append("\n");

            sb.append("---\n");
            sb.append("\n");

            sb.append("## 五、设计模式\n");
            sb.append("\n");
            sb.append(formatAsBulletPoints((String) data.getOrDefault("designPatterns", "无"))).append("\n");
            sb.append("\n");

            sb.append("---\n");
            sb.append("\n");

            sb.append("## 六、亮点设计\n");
            sb.append("\n");
            sb.append(formatAsBulletPoints((String) data.getOrDefault("highlights", "无"))).append("\n");

            return sb.toString();
        } catch (Exception e) {
            System.out.println("格式化技术文档失败: " + e.getMessage());
            System.out.println("原始内容: " + aiContent);
            log.warn("格式化技术文档失败: {}", e.getMessage());
            return aiContent;
        }
    }

    private String extractJsonResponse(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return "{}";
    }

    private String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("|", "\\|")
                   .replace("\n", " ")
                   .replace("*", "\\*")
                   .replace("_", "\\_");
    }

    private String formatAsBulletPoints(String content) {
        if (content == null || content.isEmpty() || "无".equals(content)) {
            return "无";
        }
        // 支持空格或换行作为编号分隔符：1. xxx 2. xxx 或 1. xxx\n2. xxx
        String[] parts = content.split("(?=\\d+[.、][ \\n])");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            // 如果已经是 - 开头，直接保留
            if (trimmed.startsWith("-")) {
                sb.append(trimmed);
            } else {
                sb.append("- ").append(trimmed);
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String formatJson(String json) {
        if (json == null || json.isEmpty()) return "{}";
        try {
            Object obj = objectMapper.readValue(json, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            return json;
        }
    }

    // OpenAI-compatible Chat Completions API 请求/响应类
    @Data
    public static class ChatCompletionRequest {
        private String model;
        private List<ChatCompletionMessage> messages;
    }

    @Data
    public static class ChatCompletionMessage {
        private String role;
        private String content;
    }

    @Data
    public static class ChatCompletionResponse {
        private List<ChatCompletionChoice> choices;

        @Data
        public static class ChatCompletionChoice {
            private ChatCompletionMessage message;
        }
    }
}
