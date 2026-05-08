package com.code.aigateway.admin.mapper;

import com.code.aigateway.admin.model.dataobject.GlobalConfigDO;
import org.apache.ibatis.annotations.*;

/**
 * 全局配置 Mapper
 */
@Mapper
public interface GlobalConfigMapper {

    @Results(id = "globalConfigResultMap", value = {
            @Result(property = "id", column = "id"),
            @Result(property = "configKey", column = "config_key"),
            @Result(property = "configValue", column = "config_value"),
            @Result(property = "description", column = "description"),
            @Result(property = "versionNo", column = "version_no"),
            @Result(property = "creator", column = "creator"),
            @Result(property = "createTime", column = "create_time"),
            @Result(property = "updater", column = "updater"),
            @Result(property = "updateTime", column = "update_time"),
            @Result(property = "deleted", column = "deleted")
    })
    @Select("""
            SELECT id, config_key, config_value, description, version_no,
                   creator, create_time, updater, update_time, deleted
            FROM global_config
            WHERE config_key = #{configKey}
              AND deleted = 0
            """)
    GlobalConfigDO selectByConfigKey(@Param("configKey") String configKey);

    @Update("""
            UPDATE global_config
            SET config_value = #{configValue},
                version_no = version_no + 1,
                updater = #{updater},
                update_time = #{updateTime}
            WHERE config_key = #{configKey}
              AND version_no = #{versionNo}
              AND deleted = 0
            """)
    int updateByConfigKey(GlobalConfigDO record);

    /**
     * 插入新的全局配置行（首次保存时使用，版本号从 0 开始）。
     */
    @Insert("""
            INSERT INTO global_config (config_key, config_value, description, version_no, creator, updater)
            VALUES (#{configKey}, #{configValue}, #{description}, 0, '', '')
            """)
    int insertByConfigKey(GlobalConfigDO record);
}
