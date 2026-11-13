package com.dailyreport.web.dto;

import lombok.Data;

@Data
public class CollectResult {
    private int reposProcessed;
    private int commitsCollected;
    private int commitsParsed;
    private String message;
    private boolean success;
}
