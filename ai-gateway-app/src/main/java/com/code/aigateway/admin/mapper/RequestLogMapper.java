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
            @Result(property = "responseProtocol", column = "response_protocol"),
            @Result(property = "requestPath", column = "request_path"),
            @Result(property = "httpMethod", column = "http_method"),
            @Result(property = "apiKeyPrefix", column = "api_key_prefix"),
            @Result(property = "providerApiKeyMasked", column = "provider_api_key_masked"),
            @Result(property = "providerKeyId", column = "provider_key_id"),
            @Result(property = "providerApiKeyRemark", column = "provider_api_key_remark"),
            @Result(property = "candidateCount", column = "candidate_count"),
            @Result(property = "attemptCount", column = "attempt_count"),
            @Result(property = "failoverCount", column = "failover_count"),
            @Result(property = "retryCount", column = "retry_count"),
            @Result(property = "circuitOpenSkippedCount", column = "circuit_open_skipped_count"),
            @Result(property = "rateLimitTriggered", column = "rate_limit_triggered"),
            @Result(property = "upstreamHttpStatus", column = "upstream_http_status"),
            @Result(property = "upstreamErrorType", column = "upstream_error_type"),
            @Result(property = "terminalStage", column = "terminal_stage"),
            @Result(property = "thinkingEnabled", column = "thinking_enabled"),
            @Result(property = "thinkingDepth", column = "thinking_depth"),
            @Result(property = "thinkingMapped", column = "thinking_mapped"),
            @Result(property = "traceDetailsJson", column = "trace_details_json"),
            @Result(property = "firstTokenLatencyMs", column = "first_token_latency_ms"),
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
            SELECT rl.id, rl.request_id, rl.alias_model, rl.target_model, rl.provider_code, rl.provider_type,
                   rl.response_protocol, rl.request_path, rl.http_method, rl.api_key_prefix, rl.provider_api_key_masked, rl.provider_key_id,
                   rl.candidate_count, rl.attempt_count, rl.failover_count, rl.retry_count, rl.circuit_open_skipped_count,
                   rl.rate_limit_triggered, rl.upstream_http_status, rl.upstream_error_type, rl.terminal_stage,
                   rl.thinking_enabled, rl.thinking_depth, rl.thinking_mapped, rl.trace_details_json, rl.first_token_latency_ms,
                   rl.is_stream, rl.prompt_tokens, rl.cached_input_tokens, rl.completion_tokens, rl.total_tokens, rl.duration_ms,
                   rl.status, rl.error_code, rl.error_message, rl.source_ip, rl.create_time,
                   pak.remark AS provider_api_key_remark
            FROM request_log rl
            LEFT JOIN provider_api_key pak ON rl.provider_key_id = pak.id
            WHERE rl.id = #{id}
            """)
    RequestLogDO selectById(@Param("id") Long id);

    @Select("""
            SELECT rl.id, rl.request_id, rl.alias_model, rl.target_model, rl.provider_code, rl.provider_type,
                   rl.response_protocol, rl.request_path, rl.http_method, rl.api_key_prefix, rl.provider_api_key_masked, rl.provider_key_id,
                   rl.candidate_count, rl.attempt_count, rl.failover_count, rl.retry_count, rl.circuit_open_skipped_count,
                   rl.rate_limit_triggered, rl.upstream_http_status, rl.upstream_error_type, rl.terminal_stage,
                   rl.thinking_enabled, rl.thinking_depth, rl.thinking_mapped, rl.trace_details_json, rl.first_token_latency_ms,
                   rl.is_stream, rl.prompt_tokens, rl.cached_input_tokens, rl.completion_tokens, rl.total_tokens, rl.duration_ms,
                   rl.status, rl.error_code, rl.error_message, rl.source_ip, rl.create_time,
                   pak.remark AS provider_api_key_remark
            FROM request_log rl
            LEFT JOIN provider_api_key pak ON rl.provider_key_id = pak.id
            WHERE rl.request_id = #{requestId}
            LIMIT 1
            """)
    @ResultMap("requestLogResultMap")
    RequestLogDO selectByRequestId(@Param("requestId") String requestId);

    /**
     * 插入请求日志，回填主键
     */
    @Insert("""
            INSERT INTO request_log (
                request_id, alias_model, target_model, provider_code, provider_type,
                response_protocol, request_path, http_method, api_key_prefix, provider_api_key_masked, provider_key_id,
                candidate_count, attempt_count, failover_count, retry_count, circuit_open_skipped_count,
                rate_limit_triggered, upstream_http_status, upstream_error_type, terminal_stage,
                thinking_enabled, thinking_depth, thinking_mapped, trace_details_json, first_token_latency_ms,
                is_stream, prompt_tokens, cached_input_tokens, completion_tokens, total_tokens, duration_ms,
                status, error_code, error_message, source_ip, create_time
            ) VALUES (
                #{requestId}, #{aliasModel}, #{targetModel}, #{providerCode}, #{providerType},
                #{responseProtocol}, #{requestPath}, #{httpMethod}, #{apiKeyPrefix}, #{providerApiKeyMasked}, #{providerKeyId},
                #{candidateCount}, #{attemptCount}, #{failoverCount}, #{retryCount}, #{circuitOpenSkippedCount},
                #{rateLimitTriggered}, #{upstreamHttpStatus}, #{upstreamErrorType}, #{terminalStage},
                #{thinkingEnabled}, #{thinkingDepth}, #{thinkingMapped}, #{traceDetailsJson}, #{firstTokenLatencyMs},
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
            SELECT rl.id, rl.request_id, rl.alias_model, rl.target_model, rl.provider_code, rl.provider_type,
                   rl.response_protocol, rl.request_path, rl.http_method, rl.api_key_prefix, rl.provider_api_key_masked, rl.provider_key_id,
                   rl.candidate_count, rl.attempt_count, rl.failover_count, rl.retry_count, rl.circuit_open_skipped_count,
                   rl.rate_limit_triggered, rl.upstream_http_status, rl.upstream_error_type, rl.terminal_stage,
                   rl.thinking_enabled, rl.thinking_depth, rl.thinking_mapped, rl.trace_details_json, rl.first_token_latency_ms,
                   rl.is_stream, rl.prompt_tokens, rl.cached_input_tokens, rl.completion_tokens, rl.total_tokens, rl.duration_ms,
                   rl.status, rl.error_code, rl.error_message, rl.source_ip, rl.create_time,
                   pak.remark AS provider_api_key_remark
            FROM request_log rl
            LEFT JOIN provider_api_key pak ON rl.provider_key_id = pak.id
            WHERE 1=1
            <if test='startTime != null'>
                AND rl.create_time &gt;= #{startTime}
            </if>
            ORDER BY rl.create_time DESC
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
     * 按错误码聚合统计
     */
    @Select("""
            <script>
            SELECT error_code AS errorCode, COUNT(1) AS errorCount
            FROM request_log
            WHERE status = 'ERROR'
            <if test='startTime != null'>
                AND create_time &gt;= #{startTime}
            </if>
            GROUP BY error_code
            ORDER BY errorCount DESC
            LIMIT #{limit}
            </script>
            """)
    List<ErrorAgg> aggregateByError(@Param("startTime") LocalDateTime startTime,
                                    @Param("limit") int limit);

    /**
     * 查询实时聚合指标（最近 N 分钟）
     */
    @Select("""
            SELECT COUNT(1) AS totalCount,
                   COALESCE(SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) * 100.0 / COUNT(1), 100) AS successRate,
                   COALESCE(SUM(total_tokens), 0) AS tokenSum
            FROM request_log
            WHERE create_time >= #{startTime}
            """)
    RealtimeAgg selectRealtime(@Param("startTime") LocalDateTime startTime);

    /**
     * 模型聚合结果
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

    /**
     * 错误聚合结果
     */
    record ErrorAgg(String errorCode, long errorCount) {}

    /**
     * 实时聚合结果
     */
    record RealtimeAgg(long totalCount, double successRate, long tokenSum) {}
}
