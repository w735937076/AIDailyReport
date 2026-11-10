package com.dailyreport.mapper;

import com.dailyreport.domain.PerformanceReview;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface PerformanceReviewMapper {

    @Insert("INSERT INTO performance_review (year_month, author, status, content_json, commit_stats_json, excel_path) " +
            "VALUES (#{yearMonth}, #{author}, #{status}, #{contentJson}, #{commitStatsJson}, #{excelPath})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(PerformanceReview review);

    @Update("UPDATE performance_review SET status = #{status}, content_json = #{contentJson}, " +
            "commit_stats_json = #{commitStatsJson}, excel_path = #{excelPath}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{id}")
    void update(PerformanceReview review);

    @Select("SELECT * FROM performance_review WHERE id = #{id}")
    PerformanceReview findById(@Param("id") Long id);

    @Select("SELECT * FROM performance_review WHERE year_month = #{yearMonth} AND author = #{author}")
    PerformanceReview findByMonthAndAuthor(@Param("yearMonth") String yearMonth, @Param("author") String author);

    @Select("SELECT COUNT(*) FROM performance_review")
    long countAll();

    @Select("SELECT * FROM performance_review ORDER BY year_month DESC, id DESC LIMIT #{limit} OFFSET #{offset}")
    List<PerformanceReview> findPage(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT * FROM performance_review WHERE author = #{author} AND year_month < #{yearMonth} " +
            "AND status = 'COMPLETED' ORDER BY year_month DESC LIMIT 1")
    PerformanceReview findLatestBefore(@Param("author") String author, @Param("yearMonth") String yearMonth);
}
