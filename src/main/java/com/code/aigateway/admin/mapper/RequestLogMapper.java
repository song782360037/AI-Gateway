package com.code.aigateway.admin.mapper;

import com.code.aigateway.admin.model.dataobject.RequestLogDO;
import com.code.aigateway.admin.model.req.RequestLogQueryReq;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 请求日志 Mapper
 */
@Mapper
public interface RequestLogMapper {

    @Results(id = "requestLogResultMap", value = {
            @Result(property = "id", column = "id"),
            @Result(property = "requestId", column = "request_id"),
            @Result(property = "aliasModel", column = "alias_model"),
            @Result(property = "targetModel", column = "target_model"),
            @Result(property = "providerCode", column = "provider_code"),
            @Result(property = "providerType", column = "provider_type"),
            @Result(property = "isStream", column = "is_stream"),
            @Result(property = "promptTokens", column = "prompt_tokens"),
            @Result(property = "cachedInputTokens", column = "cached_input_tokens"),
            @Result(property = "completionTokens", column = "completion_tokens"),
            @Result(property = "totalTokens", column = "total_tokens"),
            @Result(property = "durationMs", column = "duration_ms"),
            @Result(property = "status", column = "status"),
            @Result(property = "errorCode", column = "error_code"),
            @Result(property = "errorMessage", column = "error_message"),
            @Result(property = "sourceIp", column = "source_ip"),
            @Result(property = "createTime", column = "create_time")
    })
    @Select("""
            SELECT id, request_id, alias_model, target_model, provider_code, provider_type,
                   is_stream, prompt_tokens, cached_input_tokens, completion_tokens, total_tokens, duration_ms,
                   status, error_code, error_message, source_ip, create_time
            FROM request_log
            WHERE id = #{id}
            """)
    RequestLogDO selectById(@Param("id") Long id);

    /**
     * 插入请求日志，回填主键
     */
    @Insert("""
            INSERT INTO request_log (
                request_id, alias_model, target_model, provider_code, provider_type,
                is_stream, prompt_tokens, cached_input_tokens, completion_tokens, total_tokens, duration_ms,
                status, error_code, error_message, source_ip, create_time
            ) VALUES (
                #{requestId}, #{aliasModel}, #{targetModel}, #{providerCode}, #{providerType},
                #{isStream}, #{promptTokens}, #{cachedInputTokens}, #{completionTokens}, #{totalTokens}, #{durationMs},
                #{status}, #{errorCode}, #{errorMessage}, #{sourceIp}, #{createTime}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RequestLogDO record);

    /**
     * 分页查询请求日志
     */
    List<RequestLogDO> selectPage(@Param("req") RequestLogQueryReq req,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);

    /**
     * 统计分页总数
     */
    long countPage(@Param("req") RequestLogQueryReq req);

    /**
     * 查询最近 N 条请求日志，按创建时间倒序
     * 支持可选的时间范围过滤
     */
    @Select("""
            <script>
            SELECT id, request_id, alias_model, target_model, provider_code, provider_type,
                   is_stream, prompt_tokens, cached_input_tokens, completion_tokens, total_tokens, duration_ms,
                   status, error_code, error_message, source_ip, create_time
            FROM request_log
            WHERE 1=1
            <if test='startTime != null'>
                AND create_time &gt;= #{startTime}
            </if>
            ORDER BY create_time DESC
            LIMIT #{limit}
            </script>
            """)
    @ResultMap("requestLogResultMap")
    List<RequestLogDO> selectRecent(@Param("startTime") LocalDateTime startTime,
                                    @Param("limit") int limit);

    /**
     * 统计指定时间范围内的请求总数
     */
    @Select("""
            SELECT COUNT(1) FROM request_log
            WHERE create_time >= #{startTime}
            """)
    long countByStartTime(@Param("startTime") LocalDateTime startTime);

    /**
     * 统计指定时间范围内的 Token 总消耗
     */
    @Select("""
            SELECT COALESCE(SUM(total_tokens), 0) FROM request_log
            WHERE status = 'SUCCESS' AND create_time >= #{startTime}
            """)
    long sumTokensByStartTime(@Param("startTime") LocalDateTime startTime);

    /**
     * 统计指定时间范围内的平均响应时间（ms）
     */
    @Select("""
            SELECT COALESCE(AVG(duration_ms), 0) FROM request_log
            WHERE status = 'SUCCESS' AND create_time >= #{startTime}
            """)
    double avgDurationByStartTime(@Param("startTime") LocalDateTime startTime);

    /**
     * 统计最近一分钟内的请求数（用于 RPM 计算）
     */
    @Select("""
            SELECT COUNT(1) FROM request_log
            WHERE create_time >= #{startTime}
            """)
    long countLastMinute(@Param("startTime") LocalDateTime startTime);

    /**
     * 统计最近一分钟内的 Token 数（用于 TPM 计算）
     */
    @Select("""
            SELECT COALESCE(SUM(total_tokens), 0) FROM request_log
            WHERE status = 'SUCCESS' AND create_time >= #{startTime}
            """)
    long sumTokensLastMinute(@Param("startTime") LocalDateTime startTime);

    /**
     * 按目标模型聚合统计：按调用次数降序取 Top N
     * <p>
     * 按 target_model + alias_model 联合分组，确保同一目标模型的不同别名
     * 各自独立统计，同时保证 SQL 严格模式兼容性。
     * 费用估算使用 targetModel，前端展示使用 aliasModel。
     * </p>
     */
    @Select("""
            <script>
            SELECT target_model AS targetModel,
                   alias_model AS aliasModel,
                   COUNT(1) AS callCount,
                   COALESCE(SUM(total_tokens), 0) AS tokenCount,
                   COALESCE(SUM(prompt_tokens), 0) AS promptSum,
                   COALESCE(SUM(cached_input_tokens), 0) AS cachedInputSum,
                   COALESCE(SUM(completion_tokens), 0) AS completionSum
            FROM request_log
            WHERE status = 'SUCCESS'
            <if test='startTime != null'>
                AND create_time >= #{startTime}
            </if>
            GROUP BY target_model, alias_model
            ORDER BY callCount DESC
            LIMIT #{limit}
            </script>
            """)
    List<ModelAggregation> aggregateByModel(@Param("startTime") LocalDateTime startTime,
                                            @Param("limit") int limit);

    /**
     * 模型聚合结果
     *
     * @param targetModel   实际路由到的目标模型（用于费用估算）
     * @param aliasModel    用户请求的模型别名（用于前端展示）
     * @param callCount     调用次数
     * @param tokenCount    总 Token 数
     * @param promptSum     输入 Token 总数
     * @param cachedInputSum 缓存命中 Token 总数
     * @param completionSum 输出 Token 总数
     */
    record ModelAggregation(
            String targetModel,
            String aliasModel,
            long callCount,
            long tokenCount,
            long promptSum,
            long cachedInputSum,
            long completionSum
    ) {}
}
