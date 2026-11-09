package com.dailyreport.mapper;

import com.dailyreport.domain.SchedulerSettings;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SchedulerSettingsMapper {

    @Select("SELECT id, enabled, cron, timezone, git_filter_username AS gitFilterUsername, updated_at AS updatedAt FROM app_scheduler_config WHERE id = 1")
    SchedulerSettings find();

    @Update("UPDATE app_scheduler_config SET enabled = #{enabled}, cron = #{cron}, timezone = #{timezone}, git_filter_username = #{gitFilterUsername} WHERE id = 1")
    void update(SchedulerSettings settings);
}
