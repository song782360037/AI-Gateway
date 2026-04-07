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
            @Result(property = "promptTokens", column = "prompt_tokens"),
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
                request_count, success_count, error_count,
                prompt_tokens, completion_tokens, total_tokens,
                total_duration_ms, estimated_cost
            ) VALUES (
                #{statTime}, #{aliasModel}, #{providerCode},
                #{requestCount}, #{successCount}, #{errorCount},
                #{promptTokens}, #{completionTokens}, #{totalTokens},
                #{totalDurationMs}, #{estimatedCost}
            )
            ON DUPLICATE KEY UPDATE
                request_count     = request_count + VALUES(request_count),
                success_count     = success_count + VALUES(success_count),
                error_count       = error_count + VALUES(error_count),
                prompt_tokens     = prompt_tokens + VALUES(prompt_tokens),
                completion_tokens = completion_tokens + VALUES(completion_tokens),
                total_tokens      = total_tokens + VALUES(total_tokens),
                total_duration_ms = total_duration_ms + VALUES(total_duration_ms),
                estimated_cost    = estimated_cost + VALUES(estimated_cost)
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
}
