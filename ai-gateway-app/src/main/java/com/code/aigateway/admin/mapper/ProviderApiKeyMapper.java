package com.code.aigateway.admin.mapper;

import com.code.aigateway.admin.model.dataobject.ProviderApiKeyDO;
import com.code.aigateway.admin.model.dto.ProviderApiKeyCountDTO;
import org.apache.ibatis.annotations.*;

import java.util.Collection;
import java.util.List;

/**
 * 提供商 API Key Mapper
 */
@Mapper
public interface ProviderApiKeyMapper {

    @Results(id = "providerApiKeyResultMap", value = {
            @Result(property = "id", column = "id"),
            @Result(property = "providerCode", column = "provider_code"),
            @Result(property = "apiKeyCiphertext", column = "api_key_ciphertext"),
            @Result(property = "apiKeyIv", column = "api_key_iv"),
            @Result(property = "apiKeyPrefix", column = "api_key_prefix"),
            @Result(property = "remark", column = "remark"),
            @Result(property = "enabled", column = "enabled"),
            @Result(property = "weight", column = "weight"),
            @Result(property = "sortOrder", column = "sort_order"),
            @Result(property = "versionNo", column = "version_no"),
            @Result(property = "creator", column = "creator"),
            @Result(property = "createTime", column = "create_time"),
            @Result(property = "updater", column = "updater"),
            @Result(property = "updateTime", column = "update_time"),
            @Result(property = "deleted", column = "deleted")
    })
    @Select("""
            SELECT id, provider_code, api_key_ciphertext, api_key_iv, api_key_prefix,
                   remark, enabled, weight, sort_order, version_no,
                   creator, create_time, updater, update_time, deleted
            FROM provider_api_key
            WHERE id = #{id}
              AND deleted = 0
            """)
    ProviderApiKeyDO selectById(@Param("id") Long id);

    /**
     * 查询指定提供商下所有未删除的 Key
     */
    @Select("""
            SELECT id, provider_code, api_key_ciphertext, api_key_iv, api_key_prefix,
                   remark, enabled, weight, sort_order, version_no,
                   creator, create_time, updater, update_time, deleted
            FROM provider_api_key
            WHERE provider_code = #{providerCode}
              AND deleted = 0
            ORDER BY sort_order ASC, id ASC
            """)
    @ResultMap("providerApiKeyResultMap")
    List<ProviderApiKeyDO> selectByProviderCode(@Param("providerCode") String providerCode);

    /**
     * 查询指定提供商下所有启用的 Key（供运行时快照使用）
     */
    @Select("""
            SELECT id, provider_code, api_key_ciphertext, api_key_iv, api_key_prefix,
                   remark, enabled, weight, sort_order, version_no,
                   creator, create_time, updater, update_time, deleted
            FROM provider_api_key
            WHERE provider_code = #{providerCode}
              AND enabled = 1
              AND deleted = 0
            ORDER BY sort_order ASC, id ASC
            """)
    @ResultMap("providerApiKeyResultMap")
    List<ProviderApiKeyDO> selectEnabledByProviderCode(@Param("providerCode") String providerCode);

    /**
     * 统计指定提供商下启用的 Key 数量
     */
    @Select("""
            SELECT COUNT(1)
            FROM provider_api_key
            WHERE provider_code = #{providerCode}
              AND enabled = 1
              AND deleted = 0
            """)
    int countEnabledByProviderCode(@Param("providerCode") String providerCode);

    /**
     * 插入 API Key 记录
     */
    @Insert("""
            INSERT INTO provider_api_key (
                provider_code, api_key_ciphertext, api_key_iv, api_key_prefix,
                remark, enabled, weight, sort_order, version_no,
                creator, create_time, updater, update_time, deleted
            ) VALUES (
                #{providerCode}, #{apiKeyCiphertext}, #{apiKeyIv}, #{apiKeyPrefix},
                #{remark}, #{enabled}, #{weight}, #{sortOrder}, #{versionNo},
                #{creator}, #{createTime}, #{updater}, #{updateTime}, #{deleted}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ProviderApiKeyDO record);

    /**
     * 按主键和版本号更新（乐观锁）
     */
    @Update("""
            UPDATE provider_api_key
            SET remark = #{remark},
                enabled = #{enabled},
                weight = #{weight},
                sort_order = #{sortOrder},
                version_no = #{versionNo} + 1,
                updater = #{updater},
                update_time = #{updateTime}
            WHERE id = #{id}
              AND version_no = #{versionNo}
              AND deleted = 0
            """)
    int updateById(ProviderApiKeyDO record);

    /**
     * 逻辑删除
     */
    @Update("""
            UPDATE provider_api_key
            SET deleted = 1
            WHERE id = #{id}
              AND deleted = 0
            """)
    int softDeleteById(@Param("id") Long id);

    /**
     * 批量统计指定提供商下启用的 Key 数量（避免 N+1 查询）
     */
    @Select({
            "<script>",
            "SELECT provider_code, COUNT(1) AS cnt FROM provider_api_key ",
            "WHERE enabled = 1 AND deleted = 0 AND provider_code IN ",
            "<foreach collection='providerCodes' item='code' open='(' separator=',' close=')'>",
            "#{code}",
            "</foreach>",
            " GROUP BY provider_code",
            "</script>"
    })
    List<ProviderApiKeyCountDTO> countEnabledGroupedByProviderCodes(@Param("providerCodes") java.util.Collection<String> providerCodes);

    /**
     * 仅更新启用状态（乐观锁）
     */
    @Update("""
            UPDATE provider_api_key
            SET enabled = #{enabled},
                version_no = #{versionNo} + 1,
                updater = #{updater},
                update_time = #{updateTime}
            WHERE id = #{id}
              AND version_no = #{versionNo}
              AND deleted = 0
            """)
    int updateEnabled(ProviderApiKeyDO record);

    /**
     * 仅更新脱敏前缀（用于修正迁移时基于密文生成的错误前缀）
     */
    @Update("""
            UPDATE provider_api_key
            SET api_key_prefix = #{apiKeyPrefix}
            WHERE id = #{id}
              AND deleted = 0
            """)
    int updatePrefix(@Param("id") Long id, @Param("apiKeyPrefix") String apiKeyPrefix);

    /**
     * 按提供商编码和脱敏前缀查重（新增时检测重复 Key）
     * <p>
     * 说明：AES-GCM 使用随机 IV，相同明文每次加密密文不同，因此无法直接按密文查重。
     * 脱敏前缀（mask）对相同明文输出恒定，且 API Key 通常为高熵长字符串，前缀碰撞概率极低，
     * 在工程实践中可有效检测重复添加。
     * </p>
     */
    @Select("""
            SELECT COUNT(1)
            FROM provider_api_key
            WHERE provider_code = #{providerCode}
              AND api_key_prefix = #{apiKeyPrefix}
              AND deleted = 0
            """)
    int countByProviderCodeAndPrefix(@Param("providerCode") String providerCode,
                                     @Param("apiKeyPrefix") String apiKeyPrefix);

    /**
     * 对指定提供商下所有未删除的 Key 行加排他锁（用于 delete/toggle 的最后 Key 保护）。
     * 必须在事务中调用，锁在事务提交后释放。
     */
    @Select("""
            SELECT id FROM provider_api_key
            WHERE provider_code = #{providerCode}
              AND deleted = 0
            FOR UPDATE
            """)
    List<Long> lockIdsForUpdate(@Param("providerCode") String providerCode);
}
