package com.dailyreport.web.dto;

import lombok.Data;

@Data
public class InterviewConfigDto {
    private String department = "技术部";
    private String role = "java";
    /** 姓名（仅配置与 Excel 表头，不得出现在 AI 答案正文） */
    private String assessee = "";
    /** 入职日期 yyyy-MM-dd，写入 I2 */
    private String hireDate = "";
    private String templatePath = "./templates/interview/interview_template.xlsx";
    /** 本机 MonthFace 等目录（勿提交含真实姓名的 xlsx） */
    private String templateSearchDir = "";
}
