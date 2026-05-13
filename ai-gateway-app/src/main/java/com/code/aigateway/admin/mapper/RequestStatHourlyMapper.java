package com.code.aigateway.admin.mapper;

import com.code.aigateway.admin.model.dataobject.RequestStatHourlyDO;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 请求小时统计 Mapper
 */
@Mapper
public interface RequestStatHourlyMapper {

    @Results(id = "statHourlyResultMap", value = {
            @Result(property = "id", column = "id"),
            @Result(property = "statTime", column = "stat_time"),
            @Result(property = "aliasModel", column = "alias_model"),
            @Result(property = "providerCode", column = "provider_code"),
            @Result(property = "requestCount", column = "request_count"),
            @Result(property = "successCount", column = "success_count"),
            @Result(property = "errorCount", column = "error_count"),
            @Result(property = "cancelCount", column = "cancel_count"),
            @Result(property = "promptTokens", column = "prompt_tokens"),
            @Result(property = "cachedInputTokens", column = "cached_input_tokens"),
            @Result(property = "completionTokens", column = "completion_tokens"),
            @Result(property = "totalTokens", column = "total_tokens"),
            @Result(property = "totalDurationMs", column = "total_duration_ms"),
            @Result(property = "estimatedCost", column = "estimated_cost"),
            @Result(property = "createTime", column = "create_time"),
            @Result(property = "updateTime", column = "update_time")
    })

    /**
     * 按 stat_time + alias_model + provider_code 做 upsert：存在则累加，不存在则插入
     */
    @Insert("""
            INSERT INTO request_stat_hourly (
                stat_time, alias_model, provider_code,
                request_count, success_count, error_count, cancel_count,
                prompt_tokens, cached_input_tokens, completion_tokens, total_tokens,
                total_duration_ms, estimated_cost
            ) VALUES (
                #{statTime}, #{aliasModel}, #{providerCode},
                #{requestCount}, #{successCount}, #{errorCount}, #{cancelCount},
                #{promptTokens}, #{cachedInputTokens}, #{completionTokens}, #{totalTokens},
                #{totalDurationMs}, #{estimatedCost}
            )
            ON DUPLICATE KEY UPDATE
                request_count       = request_count + VALUES(request_count),
                success_count       = success_count + VALUES(success_count),
                error_count         = error_count + VALUES(error_count),
                cancel_count        = cancel_count + VALUES(cancel_count),
                prompt_tokens       = prompt_tokens + VALUES(prompt_tokens),
                cached_input_tokens = cached_input_tokens + VALUES(cached_input_tokens),
                completion_tokens   = completion_tokens + VALUES(completion_tokens),
                total_tokens        = total_tokens + VALUES(total_tokens),
                total_duration_ms   = total_duration_ms + VALUES(total_duration_ms),
                estimated_cost      = estimated_cost + VALUES(estimated_cost)
            """)
    int upsert(RequestStatHourlyDO record);

    /**
     * 汇总指定时间范围内的请求总数
     */
    @Select("""
            SELECT COALESCE(SUM(request_count), 0) FROM request_stat_hourly
            WHERE stat_time >= #{startTime}
            """)
    long sumRequestCount(@Param("startTime") LocalDateTime startTime);

    /**
     * 汇总指定时间范围内的成功数
     */
    @Select("""
            SELECT COALESCE(SUM(success_count), 0) FROM request_stat_hourly
            WHERE stat_time >= #{startTime}
            """)
    long sumSuccessCount(@Param("startTime") LocalDateTime startTime);

    /**
     * 汇总全部请求总数
     */
    @Select("SELECT COALESCE(SUM(request_count), 0) FROM request_stat_hourly")
    long sumTotalRequestCount();

    /**
     * 汇总指定时间范围内的 Token 总数
     */
    @Select("""
            SELECT COALESCE(SUM(total_tokens), 0) FROM request_stat_hourly
            WHERE stat_time >= #{startTime}
            """)
    long sumTotalTokens(@Param("startTime") LocalDateTime startTime);

    /**
     * 汇总全部 Token 总数
     */
    @Select("SELECT COALESCE(SUM(total_tokens), 0) FROM request_stat_hourly")
    long sumAllTotalTokens();

    /**
     * 汇总指定时间范围内的估算费用
     */
    @Select("""
            SELECT COALESCE(SUM(estimated_cost), 0) FROM request_stat_hourly
            WHERE stat_time >= #{startTime}
            """)
    BigDecimal sumEstimatedCost(@Param("startTime") LocalDateTime startTime);

    /**
     * 汇总全部估算费用
     */
    @Select("SELECT COALESCE(SUM(estimated_cost), 0) FROM request_stat_hourly")
    BigDecimal sumAllEstimatedCost();

    /**
     * 汇总指定时间范围内的平均响应时间（ms）
     */
    @Select("""
            SELECT CASE
                WHEN SUM(request_count) = 0 THEN 0
                ELSE SUM(total_duration_ms) / SUM(request_count)
            END
            FROM request_stat_hourly
            WHERE stat_time >= #{startTime}
            """)
    Double avgDurationMs(@Param("startTime") LocalDateTime startTime);

    /**
     * 汇总指定时间范围内的总响应耗时（ms），用于计算加权平均
     */
    @Select("""
            SELECT COALESCE(SUM(total_duration_ms), 0) FROM request_stat_hourly
            WHERE stat_time >= #{startTime}
            """)
    long sumDurationMs(@Param("startTime") LocalDateTime startTime);

    /**
     * 汇总指定时间范围内的缓存 Token 总数
     */
    @Select("""
            SELECT COALESCE(SUM(cached_input_tokens), 0) FROM request_stat_hourly
            WHERE stat_time >= #{startTime}
            """)
    long sumCachedInputTokens(@Param("startTime") LocalDateTime startTime);

    /**
     * 按时间粒度查询趋势数据
     *
     * @param startTime 起始时间
     * @param pattern   DATE_FORMAT 模式：'%H:00' 按小时 / '%m-%d' 按天
     */
    @Select("""
            SELECT DATE_FORMAT(stat_time, #{pattern}) AS timeLabel,
                   COALESCE(SUM(request_count), 0) AS requestCount,
                   COALESCE(SUM(total_tokens), 0) AS tokenCount,
                   COALESCE(SUM(estimated_cost), 0) AS cost,
                   COALESCE(SUM(success_count) * 100.0 / NULLIF(SUM(request_count), 0), 100) AS successRate,
                   COALESCE(SUM(cached_input_tokens) * 100.0 / NULLIF(SUM(prompt_tokens), 0), 0) AS cacheHitRate
            FROM request_stat_hourly
            WHERE stat_time >= #{startTime}
            GROUP BY DATE_FORMAT(stat_time, #{pattern})
            ORDER BY timeLabel
            """)
    List<TrendPoint> selectTrend(@Param("startTime") LocalDateTime startTime,
                                 @Param("pattern") String pattern);

    /**
     * 按提供商查询调用分布
     */
    @Select("""
            SELECT provider_code AS providerCode,
                   COALESCE(SUM(request_count), 0) AS requestCount,
                   COALESCE(SUM(total_tokens), 0) AS tokenCount,
                   COALESCE(SUM(estimated_cost), 0) AS cost
            FROM request_stat_hourly
            WHERE stat_time >= #{startTime}
            GROUP BY provider_code
            ORDER BY requestCount DESC
            """)
    List<ProviderAgg> selectProviderDistribution(@Param("startTime") LocalDateTime startTime);

    /**
     * 趋势数据点
     */
    record TrendPoint(String timeLabel, long requestCount, long tokenCount, BigDecimal cost,
                      double successRate, double cacheHitRate) {}

    /**
     * 提供商聚合结果
     */
    record ProviderAgg(String providerCode, long requestCount, long tokenCount, BigDecimal cost) {}
}
