package com.dailyreport.service;

import com.dailyreport.domain.WorkItem;
import com.dailyreport.mapper.WorkItemMapper;
import com.dailyreport.web.dto.PageResult;
import com.dailyreport.web.dto.PageRequestUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class WorkItemQueryService {

    private final WorkItemMapper workItemMapper;

    public WorkItemQueryService(WorkItemMapper workItemMapper) {
        this.workItemMapper = workItemMapper;
    }

    public PageResult<WorkItem> search(int page, int size, LocalDate workDate, String repoName, String type) {
        int s = PageRequestUtil.clampSize(size);
        int p = PageRequestUtil.clampPage(page);
        int offset = PageRequestUtil.offset(p, s);
        String repo = emptyToNull(repoName);
        String typeFilter = emptyToNull(type);
        List<WorkItem> items = workItemMapper.findPageFiltered(workDate, repo, typeFilter, offset, s);
        long total = workItemMapper.countFiltered(workDate, repo, typeFilter);
        return new PageResult<>(items, total, p, s);
    }

    public WorkItem getById(Long id) {
        return workItemMapper.findByIdWithRaw(id);
    }

    public List<String> listDistinctRepos() {
        return workItemMapper.findDistinctRepoNames();
    }

    private static String emptyToNull(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        return v.trim();
    }
}
