package com.code.aigateway.core.ratelimit;

/**
 * 限流检查结果
 *
 * @param allowed          是否允许通过
 * @param limit            当前窗口上限
 * @param remaining        剩余可用次数
 * @param resetAtEpochSeconds 窗口重置时间（Unix 秒）
 */
public record RateLimitResult(
        boolean allowed,
        long limit,
        long remaining,
        long resetAtEpochSeconds
) {}
