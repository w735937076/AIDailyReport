package com.dailyreport.web.dto;

import lombok.Data;

@Data
public class PerformanceConfigDto {
    private String department = "技术部";
    private String role = "java";
    private String assessee = "";
    private String assessor = "";
    /** Absolute or relative path to xlsx template; optional local MonthPerformance dir */
    private String templatePath = "./templates/performance/performance_template.xlsx";
    private String templateSearchDir = "";
    private int scoreKpi = 55;
    private int scoreAuxiliary = 18;
    private int scoreAttitude = 20;
    private int scoreOther = 0;
}
