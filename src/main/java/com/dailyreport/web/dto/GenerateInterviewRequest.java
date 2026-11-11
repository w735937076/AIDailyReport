package com.dailyreport.web.dto;

import lombok.Data;

@Data
public class GenerateInterviewRequest {
    private String yearMonth;
    private Boolean replaceExisting;
    /** 面谈日期 yyyy-MM-dd；为空则用所选月份最后一天（Asia/Shanghai 日历日） */
    private String interviewDate;
}
