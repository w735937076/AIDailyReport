package com.dailyreport.mapper;

import com.dailyreport.domain.PerformanceReview;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface PerformanceReviewMapper {

    @Insert("INSERT INTO performance_review (review_month, author, status, content_json, commit_stats_json, excel_path) " +
            "VALUES (#{reviewMonth}, #{author}, #{status}, #{contentJson}, #{commitStatsJson}, #{excelPath})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(PerformanceReview review);

    @Update("UPDATE performance_review SET status = #{status}, content_json = #{contentJson}, " +
            "commit_stats_json = #{commitStatsJson}, excel_path = #{excelPath}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{id}")
    void update(PerformanceReview review);

    @Select("SELECT * FROM performance_review WHERE id = #{id}")
    PerformanceReview findById(@Param("id") Long id);

    @Select("SELECT * FROM performance_review WHERE review_month = #{reviewMonth} AND author = #{author}")
    PerformanceReview findByMonthAndAuthor(@Param("reviewMonth") String reviewMonth, @Param("author") String author);

    @Select("SELECT COUNT(*) FROM performance_review")
    long countAll();

    @Select("SELECT * FROM performance_review ORDER BY review_month DESC, id DESC LIMIT #{limit} OFFSET #{offset}")
    List<PerformanceReview> findPage(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT * FROM performance_review WHERE author = #{author} AND review_month < #{reviewMonth} " +
            "AND status = 'COMPLETED' ORDER BY review_month DESC LIMIT 1")
    PerformanceReview findLatestBefore(@Param("author") String author, @Param("reviewMonth") String reviewMonth);
}
