package com.dailyreport.service;

import com.dailyreport.web.dto.PerformanceConfigDto;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceExcelServiceTest {

    @Test
    void applyConfigIdentityFields_overwritesAssessorFromTemplate() throws Exception {
        try (var wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("26年11月");
            sheet.createRow(38).createCell(1).setCellValue("被评估人：汪志立");
            sheet.getRow(38).createCell(3).setCellValue("评估人：汪志强");
            sheet.getRow(38).createCell(5).setCellValue("人事确认：张三");
            sheet.getRow(38).createCell(7).setCellValue("日期：2026-11-01");

            PerformanceConfigDto config = new PerformanceConfigDto();
            config.setAssessee("汪志立");
            config.setAssessor("黄鑫");
            config.setDepartment("技术部");
            config.setRole("java");

            PerformanceExcelService.applyConfigIdentityFields(sheet, config);

            assertEquals("被评估人：汪志立", sheet.getRow(38).getCell(1).getStringCellValue());
            assertEquals("评估人：黄鑫", sheet.getRow(38).getCell(3).getStringCellValue());
            assertEquals("人事确认：", sheet.getRow(38).getCell(5).getStringCellValue());
            assertEquals("日期：", sheet.getRow(38).getCell(7).getStringCellValue());
        }
    }

    @Test
    void applyConfigIdentityFields_scansMergedStyleLabels() throws Exception {
        try (var wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sheet1");
            int r = 40;
            sheet.createRow(r).createCell(2).setCellValue("被评估人：旧名");
            sheet.getRow(r).createCell(4).setCellValue("评估人：模板评估人");

            PerformanceConfigDto config = new PerformanceConfigDto();
            config.setAssessee("甲");
            config.setAssessor("乙");

            PerformanceExcelService.applyConfigIdentityFields(sheet, config);

            assertTrue(sheet.getRow(r).getCell(2).getStringCellValue().contains("甲"));
            assertEquals("评估人：乙", sheet.getRow(r).getCell(4).getStringCellValue());
        }
    }
}
