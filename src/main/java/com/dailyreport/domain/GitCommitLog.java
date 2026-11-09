package com.dailyreport.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class GitCommitLog {
    private Long id;
    private String repoName;
    private String commitId;
    private String author;
    private String authorEmail;
    private LocalDateTime commitTime;
    private String message;
    private String diffText;
    private Boolean parsed;
    private LocalDateTime createdAt;
}
