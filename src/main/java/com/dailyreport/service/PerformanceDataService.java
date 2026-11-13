package com.dailyreport.service;

import com.dailyreport.domain.GitCommitLog;
import com.dailyreport.domain.WorkItem;
import com.dailyreport.mapper.GitCommitLogMapper;
import com.dailyreport.mapper.WorkItemMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PerformanceDataService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final GitCommitLogMapper gitCommitLogMapper;
    private final WorkItemMapper workItemMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PerformanceDataService(GitCommitLogMapper gitCommitLogMapper, WorkItemMapper workItemMapper) {
        this.gitCommitLogMapper = gitCommitLogMapper;
        this.workItemMapper = workItemMapper;
    }

    public record MonthData(List<GitCommitLog> commits, List<WorkItem> workItems, Map<String, Object> stats) {}

    public MonthData loadMonth(String author, YearMonth ym) {
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.plusMonths(1).atDay(1).atStartOfDay();
        List<GitCommitLog> all = gitCommitLogMapper.findByAuthorAndTimeRange(author, start, end);
        List<GitCommitLog> commits = all.stream().filter(c -> !isMergeNoise(c)).toList();
        LocalDate dStart = ym.atDay(1);
        LocalDate dEnd = ym.atEndOfMonth();
        List<WorkItem> allItems = workItemMapper.findByDateRange(dStart, dEnd);
        Set<String> commitIds = commits.stream().map(GitCommitLog::getCommitId).collect(Collectors.toSet());
        List<WorkItem> workItems = allItems.stream()
                .filter(w -> w.getCommitId() != null && commitIds.contains(w.getCommitId()))
                .toList();
        Map<String, Long> byRepo = commits.stream()
                .collect(Collectors.groupingBy(c -> c.getRepoName() != null ? c.getRepoName() : "—", Collectors.counting()));
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("yearMonth", ym.toString());
        stats.put("author", author);
        stats.put("commitCount", commits.size());
        stats.put("commitCountRaw", all.size());
        stats.put("workItemCount", workItems.size());
        stats.put("repoDistribution", byRepo);
        if (!commits.isEmpty()) {
            stats.put("firstCommit", commits.get(0).getCommitTime().toString());
            stats.put("lastCommit", commits.get(commits.size() - 1).getCommitTime().toString());
        }
        return new MonthData(commits, workItems, stats);
    }

    public String statsToJson(Map<String, Object> stats) {
        try {
            return objectMapper.writeValueAsString(stats);
        } catch (Exception e) {
            return "{}";
        }
    }

    public String buildEvidenceSummary(MonthData data, int maxCommits) {
        StringBuilder sb = new StringBuilder();
        sb.append("工作项（结构化）:\n");
        Map<String, List<WorkItem>> byRepo = data.workItems().stream()
                .collect(Collectors.groupingBy(w -> w.getRepoName() != null ? w.getRepoName() : "—",
                        LinkedHashMap::new, Collectors.toList()));
        for (var e : byRepo.entrySet()) {
            sb.append("【").append(e.getKey()).append("】\n");
            int n = 0;
            for (WorkItem w : e.getValue()) {
                if (n++ >= 15) {
                    sb.append("…（其余省略）\n");
                    break;
                }
                sb.append("- ").append(w.getType()).append(" / ").append(w.getModule())
                        .append(": ").append(shorten(w.getPlan(), 80)).append("\n");
            }
        }
        sb.append("\n补充工作线索（按项目归类，供归纳；勿在绩效正文中复述下列原文）:\n");
        int shown = 0;
        for (GitCommitLog c : data.commits()) {
            if (shown >= maxCommits) break;
            sb.append("- 项目「").append(c.getRepoName() != null ? c.getRepoName() : "—")
                    .append("」: ").append(shorten(c.getMessage(), 120)).append("\n");
            shown++;
        }
        return sb.toString();
    }

    private static boolean isMergeNoise(GitCommitLog c) {
        String msg = c.getMessage();
        if (msg == null) return false;
        String m = msg.trim().toLowerCase(Locale.ROOT);
        return m.startsWith("merge branch") || m.startsWith("merge pull request")
                || m.startsWith("merge remote-tracking") || m.equals("merge");
    }

    private static String shorten(String s, int max) {
        if (s == null) return "";
        String t = s.replace('\n', ' ').trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
