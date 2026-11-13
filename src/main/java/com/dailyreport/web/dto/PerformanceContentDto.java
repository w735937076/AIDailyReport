package com.dailyreport.web.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class PerformanceContentDto {
    private String a1Title;
    private String sheetName;
    private String kpiD5;
    private String auxiliaryD12;
    private String attitudeD15;
    private String overallB38;
    private Map<String, Integer> scores = new LinkedHashMap<>();

    public static Map<String, Integer> defaultScores() {
        Map<String, Integer> s = new LinkedHashMap<>();
        s.put("h5", 55);
        s.put("h12", 18);
        s.put("h15", 20);
        s.put("h33", 0);
        return s;
    }
}
