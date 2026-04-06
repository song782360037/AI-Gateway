package com.code.aigateway.admin.mapper;

import com.code.aigateway.admin.model.dataobject.ProviderConfigDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 提供商配置 Mapper
 */
@Mapper
public interface ProviderConfigMapper {

    /**
     * 统一结果映射，显式声明下划线字段到驼峰属性的关系。
     */
    @Results(id = "providerConfigResultMap", value = {
            @Result(property = "id", column = "id"),
            @Result(property = "providerCode", column = "provider_code"),
            @Result(property = "providerType", column = "provider_type"),
            @Result(property = "displayName", column = "display_name"),
            @Result(property = "enabled", column = "enabled"),
            @Result(property = "baseUrl", column = "base_url"),
            @Result(property = "apiKeyCiphertext", column = "api_key_ciphertext"),
            @Result(property = "apiKeyIv", column = "api_key_iv"),
            @Result(property = "timeoutSeconds", column = "timeout_seconds"),
            @Result(property = "priority", column = "priority"),
            @Result(property = "supportedProtocols", column = "supported_protocols"),
            @Result(property = "versionNo", column = "version_no"),
            @Result(property = "creator", column = "creator"),
            @Result(property = "createTime", column = "create_time"),
            @Result(property = "updater", column = "updater"),
            @Result(property = "updateTime", column = "update_time"),
            @Result(property = "deleted", column = "deleted")
    })
    @Select("""
            SELECT id, provider_code, provider_type, display_name, enabled, base_url, api_key_ciphertext,
                   api_key_iv, timeout_seconds, priority, supported_protocols, version_no,
                   creator, create_time, updater, update_time, deleted
            FROM provider_config
            WHERE id = #{id}
              AND deleted = 0
            """)
    ProviderConfigDO selectById(@Param("id") Long id);

    /**
     * 按业务编码查询单条有效记录，便于配置管理和唯一性校验。
     */
    @Select("""
            SELECT id, provider_code, provider_type, display_name, enabled, base_url, api_key_ciphertext,
                   api_key_iv, timeout_seconds, priority, supported_protocols, version_no,
                   creator, create_time, updater, update_time, deleted
            FROM provider_config
            WHERE provider_code = #{providerCode}
              AND deleted = 0
            LIMIT 1
            """)
    @ResultMap("providerConfigResultMap")
    ProviderConfigDO selectByProviderCode(@Param("providerCode") String providerCode);

    /**
     * 插入提供商配置，并回填数据库生成的主键。
     */
    @Insert("""
            INSERT INTO provider_config (
                provider_code, provider_type, display_name, enabled, base_url, api_key_ciphertext,
                api_key_iv, timeout_seconds, priority, supported_protocols, version_no,
                creator, create_time, updater, update_time, deleted
            ) VALUES (
                #{providerCode}, #{providerType}, #{displayName}, #{enabled}, #{baseUrl}, #{apiKeyCiphertext},
                #{apiKeyIv}, #{timeoutSeconds}, #{priority}, #{supportedProtocols}, #{versionNo},
                #{creator}, #{createTime}, #{updater}, #{updateTime}, #{deleted}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ProviderConfigDO record);

    /**
     * 按主键和版本号更新，依赖乐观锁避免并发覆盖。
     */
    @Update("""
            UPDATE provider_config
            SET provider_code = #{providerCode},
                provider_type = #{providerType},
                display_name = #{displayName},
                enabled = #{enabled},
                base_url = #{baseUrl},
                api_key_ciphertext = #{apiKeyCiphertext},
                api_key_iv = #{apiKeyIv},
                timeout_seconds = #{timeoutSeconds},
                priority = #{priority},
                supported_protocols = #{supportedProtocols},
                version_no = #{versionNo} + 1,
                updater = #{updater},
                update_time = #{updateTime}
            WHERE id = #{id}
              AND version_no = #{versionNo}
              AND deleted = 0
            """)
    int updateById(ProviderConfigDO record);

    /**
     * 逻辑删除配置，保留历史数据以便审计和回滚。
     */
    @Update("""
            UPDATE provider_config
            SET deleted = 1
            WHERE id = #{id}
              AND deleted = 0
            """)
    int softDeleteById(@Param("id") Long id);

    /**
     * 仅更新启用状态（乐观锁），用于快速切换提供商开关。
     */
    @Update("""
            UPDATE provider_config
            SET enabled = #{enabled},
                version_no = version_no + 1,
                updater = #{updater},
                update_time = #{updateTime}
            WHERE id = #{id}
              AND version_no = #{versionNo}
              AND deleted = 0
            """)
    int updateEnabled(ProviderConfigDO record);

    /**
     * 分页查询提供商配置，动态拼接筛选条件。
     */
    @Select("""
            <script>
            SELECT id, provider_code, provider_type, display_name, enabled, base_url, api_key_ciphertext,
                   api_key_iv, timeout_seconds, priority, supported_protocols, version_no,
                   creator, create_time, updater, update_time, deleted
            FROM provider_config
            WHERE deleted = 0
            <if test='providerCode != null and providerCode != ""'>
                AND provider_code LIKE CONCAT('%', #{providerCode}, '%')
            </if>
            <if test='providerType != null and providerType != ""'>
                AND provider_type = #{providerType}
            </if>
            <if test='enabled != null'>
                AND enabled = #{enabled}
            </if>
            ORDER BY priority DESC, update_time DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @ResultMap("providerConfigResultMap")
    List<ProviderConfigDO> selectList(@Param("providerCode") String providerCode,
                                      @Param("providerType") String providerType,
                                      @Param("enabled") Boolean enabled,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    /**
     * 查询分页总数，保证列表和总数使用一致的过滤条件。
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM provider_config
            WHERE deleted = 0
            <if test='providerCode != null and providerCode != ""'>
                AND provider_code LIKE CONCAT('%', #{providerCode}, '%')
            </if>
            <if test='providerType != null and providerType != ""'>
                AND provider_type = #{providerType}
            </if>
            <if test='enabled != null'>
                AND enabled = #{enabled}
            </if>
            </script>
            """)
    long countList(@Param("providerCode") String providerCode,
                   @Param("providerType") String providerType,
                   @Param("enabled") Boolean enabled);

    /**
     * 检查业务编码是否已存在，用于创建前防重。
     */
    @Select("""
            SELECT COUNT(1)
            FROM provider_config
            WHERE provider_code = #{providerCode}
              AND deleted = 0
            """)
    int existsByProviderCode(@Param("providerCode") String providerCode);

    /**
     * 检查当前提供商是否被启用中的模型重定向规则引用，供删除前校验使用。
     */
    @Select("""
            SELECT COUNT(1)
            FROM model_redirect_config
            WHERE provider_code = #{providerCode}
              AND enabled = 1
              AND deleted = 0
            """)
    int existsEnabledRedirectByProviderCode(@Param("providerCode") String providerCode);

    /**
     * 仅更新优先级（乐观锁），用于拖拽排序后批量持久化新顺序。
     */
    @Update("""
            UPDATE provider_config
            SET priority = #{priority},
                version_no = version_no + 1,
                updater = #{updater},
                update_time = #{updateTime}
            WHERE id = #{id}
              AND version_no = #{versionNo}
              AND deleted = 0
            """)
    int updatePriority(ProviderConfigDO record);

    /**
     * 查询全部启用中的提供商配置，供系统启动时预热缓存或客户端使用。
     */
    @Select("""
            SELECT id, provider_code, provider_type, display_name, enabled, base_url, api_key_ciphertext,
                   api_key_iv, timeout_seconds, priority, supported_protocols, version_no,
                   creator, create_time, updater, update_time, deleted
            FROM provider_config
            WHERE enabled = 1
              AND deleted = 0
            ORDER BY priority DESC, update_time DESC
            """)
    @ResultMap("providerConfigResultMap")
    List<ProviderConfigDO> selectAllEnabled();
}
