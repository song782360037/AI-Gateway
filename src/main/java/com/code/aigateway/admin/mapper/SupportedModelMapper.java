package com.code.aigateway.admin.mapper;

import com.code.aigateway.admin.model.dataobject.SupportedModelDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 支持模型配置 Mapper
 */
@Mapper
public interface SupportedModelMapper {

    @Results(id = "supportedModelResultMap", value = {
            @Result(property = "id", column = "id"),
            @Result(property = "modelId", column = "model_id"),
            @Result(property = "displayName", column = "display_name"),
            @Result(property = "ownedBy", column = "owned_by"),
            @Result(property = "enabled", column = "enabled"),
            @Result(property = "sortOrder", column = "sort_order"),
            @Result(property = "versionNo", column = "version_no"),
            @Result(property = "creator", column = "creator"),
            @Result(property = "createTime", column = "create_time"),
            @Result(property = "updater", column = "updater"),
            @Result(property = "updateTime", column = "update_time"),
            @Result(property = "deleted", column = "deleted")
    })
    @Select("""
            SELECT id, model_id, display_name, owned_by, enabled, sort_order, version_no,
                   creator, create_time, updater, update_time, deleted
            FROM supported_model
            WHERE id = #{id}
              AND deleted = 0
            """)
    SupportedModelDO selectById(@Param("id") Long id);

    @Insert("""
            INSERT INTO supported_model (
                model_id, display_name, owned_by, enabled, sort_order, version_no,
                creator, create_time, updater, update_time, deleted
            ) VALUES (
                #{modelId}, #{displayName}, #{ownedBy}, #{enabled}, #{sortOrder}, #{versionNo},
                #{creator}, #{createTime}, #{updater}, #{updateTime}, #{deleted}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SupportedModelDO record);

    @Update("""
            UPDATE supported_model
            SET model_id = #{modelId},
                display_name = #{displayName},
                owned_by = #{ownedBy},
                enabled = #{enabled},
                sort_order = #{sortOrder},
                version_no = #{versionNo} + 1,
                updater = #{updater},
                update_time = #{updateTime}
            WHERE id = #{id}
              AND version_no = #{versionNo}
              AND deleted = 0
            """)
    int updateById(SupportedModelDO record);

    /**
     * 逻辑删除：同时修改 model_id 以避免唯一约束冲突（与 deleted 组合唯一键），
     * 并校验 version_no 乐观锁，防止并发删除覆盖。
     */
    @Update("""
            UPDATE supported_model
            SET deleted = 1,
                model_id = CONCAT(LEFT(model_id, 120 - CHAR_LENGTH(CONCAT('_del_', id))), '_del_', id),
                updater = #{updater},
                update_time = #{updateTime}
            WHERE id = #{id}
              AND version_no = #{versionNo}
              AND deleted = 0
            """)
    int softDeleteById(SupportedModelDO record);

    @Select("""
            <script>
            SELECT id, model_id, display_name, owned_by, enabled, sort_order, version_no,
                   creator, create_time, updater, update_time, deleted
            FROM supported_model
            WHERE deleted = 0
            <if test='modelId != null and modelId != ""'>
                AND model_id LIKE CONCAT('%', REPLACE(REPLACE(#{modelId}, '%', '\\%'), '_', '\\_'), '%') ESCAPE '\\'
            </if>
            <if test='displayName != null and displayName != ""'>
                AND display_name LIKE CONCAT('%', REPLACE(REPLACE(#{displayName}, '%', '\\%'), '_', '\\_'), '%') ESCAPE '\\'
            </if>
            <if test='ownedBy != null and ownedBy != ""'>
                AND owned_by LIKE CONCAT('%', REPLACE(REPLACE(#{ownedBy}, '%', '\\%'), '_', '\\_'), '%') ESCAPE '\\'
            </if>
            <if test='enabled != null'>
                AND enabled = #{enabled}
            </if>
            ORDER BY sort_order ASC, update_time DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @ResultMap("supportedModelResultMap")
    List<SupportedModelDO> selectList(@Param("modelId") String modelId,
                                      @Param("displayName") String displayName,
                                      @Param("ownedBy") String ownedBy,
                                      @Param("enabled") Boolean enabled,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM supported_model
            WHERE deleted = 0
            <if test='modelId != null and modelId != ""'>
                AND model_id LIKE CONCAT('%', REPLACE(REPLACE(#{modelId}, '%', '\\%'), '_', '\\_'), '%') ESCAPE '\\'
            </if>
            <if test='displayName != null and displayName != ""'>
                AND display_name LIKE CONCAT('%', REPLACE(REPLACE(#{displayName}, '%', '\\%'), '_', '\\_'), '%') ESCAPE '\\'
            </if>
            <if test='ownedBy != null and ownedBy != ""'>
                AND owned_by LIKE CONCAT('%', REPLACE(REPLACE(#{ownedBy}, '%', '\\%'), '_', '\\_'), '%') ESCAPE '\\'
            </if>
            <if test='enabled != null'>
                AND enabled = #{enabled}
            </if>
            </script>
            """)
    long countList(@Param("modelId") String modelId,
                   @Param("displayName") String displayName,
                   @Param("ownedBy") String ownedBy,
                   @Param("enabled") Boolean enabled);

    @Select("""
            SELECT COUNT(1)
            FROM supported_model
            WHERE model_id = #{modelId}
              AND deleted = 0
            """)
    int existsByModelId(@Param("modelId") String modelId);

    @Select("""
            SELECT id, model_id, display_name, owned_by, enabled, sort_order, version_no,
                   creator, create_time, updater, update_time, deleted
            FROM supported_model
            WHERE enabled = 1
              AND deleted = 0
            ORDER BY sort_order ASC, update_time DESC
            """)
    @ResultMap("supportedModelResultMap")
    List<SupportedModelDO> selectAllEnabled();

    @Update("""
            UPDATE supported_model
            SET enabled = #{enabled},
                version_no = version_no + 1,
                updater = #{updater},
                update_time = #{updateTime}
            WHERE id = #{id}
              AND version_no = #{versionNo}
              AND deleted = 0
            """)
    int updateEnabled(SupportedModelDO record);

    /**
     * 批量查询已存在的 modelId，用于同步导入时去重。
     */
    @Select("""
            <script>
            SELECT model_id
            FROM supported_model
            WHERE deleted = 0
              AND model_id IN
            <foreach collection='modelIds' item='mid' open='(' separator=',' close=')'>
                #{mid}
            </foreach>
            </script>
            """)
    List<String> selectExistingModelIds(@Param("modelIds") List<String> modelIds);
}
