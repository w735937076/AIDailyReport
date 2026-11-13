package com.dailyreport.service;

import com.dailyreport.web.dto.JobDto;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JobService {

    private final ConcurrentHashMap<String, JobState> jobs = new ConcurrentHashMap<>();

    public String create(String type) {
        String id = UUID.randomUUID().toString().replace("-", "");
        JobState state = new JobState();
        state.jobId = id;
        state.type = type;
        state.status = "PENDING";
        state.stage = "INIT";
        state.percent = 0;
        state.message = "任务已创建";
        state.startedAt = Instant.now().toEpochMilli();
        state.updatedAt = state.startedAt;
        jobs.put(id, state);
        return id;
    }

    public JobDto get(String jobId) {
        JobState state = jobs.get(jobId);
        if (state == null) {
            return null;
        }
        return state.toDto();
    }

    public void start(String jobId, String message) {
        update(jobId, "RUNNING", null, null, message, null);
    }

    public void stage(String jobId, String stage, int percent, String message) {
        update(jobId, "RUNNING", stage, percent, message, null);
    }

    public void complete(String jobId, String message, Map<String, Object> result) {
        update(jobId, "COMPLETED", "DONE", 100, message, null);
        JobState state = jobs.get(jobId);
        if (state != null) {
            state.result = result;
        }
    }

    public void fail(String jobId, String error) {
        update(jobId, "FAILED", "ERROR", null, "失败", error);
    }

    private void update(String jobId, String status, String stage, Integer percent, String message, String error) {
        JobState state = jobs.get(jobId);
        if (state == null) {
            return;
        }
        state.status = status;
        if (stage != null) {
            state.stage = stage;
        }
        if (percent != null) {
            state.percent = percent;
        }
        if (message != null) {
            state.message = message;
        }
        if (error != null) {
            state.error = error;
        }
        state.updatedAt = Instant.now().toEpochMilli();
    }

    private static class JobState {
        String jobId;
        String type;
        String status;
        String stage;
        int percent;
        String message;
        String error;
        long startedAt;
        long updatedAt;
        Map<String, Object> result;

        JobDto toDto() {
            JobDto dto = new JobDto();
            dto.setJobId(jobId);
            dto.setType(type);
            dto.setStatus(status);
            dto.setStage(stage);
            dto.setPercent(percent);
            dto.setMessage(message);
            dto.setError(error);
            dto.setStartedAt(startedAt);
            dto.setUpdatedAt(updatedAt);
            dto.setResult(result);
            return dto;
        }
    }
}
