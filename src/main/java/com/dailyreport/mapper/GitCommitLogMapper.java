package com.dailyreport.mapper;

import com.dailyreport.domain.GitCommitLog;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface GitCommitLogMapper {

    @Insert("INSERT INTO git_commit_log (repo_name, commit_id, author, author_email, commit_time, message, diff_text, parsed) " +
            "VALUES (#{repoName}, #{commitId}, #{author}, #{authorEmail}, #{commitTime}, #{message}, #{diffText}, #{parsed})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(GitCommitLog log);

    @Select("SELECT * FROM git_commit_log WHERE repo_name = #{repoName} AND commit_id = #{commitId}")
    GitCommitLog findByCommitId(@Param("repoName") String repoName, @Param("commitId") String commitId);

    @Select("SELECT * FROM git_commit_log WHERE parsed = 0")
    List<GitCommitLog> findUnparsed();

    @Select("SELECT * FROM git_commit_log WHERE commit_time >= #{startTime} AND commit_time < #{endTime}")
    List<GitCommitLog> findByTimeRange(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Update("UPDATE git_commit_log SET parsed = 1 WHERE id = #{id}")
    void markAsParsed(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM git_commit_log WHERE repo_name = #{repoName}")
    int countByRepo(@Param("repoName") String repoName);
}
