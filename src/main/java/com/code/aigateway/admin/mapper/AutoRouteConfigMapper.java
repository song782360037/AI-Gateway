package com.code.aigateway.admin.mapper;

import com.code.aigateway.admin.model.dataobject.AutoRouteConfigDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Auto 智能路由配置 Mapper
 */
@Mapper
public interface AutoRouteConfigMapper {

    @Results(id = "autoRouteConfigResultMap", value = {
            @Result(property = "id", column = "id"),
            @Result(property = "routeKey", column = "route_key"),
            @Result(property = "displayName", column = "display_name"),
            @Result(property = "description", column = "description"),
            @Result(property = "enabled", column = "enabled"),
            @Result(property = "selectionStrategy", column = "selection_strategy"),
            @Result(property = "versionNo", column = "version_no"),
            @Result(property = "creator", column = "creator"),
            @Result(property = "createTime", column = "create_time"),
            @Result(property = "updater", column = "updater"),
            @Result(property = "updateTime", column = "update_time"),
            @Result(property = "deleted", column = "deleted")
    })
    @Select("""
            SELECT id, route_key, display_name, description, enabled, selection_strategy, version_no,
                   creator, create_time, updater, update_time, deleted
            FROM auto_route_config
            WHERE id = #{id}
              AND deleted = 0
            """)
    AutoRouteConfigDO selectById(@Param("id") Long id);

    @Insert("""
            INSERT INTO auto_route_config (
                route_key, display_name, description, enabled, selection_strategy, version_no,
                creator, create_time, updater, update_time, deleted
            ) VALUES (
                #{routeKey}, #{displayName}, #{description}, #{enabled}, #{selectionStrategy}, #{versionNo},
                #{creator}, #{createTime}, #{updater}, #{updateTime}, #{deleted}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AutoRouteConfigDO record);

    @Update("""
            UPDATE auto_route_config
            SET route_key = #{routeKey},
                display_name = #{displayName},
                description = #{description},
                enabled = #{enabled},
                selection_strategy = #{selectionStrategy},
                version_no = version_no + 1,
                updater = #{updater},
                update_time = #{updateTime}
            WHERE id = #{id}
              AND version_no = #{versionNo}
              AND deleted = 0
            """)
    int updateById(AutoRouteConfigDO record);

    @Update("""
            UPDATE auto_route_config
            SET deleted = 1,
                route_key = CONCAT(LEFT(route_key, 64 - CHAR_LENGTH(CONCAT('_del_', id))), '_del_', id),
                updater = #{updater},
                update_time = #{updateTime}
            WHERE id = #{id}
              AND version_no = #{versionNo}
              AND deleted = 0
            """)
    int softDeleteById(AutoRouteConfigDO record);

    @Select("""
            <script>
            SELECT id, route_key, display_name, description, enabled, selection_strategy, version_no,
                   creator, create_time, updater, update_time, deleted
            FROM auto_route_config
            WHERE deleted = 0
            <if test='routeKey != null and routeKey != ""'>
                AND route_key LIKE CONCAT('%', #{routeKey}, '%')
            </if>
            <if test='displayName != null and displayName != ""'>
                AND display_name LIKE CONCAT('%', #{displayName}, '%')
            </if>
            <if test='enabled != null'>
                AND enabled = #{enabled}
            </if>
            ORDER BY update_time DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @ResultMap("autoRouteConfigResultMap")
    List<AutoRouteConfigDO> selectList(@Param("routeKey") String routeKey,
                                       @Param("displayName") String displayName,
                                       @Param("enabled") Boolean enabled,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM auto_route_config
            WHERE deleted = 0
            <if test='routeKey != null and routeKey != ""'>
                AND route_key LIKE CONCAT('%', #{routeKey}, '%')
            </if>
            <if test='displayName != null and displayName != ""'>
                AND display_name LIKE CONCAT('%', #{displayName}, '%')
            </if>
            <if test='enabled != null'>
                AND enabled = #{enabled}
            </if>
            </script>
            """)
    long countList(@Param("routeKey") String routeKey,
                   @Param("displayName") String displayName,
                   @Param("enabled") Boolean enabled);

    @Select("""
            SELECT COUNT(1)
            FROM auto_route_config
            WHERE route_key = #{routeKey}
              AND deleted = 0
            """)
    int existsByRouteKey(@Param("routeKey") String routeKey);

    @Select("""
            SELECT id, route_key, display_name, description, enabled, selection_strategy, version_no,
                   creator, create_time, updater, update_time, deleted
            FROM auto_route_config
            WHERE enabled = 1
              AND deleted = 0
            ORDER BY update_time DESC
            """)
    @ResultMap("autoRouteConfigResultMap")
    List<AutoRouteConfigDO> selectAllEnabled();

    @Update("""
            UPDATE auto_route_config
            SET enabled = #{enabled},
                version_no = version_no + 1,
                updater = #{updater},
                update_time = #{updateTime}
            WHERE id = #{id}
              AND version_no = #{versionNo}
              AND deleted = 0
            """)
    int updateEnabled(AutoRouteConfigDO record);
}
