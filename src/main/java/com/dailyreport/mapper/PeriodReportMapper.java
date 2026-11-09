package com.dailyreport.mapper;

import com.dailyreport.domain.PeriodReport;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface PeriodReportMapper {

    @Insert("INSERT INTO period_report (type, start_date, end_date, content, raw_data_json, excel_path, status) " +
            "VALUES (#{type}, #{startDate}, #{endDate}, #{content}, #{rawDataJson}, #{excelPath}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(PeriodReport report);

    @Update("UPDATE period_report SET content = #{content}, raw_data_json = #{rawDataJson}, " +
            "excel_path = #{excelPath}, status = #{status}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE type = #{type} AND start_date = #{startDate}")
    void update(PeriodReport report);

    @Select("SELECT * FROM period_report WHERE type = #{type} AND start_date = #{startDate}")
    PeriodReport findByTypeAndStartDate(@Param("type") String type, @Param("startDate") LocalDate startDate);

    @Select("SELECT * FROM period_report WHERE type = #{type} ORDER BY start_date DESC")
    List<PeriodReport> findByType(@Param("type") String type);

    @Select("SELECT * FROM period_report ORDER BY start_date DESC")
    List<PeriodReport> findAll();

    @Select("SELECT COUNT(*) FROM period_report WHERE type = #{type}")
    long countByType(@Param("type") String type);

    @Select("SELECT * FROM period_report WHERE type = #{type} ORDER BY start_date DESC LIMIT #{limit} OFFSET #{offset}")
    List<PeriodReport> findPageByType(@Param("type") String type, @Param("offset") int offset, @Param("limit") int limit);
}
