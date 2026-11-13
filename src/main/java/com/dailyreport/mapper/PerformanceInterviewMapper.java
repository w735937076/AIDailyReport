package com.dailyreport.mapper;

import com.dailyreport.domain.PerformanceInterview;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface PerformanceInterviewMapper {

    @Insert("INSERT INTO performance_interview (interview_month, author, status, content_json, commit_stats_json, excel_path) " +
            "VALUES (#{interviewMonth}, #{author}, #{status}, #{contentJson}, #{commitStatsJson}, #{excelPath})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(PerformanceInterview row);

    @Update("UPDATE performance_interview SET status = #{status}, content_json = #{contentJson}, " +
            "commit_stats_json = #{commitStatsJson}, excel_path = #{excelPath}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{id}")
    void update(PerformanceInterview row);

    @Select("SELECT * FROM performance_interview WHERE id = #{id}")
    PerformanceInterview findById(@Param("id") Long id);

    @Select("SELECT * FROM performance_interview WHERE interview_month = #{interviewMonth} AND author = #{author}")
    PerformanceInterview findByMonthAndAuthor(@Param("interviewMonth") String interviewMonth, @Param("author") String author);

    @Select("SELECT COUNT(*) FROM performance_interview")
    long countAll();

    @Select("SELECT * FROM performance_interview ORDER BY interview_month DESC, id DESC LIMIT #{limit} OFFSET #{offset}")
    List<PerformanceInterview> findPage(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT * FROM performance_interview WHERE author = #{author} AND interview_month < #{interviewMonth} " +
            "AND status = 'COMPLETED' ORDER BY interview_month DESC LIMIT 1")
    PerformanceInterview findLatestBefore(@Param("author") String author, @Param("interviewMonth") String interviewMonth);
}
