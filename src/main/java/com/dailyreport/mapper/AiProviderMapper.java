package com.dailyreport.mapper;

import com.dailyreport.domain.AiProvider;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AiProviderMapper {

    @Select("SELECT id, name, base_url AS baseUrl, api_key AS apiKey, model, connect_timeout_ms AS connectTimeoutMs, " +
            "read_timeout_ms AS readTimeoutMs, enabled, priority, created_at AS createdAt, updated_at AS updatedAt " +
            "FROM app_ai_provider ORDER BY priority ASC, id ASC")
    List<AiProvider> findAll();

    @Select("SELECT id, name, base_url AS baseUrl, api_key AS apiKey, model, connect_timeout_ms AS connectTimeoutMs, " +
            "read_timeout_ms AS readTimeoutMs, enabled, priority, created_at AS createdAt, updated_at AS updatedAt " +
            "FROM app_ai_provider WHERE enabled = 1 ORDER BY priority ASC, id ASC")
    List<AiProvider> findEnabled();

    @Select("SELECT id, name, base_url AS baseUrl, api_key AS apiKey, model, connect_timeout_ms AS connectTimeoutMs, " +
            "read_timeout_ms AS readTimeoutMs, enabled, priority, created_at AS createdAt, updated_at AS updatedAt " +
            "FROM app_ai_provider WHERE id = #{id}")
    AiProvider findById(@Param("id") Long id);

    @Insert("INSERT INTO app_ai_provider (name, base_url, api_key, model, connect_timeout_ms, read_timeout_ms, enabled, priority) " +
            "VALUES (#{name}, #{baseUrl}, #{apiKey}, #{model}, #{connectTimeoutMs}, #{readTimeoutMs}, #{enabled}, #{priority})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(AiProvider provider);

    @Update("UPDATE app_ai_provider SET name = #{name}, base_url = #{baseUrl}, api_key = #{apiKey}, model = #{model}, " +
            "connect_timeout_ms = #{connectTimeoutMs}, read_timeout_ms = #{readTimeoutMs}, enabled = #{enabled}, priority = #{priority} " +
            "WHERE id = #{id}")
    void update(AiProvider provider);

    @Update("UPDATE app_ai_provider SET enabled = #{enabled} WHERE id = #{id}")
    void updateEnabled(@Param("id") Long id, @Param("enabled") boolean enabled);

    @Update("UPDATE app_ai_provider SET priority = #{priority} WHERE id = #{id}")
    void updatePriority(@Param("id") Long id, @Param("priority") int priority);

    @Delete("DELETE FROM app_ai_provider WHERE id = #{id}")
    void delete(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM app_ai_provider")
    int count();
}
