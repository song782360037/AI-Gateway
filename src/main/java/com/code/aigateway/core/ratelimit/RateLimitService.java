package com.code.aigateway.core.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 分布式限流服务
 * <p>
 * 基于 Redis + Lua 脚本实现固定窗口计数器，支持每分钟和每小时的 API Key 维度限流。
 * Lua 脚本保证原子性：读取计数 + 递增 + 设置过期在一个事务中完成。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final ReactiveStringRedisTemplate redisTemplate;

    /** Lua 脚本：原子递增 + 过期 + 返回限流信息 */
    private final DefaultRedisScript<List> rateLimitScript = loadScript();

    /**
     * 检查 API Key 的限流状态
     *
     * @param keyHash     API Key 的 SHA-256 哈希
     * @param rpmLimit    每分钟请求上限
     * @param hourlyLimit 每小时请求上限
     * @return 限流检查结果
     */
    public Mono<RateLimitResult> checkRateLimit(String keyHash, int rpmLimit, int hourlyLimit) {
        long nowEpochSeconds = Instant.now().getEpochSecond();

        // 分钟级窗口
        long minuteWindow = nowEpochSeconds / 60;
        String minuteKey = "rate_limit:" + keyHash + ":m:" + minuteWindow;
        // 小时级窗口
        long hourWindow = nowEpochSeconds / 3600;
        String hourKey = "rate_limit:" + keyHash + ":h:" + hourWindow;

        // 分别检查两个维度，取最严格的那个
        Mono<RateLimitResult> minuteCheck = executeScript(minuteKey, rpmLimit, 60);
        Mono<RateLimitResult> hourCheck = executeScript(hourKey, hourlyLimit, 3600);

        return Mono.zip(minuteCheck, hourCheck)
                .map(tuple -> {
                    RateLimitResult minute = tuple.getT1();
                    RateLimitResult hour = tuple.getT2();
                    // 两个维度都通过才算通过
                    boolean allowed = minute.allowed() && hour.allowed();
                    // 取剩余量更少的维度
                    long remaining = Math.min(minute.remaining(), hour.remaining());
                    long limit = Math.min(minute.limit(), hour.limit());
                    long resetAt = Math.min(minute.resetAtEpochSeconds(), hour.resetAtEpochSeconds());
                    return new RateLimitResult(allowed, limit, remaining, resetAt);
                })
                .onErrorResume(ex -> {
                    // Redis 不可用时放行，不影响主链路
                    log.warn("[限流] Redis 检查失败，放行请求: {}", ex.getMessage());
                    return Mono.just(new RateLimitResult(true, rpmLimit, rpmLimit,
                            nowEpochSeconds + 60));
                });
    }

    /**
     * 执行 Lua 限流脚本
     */
    @SuppressWarnings("unchecked")
    private Mono<RateLimitResult> executeScript(String key, int limit, int windowSeconds) {
        long nowEpochSeconds = Instant.now().getEpochSecond();
        long resetAt = (nowEpochSeconds / windowSeconds + 1) * windowSeconds;

        return redisTemplate.execute(rateLimitScript,
                        List.of(key),
                        List.of(String.valueOf(limit), String.valueOf(windowSeconds))
                )
                .next()
                .map(result -> {
                    List<Long> values = (List<Long>) result;
                    long currentCount = values.get(0);
                    long ttlSeconds = values.get(1);
                    boolean allowed = currentCount <= limit;
                    long remaining = Math.max(0, limit - currentCount);
                    return new RateLimitResult(allowed, limit, remaining, resetAt);
                });
    }

    private DefaultRedisScript<List> loadScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/rate_limit.lua"));
        script.setResultType(List.class);
        return script;
    }
}
