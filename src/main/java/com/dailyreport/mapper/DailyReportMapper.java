package com.dailyreport.mapper;

import com.dailyreport.domain.DailyReport;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DailyReportMapper {

    @Insert("INSERT INTO daily_report (report_date, content, tomorrow_plan, raw_data_json, excel_path, status) " +
            "VALUES (#{reportDate}, #{content}, #{tomorrowPlan}, #{rawDataJson}, #{excelPath}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(DailyReport report);

    @Update("UPDATE daily_report SET content = #{content}, tomorrow_plan = #{tomorrowPlan}, " +
            "raw_data_json = #{rawDataJson}, excel_path = #{excelPath}, status = #{status}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE report_date = #{reportDate}")
    void update(DailyReport report);

    @Update("UPDATE daily_report SET content = #{content}, tomorrow_plan = #{tomorrowPlan}, " +
            "updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updateTextById(@Param("id") Long id, @Param("content") String content, @Param("tomorrowPlan") String tomorrowPlan);

    @Select("SELECT * FROM daily_report WHERE id = #{id}")
    DailyReport findById(@Param("id") Long id);

    @Select("SELECT * FROM daily_report WHERE report_date = #{date}")
    DailyReport findByDate(@Param("date") LocalDate date);

    @Select("SELECT * FROM daily_report ORDER BY report_date DESC")
    List<DailyReport> findAll();

    @Select("SELECT * FROM daily_report WHERE status = #{status}")
    List<DailyReport> findByStatus(@Param("status") String status);

    @Select("SELECT * FROM daily_report WHERE report_date BETWEEN #{startDate} AND #{endDate} ORDER BY report_date")
    List<DailyReport> findByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Select("SELECT COUNT(*) FROM daily_report")
    long countAll();

    @Select("SELECT * FROM daily_report ORDER BY report_date DESC LIMIT #{limit} OFFSET #{offset}")
    List<DailyReport> findPage(@Param("offset") int offset, @Param("limit") int limit);
}
