package com.dailyreport.mapper;

import com.dailyreport.domain.GitProject;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface GitProjectMapper {

    @Select("SELECT id, name, local_path AS localPath, remote_url AS remoteUrl, branch, enabled, created_at AS createdAt, updated_at AS updatedAt FROM app_git_project ORDER BY name")
    List<GitProject> findAll();

    @Select("SELECT id, name, local_path AS localPath, remote_url AS remoteUrl, branch, enabled, created_at AS createdAt, updated_at AS updatedAt FROM app_git_project WHERE enabled = 1 ORDER BY name")
    List<GitProject> findEnabled();

    @Select("SELECT id, name, local_path AS localPath, remote_url AS remoteUrl, branch, enabled, created_at AS createdAt, updated_at AS updatedAt FROM app_git_project WHERE id = #{id}")
    GitProject findById(@Param("id") Long id);

    @Insert("INSERT INTO app_git_project (name, local_path, remote_url, branch, enabled) VALUES (#{name}, #{localPath}, #{remoteUrl}, #{branch}, #{enabled})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(GitProject project);

    @Update("UPDATE app_git_project SET name = #{name}, local_path = #{localPath}, remote_url = #{remoteUrl}, branch = #{branch}, enabled = #{enabled} WHERE id = #{id}")
    void update(GitProject project);

    @Delete("DELETE FROM app_git_project WHERE id = #{id}")
    void delete(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM app_git_project")
    int count();
}
