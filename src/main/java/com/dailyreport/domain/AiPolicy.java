package com.dailyreport.domain;

import lombok.Data;

@Data
public class AiPolicy {
    private int maxRetries = 3;
    private int retryDelayMs = 1000;
    private int failureThreshold = 2;
    private long cooldownMs = 120_000L;
}
