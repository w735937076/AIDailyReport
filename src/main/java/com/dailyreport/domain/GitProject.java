package com.dailyreport.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GitProject {
    private Long id;
    private String name;
    private String localPath;
    private String remoteUrl;
    private String branch;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
