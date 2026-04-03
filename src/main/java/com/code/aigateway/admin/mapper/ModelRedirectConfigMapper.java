package com.code.aigateway.admin.mapper;

import com.code.aigateway.admin.model.dataobject.ModelRedirectConfigDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 模型重定向配置 Mapper
 */
@Mapper
public interface ModelRedirectConfigMapper {

    /**
     * 统一结果映射，显式声明下划线字段到驼峰属性的关系。
     */
    @Results(id = "modelRedirectConfigResultMap", value = {
            @Result(property = "id", column = "id"),
            @Result(property = "aliasName", column = "alias_name"),
            @Result(property = "providerCode", column = "provider_code"),
            @Result(property = "targetModel", column = "target_model"),
            @Result(property = "enabled", column = "enabled"),
            @Result(property = "versionNo", column = "version_no"),
            @Result(property = "creator", column = "creator"),
            @Result(property = "createTime", column = "create_time"),
            @Result(property = "updater", column = "updater"),
            @Result(property = "updateTime", column = "update_time"),
            @Result(property = "deleted", column = "deleted")
    })
    @Select("""
            SELECT id, alias_name, provider_code, target_model, enabled, version_no,
                   creator, create_time, updater, update_time, deleted
            FROM model_redirect_config
            WHERE id = #{id}
              AND deleted = 0
            """)
    ModelRedirectConfigDO selectById(@Param("id") Long id);

    /**
     * 插入模型重定向配置，并回填自增主键。
     */
    @Insert("""
            INSERT INTO model_redirect_config (
                alias_name, provider_code, target_model, enabled, version_no,
                creator, create_time, updater, update_time, deleted
            ) VALUES (
                #{aliasName}, #{providerCode}, #{targetModel}, #{enabled}, #{versionNo},
                #{creator}, #{createTime}, #{updater}, #{updateTime}, #{deleted}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ModelRedirectConfigDO record);

    /**
     * 按主键和版本号更新，确保并发场景下的数据一致性。
     */
    @Update("""
            UPDATE model_redirect_config
            SET alias_name = #{aliasName},
                provider_code = #{providerCode},
                target_model = #{targetModel},
                enabled = #{enabled},
                version_no = #{versionNo} + 1,
                updater = #{updater},
                update_time = #{updateTime}
            WHERE id = #{id}
              AND version_no = #{versionNo}
              AND deleted = 0
            """)
    int updateById(ModelRedirectConfigDO record);

    /**
     * 逻辑删除重定向配置，避免物理删除造成引用追溯困难。
     */
    @Update("""
            UPDATE model_redirect_config
            SET deleted = 1
            WHERE id = #{id}
              AND deleted = 0
            """)
    int softDeleteById(@Param("id") Long id);

    /**
     * 分页查询重定向配置，支持多条件动态筛选。
     */
    @Select("""
            <script>
            SELECT id, alias_name, provider_code, target_model, enabled, version_no,
                   creator, create_time, updater, update_time, deleted
            FROM model_redirect_config
            WHERE deleted = 0
            <if test='aliasName != null and aliasName != ""'>
                AND alias_name LIKE CONCAT('%', #{aliasName}, '%')
            </if>
            <if test='providerCode != null and providerCode != ""'>
                AND provider_code = #{providerCode}
            </if>
            <if test='targetModel != null and targetModel != ""'>
                AND target_model = #{targetModel}
            </if>
            <if test='enabled != null'>
                AND enabled = #{enabled}
            </if>
            ORDER BY update_time DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @ResultMap("modelRedirectConfigResultMap")
    List<ModelRedirectConfigDO> selectList(@Param("aliasName") String aliasName,
                                           @Param("providerCode") String providerCode,
                                           @Param("targetModel") String targetModel,
                                           @Param("enabled") Boolean enabled,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    /**
     * 统计分页总数，和分页列表保持相同过滤口径。
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM model_redirect_config
            WHERE deleted = 0
            <if test='aliasName != null and aliasName != ""'>
                AND alias_name LIKE CONCAT('%', #{aliasName}, '%')
            </if>
            <if test='providerCode != null and providerCode != ""'>
                AND provider_code = #{providerCode}
            </if>
            <if test='targetModel != null and targetModel != ""'>
                AND target_model = #{targetModel}
            </if>
            <if test='enabled != null'>
                AND enabled = #{enabled}
            </if>
            </script>
            """)
    long countList(@Param("aliasName") String aliasName,
                   @Param("providerCode") String providerCode,
                   @Param("targetModel") String targetModel,
                   @Param("enabled") Boolean enabled);

    /**
     * 校验指定重定向规则是否已存在，用于新增前去重。
     */
    @Select("""
            SELECT COUNT(1)
            FROM model_redirect_config
            WHERE alias_name = #{aliasName}
              AND provider_code = #{providerCode}
              AND target_model = #{targetModel}
              AND deleted = 0
            """)
    int existsRedirect(@Param("aliasName") String aliasName,
                       @Param("providerCode") String providerCode,
                       @Param("targetModel") String targetModel);

    /**
     * 查询全部启用中的重定向配置，供路由缓存或预热使用。
     */
    @Select("""
            SELECT id, alias_name, provider_code, target_model, enabled, version_no,
                   creator, create_time, updater, update_time, deleted
            FROM model_redirect_config
            WHERE enabled = 1
              AND deleted = 0
            ORDER BY update_time DESC
            """)
    @ResultMap("modelRedirectConfigResultMap")
    List<ModelRedirectConfigDO> selectAllEnabled();

    /**
     * 按模型别名查询有效规则。
     */
    @Select("""
            SELECT id, alias_name, provider_code, target_model, enabled, version_no,
                   creator, create_time, updater, update_time, deleted
            FROM model_redirect_config
            WHERE alias_name = #{aliasName}
              AND enabled = 1
              AND deleted = 0
            ORDER BY update_time DESC
            """)
    @ResultMap("modelRedirectConfigResultMap")
    List<ModelRedirectConfigDO> selectEnabledByAliasName(@Param("aliasName") String aliasName);

    /**
     * 按提供商查询有效规则，便于 provider 维度的数据分析和校验。
     */
    @Select("""
            SELECT id, alias_name, provider_code, target_model, enabled, version_no,
                   creator, create_time, updater, update_time, deleted
            FROM model_redirect_config
            WHERE provider_code = #{providerCode}
              AND enabled = 1
              AND deleted = 0
            ORDER BY update_time DESC
            """)
    @ResultMap("modelRedirectConfigResultMap")
    List<ModelRedirectConfigDO> selectEnabledByProviderCode(@Param("providerCode") String providerCode);

    /**
     * 仅更新启用状态（乐观锁），用于快速切换路由规则开关。
     */
    @Update("""
            UPDATE model_redirect_config
            SET enabled = #{enabled},
                version_no = version_no + 1,
                updater = #{updater},
                update_time = #{updateTime}
            WHERE id = #{id}
              AND version_no = #{versionNo}
              AND deleted = 0
            """)
    int updateEnabled(ModelRedirectConfigDO record);

    /**
     * 检查提供商是否被启用中的重定向规则引用，供删除和停用前校验。
     */
    @Select("""
            SELECT COUNT(1)
            FROM model_redirect_config
            WHERE provider_code = #{providerCode}
              AND enabled = 1
              AND deleted = 0
            """)
    int existsEnabledRedirectByProviderCode(@Param("providerCode") String providerCode);
}
