package com.dailyreport.web.dto;

import lombok.Data;

@Data
public class InterviewContentDto {
    private String sheetName = "绩效面谈";
    private String answerF3 = "";
    private String answerF4 = "";
    private String answerF5 = "";
    private String answerF6 = "";
    private String answerF8 = "";
    private String answerF9 = "无";
    /** yyyy-MM-dd，Excel K2 */
    private String interviewDate = "";
}
