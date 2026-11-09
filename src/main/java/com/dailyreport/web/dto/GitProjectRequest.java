package com.dailyreport.web.dto;

import lombok.Data;

@Data
public class GitProjectRequest {
    private String name;
    private String localPath;
    private String remoteUrl;
    private String branch;
    private Boolean enabled;
}
