package com.dailyreport.service;

import com.dailyreport.web.dto.PerformanceConfigDto;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

            PerformanceExcelService.applyConfigIdentityFields(sheet, config);

            assertEquals("被评估人：汪志立", sheet.getRow(38).getCell(1).getStringCellValue());
            assertEquals("评估人：黄鑫", sheet.getRow(38).getCell(3).getStringCellValue());
            assertEquals("人事确认：", sheet.getRow(38).getCell(5).getStringCellValue());
            assertEquals("日期：", sheet.getRow(38).getCell(7).getStringCellValue());
        }
    }

    @Test
    void applyConfigIdentityFields_scansSeparateLabelsOnOtherRows() throws Exception {
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

    @Test
    void combinedMergedSignatureRow_retainsAllFourLabels() throws Exception {
        String original = "被评估人：汪志立    评估人：汪志强    人事确认：    日期：";
        try (var wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("26年11月");
            sheet.createRow(38).createCell(1).setCellValue(original);
            sheet.addMergedRegion(new CellRangeAddress(38, 38, 1, 7));

            PerformanceConfigDto config = new PerformanceConfigDto();
            config.setAssessee("汪志立");
            config.setAssessor("黄鑫");

            PerformanceExcelService.applyConfigIdentityFields(sheet, config);

            String value = sheet.getRow(38).getCell(1).getStringCellValue();
            assertTrue(value.contains("被评估人：汪志立"), value);
            assertTrue(value.contains("评估人：黄鑫"), value);
            assertTrue(value.contains("人事确认："), value);
            assertTrue(value.contains("日期："), value);
            assertFalse(value.equals("被评估人：汪志立"));
        }
    }

    @Test
    void rewriteSignatureText_patchesInPlace() {
        String raw = "被评估人：汪志立    评估人：汪志强    人事确认：    日期：";
        String out = PerformanceExcelService.rewriteSignatureText(raw, "汪志立", "黄鑫");
        assertTrue(out.contains("评估人：黄鑫"));
        assertTrue(out.contains("人事确认："));
        assertTrue(out.contains("日期："));
        assertFalse(out.contains("汪志强"));
    }
}
