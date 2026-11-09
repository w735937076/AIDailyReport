package com.dailyreport.mapper;

import com.dailyreport.domain.AiSettings;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiSettingsMapper {

    @Select("SELECT id, primary_api_key AS primaryApiKey, primary_base_url AS primaryBaseUrl, primary_model AS primaryModel, " +
            "primary_connect_timeout_ms AS primaryConnectTimeoutMs, primary_read_timeout_ms AS primaryReadTimeoutMs, " +
            "max_retries AS maxRetries, retry_delay_ms AS retryDelayMs, failure_threshold AS failureThreshold, cooldown_ms AS cooldownMs, " +
            "fallback_enabled AS fallbackEnabled, fallback_api_key AS fallbackApiKey, fallback_base_url AS fallbackBaseUrl, " +
            "fallback_model AS fallbackModel, fallback_connect_timeout_ms AS fallbackConnectTimeoutMs, " +
            "fallback_read_timeout_ms AS fallbackReadTimeoutMs, updated_at AS updatedAt FROM app_ai_config WHERE id = 1")
    AiSettings find();

    @Update("UPDATE app_ai_config SET primary_api_key = #{primaryApiKey}, primary_base_url = #{primaryBaseUrl}, primary_model = #{primaryModel}, " +
            "primary_connect_timeout_ms = #{primaryConnectTimeoutMs}, primary_read_timeout_ms = #{primaryReadTimeoutMs}, " +
            "max_retries = #{maxRetries}, retry_delay_ms = #{retryDelayMs}, failure_threshold = #{failureThreshold}, cooldown_ms = #{cooldownMs}, " +
            "fallback_enabled = #{fallbackEnabled}, fallback_api_key = #{fallbackApiKey}, fallback_base_url = #{fallbackBaseUrl}, " +
            "fallback_model = #{fallbackModel}, fallback_connect_timeout_ms = #{fallbackConnectTimeoutMs}, " +
            "fallback_read_timeout_ms = #{fallbackReadTimeoutMs} WHERE id = 1")
    void update(AiSettings settings);
}
