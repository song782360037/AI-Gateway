package com.code.aigateway.admin.mapper;

import com.code.aigateway.admin.model.dataobject.ApiKeyConfigDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * API Key 配置 Mapper
 */
@Mapper
public interface ApiKeyConfigMapper {

    /** 统一结果映射 */
    @Results(id = "apiKeyConfigResultMap", value = {
            @Result(property = "id", column = "id"),
            @Result(property = "keyHash", column = "key_hash"),
            @Result(property = "keyPrefix", column = "key_prefix"),
            @Result(property = "name", column = "name"),
            @Result(property = "status", column = "status"),
            @Result(property = "dailyLimit", column = "daily_limit"),
            @Result(property = "rpmLimit", column = "rpm_limit"),
            @Result(property = "hourlyLimit", column = "hourly_limit"),
            @Result(property = "totalLimit", column = "total_limit"),
            @Result(property = "usedCount", column = "used_count"),
            @Result(property = "expireTime", column = "expire_time"),
            @Result(property = "versionNo", column = "version_no"),
            @Result(property = "creator", column = "creator"),
            @Result(property = "createTime", column = "create_time"),
            @Result(property = "updater", column = "updater"),
            @Result(property = "updateTime", column = "update_time"),
            @Result(property = "deleted", column = "deleted")
    })
    @Select("""
            SELECT id, key_hash, key_prefix, name, status, daily_limit, rpm_limit, hourly_limit,
                   total_limit, used_count, expire_time, version_no, creator, create_time, updater,
                   update_time, deleted
            FROM api_key_config
            WHERE id = #{id}
              AND deleted = 0
            """)
    ApiKeyConfigDO selectById(@Param("id") Long id);

    /** 按 key 哈希查询，供鉴权过滤器使用 */
    @Select("""
            SELECT id, key_hash, key_prefix, name, status, daily_limit, rpm_limit, hourly_limit,
                   total_limit, used_count, expire_time, version_no, creator, create_time, updater,
                   update_time, deleted
            FROM api_key_config
            WHERE key_hash = #{keyHash}
              AND deleted = 0
            LIMIT 1
            """)
    @ResultMap("apiKeyConfigResultMap")
    ApiKeyConfigDO selectByHash(@Param("keyHash") String keyHash);

    /** 插入 API Key 配置，回填主键 */
    @Insert("""
            INSERT INTO api_key_config (
                key_hash, key_prefix, name, status, daily_limit, rpm_limit, hourly_limit,
                total_limit, used_count, expire_time, version_no, creator, create_time, updater,
                update_time, deleted
            ) VALUES (
                #{keyHash}, #{keyPrefix}, #{name}, #{status}, #{dailyLimit}, #{rpmLimit}, #{hourlyLimit},
                #{totalLimit}, #{usedCount}, #{expireTime}, #{versionNo}, #{creator}, #{createTime}, #{updater},
                #{updateTime}, #{deleted}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ApiKeyConfigDO record);

    /** 按主键和版本号更新，乐观锁防并发覆盖 */
    @Update("""
            UPDATE api_key_config
            SET name = #{name},
                status = #{status},
                daily_limit = #{dailyLimit},
                rpm_limit = #{rpmLimit},
                hourly_limit = #{hourlyLimit},
                total_limit = #{totalLimit},
                expire_time = #{expireTime},
                version_no = #{versionNo} + 1,
                updater = #{updater},
                update_time = #{updateTime}
            WHERE id = #{id}
              AND version_no = #{versionNo}
              AND deleted = 0
            """)
    int updateById(ApiKeyConfigDO record);

    /** 逻辑删除 */
    @Update("""
            UPDATE api_key_config
            SET deleted = 1
            WHERE id = #{id}
              AND deleted = 0
            """)
    int softDeleteById(@Param("id") Long id);

    /** 递增已使用次数，鉴权通过后调用 */
    @Update("""
            UPDATE api_key_config
            SET used_count = used_count + 1,
                update_time = NOW()
            WHERE id = #{id}
              AND deleted = 0
            """)
    int incrementUsedCount(@Param("id") Long id);

    /** 分页查询，动态拼接筛选条件 */
    @Select("""
            <script>
            SELECT id, key_hash, key_prefix, name, status, daily_limit, rpm_limit, hourly_limit,
                   total_limit, used_count, expire_time, version_no, creator, create_time, updater,
                   update_time, deleted
            FROM api_key_config
            WHERE deleted = 0
            <if test='name != null and name != ""'>
                AND name LIKE CONCAT('%', #{name}, '%')
            </if>
            <if test='status != null and status != ""'>
                AND status = #{status}
            </if>
            ORDER BY create_time DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @ResultMap("apiKeyConfigResultMap")
    List<ApiKeyConfigDO> selectList(@Param("name") String name,
                                    @Param("status") String status,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    /** 查询分页总数 */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM api_key_config
            WHERE deleted = 0
            <if test='name != null and name != ""'>
                AND name LIKE CONCAT('%', #{name}, '%')
            </if>
            <if test='status != null and status != ""'>
                AND status = #{status}
            </if>
            </script>
            """)
    long countList(@Param("name") String name,
                   @Param("status") String status);
}
