package com.dailyreport.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface AppSettingMapper {

    @Select("SELECT setting_value FROM app_setting WHERE setting_key = #{key}")
    String get(@Param("key") String key);

    @Insert("INSERT INTO app_setting (setting_key, setting_value) VALUES (#{key}, #{value}) " +
            "ON DUPLICATE KEY UPDATE setting_value = #{value}")
    void put(@Param("key") String key, @Param("value") String value);
}
