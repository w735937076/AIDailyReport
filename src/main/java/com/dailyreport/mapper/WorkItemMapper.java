package com.dailyreport.mapper;

import com.dailyreport.domain.WorkItem;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface WorkItemMapper {

    @Insert("INSERT INTO work_item (commit_id, repo_name, work_date, type, module, plan, practice, estimate, progress, confidence, ai_raw_response) " +
            "VALUES (#{commitId}, #{repoName}, #{workDate}, #{type}, #{module}, #{plan}, #{practice}, #{estimate}, #{progress}, #{confidence}, #{aiRawResponse})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(WorkItem item);

    @Select("SELECT * FROM work_item WHERE work_date = #{date}")
    List<WorkItem> findByDate(@Param("date") LocalDate date);

    @Select("SELECT * FROM work_item WHERE work_date BETWEEN #{startDate} AND #{endDate}")
    List<WorkItem> findByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Select("SELECT DISTINCT type FROM work_item WHERE work_date = #{date}")
    List<String> findDistinctTypesByDate(@Param("date") LocalDate date);

    @Select("SELECT * FROM work_item WHERE work_date = #{date} ORDER BY type, module")
    List<WorkItem> findByDateOrdered(@Param("date") LocalDate date);

    @Delete("DELETE FROM work_item WHERE commit_id = #{commitId}")
    void deleteByCommitId(@Param("commitId") String commitId);

    @Select("SELECT DISTINCT repo_name FROM work_item WHERE repo_name IS NOT NULL AND repo_name <> '' ORDER BY repo_name")
    List<String> findDistinctRepoNames();

    @Select("<script>" +
            "SELECT id, commit_id, repo_name, work_date, type, module, plan, practice, estimate, progress, confidence, created_at " +
            "FROM work_item WHERE 1=1 " +
            "<if test='workDate != null'>AND work_date = #{workDate}</if> " +
            "<if test='repoName != null'>AND repo_name = #{repoName}</if> " +
            "<if test='type != null'>AND type = #{type}</if> " +
            "ORDER BY work_date DESC, id DESC LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<WorkItem> findPageFiltered(@Param("workDate") LocalDate workDate,
                                  @Param("repoName") String repoName,
                                  @Param("type") String type,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);

    @Select("<script>" +
            "SELECT COUNT(*) FROM work_item WHERE 1=1 " +
            "<if test='workDate != null'>AND work_date = #{workDate}</if> " +
            "<if test='repoName != null'>AND repo_name = #{repoName}</if> " +
            "<if test='type != null'>AND type = #{type}</if>" +
            "</script>")
    long countFiltered(@Param("workDate") LocalDate workDate,
                       @Param("repoName") String repoName,
                       @Param("type") String type);

    @Select("SELECT * FROM work_item WHERE id = #{id}")
    WorkItem findByIdWithRaw(@Param("id") Long id);
}
