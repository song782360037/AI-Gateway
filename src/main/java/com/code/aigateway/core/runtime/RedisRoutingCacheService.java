package com.code.aigateway.core.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Redis 路由缓存服务
 *
 * <p>负责运行时快照以及路由相关缓存键的读写与失效控制。</p>
 * <p>所有 Redis 异常都会被吞掉并记录告警，避免影响主业务链路。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRoutingCacheService {

    /** Spring 提供的字符串 Redis 客户端 */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 预热整份路由快照到 Redis。
     *
     * @param snapshotJson 路由快照 JSON
     * @param version 快照版本号
     */
    public void warmupSnapshot(String snapshotJson, long version) {
        try {
            long snapshotTtl = buildTtl(CacheConstants.TTL_SNAPSHOT);
            long versionTtl = buildTtl(CacheConstants.TTL_SNAPSHOT);

            // 将完整快照写入 Redis，便于其他节点快速拉取最新配置。
            stringRedisTemplate.opsForValue().set(
                    CacheConstants.KEY_CONFIG_SNAPSHOT,
                    snapshotJson,
                    snapshotTtl,
                    TimeUnit.SECONDS
            );
            // 单独写入版本号，方便轻量级比较与探测是否需要刷新。
            stringRedisTemplate.opsForValue().set(
                    CacheConstants.KEY_CONFIG_VERSION,
                    String.valueOf(version),
                    versionTtl,
                    TimeUnit.SECONDS
            );
        } catch (Exception ex) {
            log.warn("[Redis 路由缓存] 预热快照失败，版本: {}", version, ex);
        }
    }

    /**
     * 获取整份路由快照 JSON。
     */
    public String getSnapshot() {
        try {
            return stringRedisTemplate.opsForValue().get(CacheConstants.KEY_CONFIG_SNAPSHOT);
        } catch (Exception ex) {
            log.warn("[Redis 路由缓存] 读取快照失败", ex);
            return null;
        }
    }

    /**
     * 删除整份快照缓存。
     */
    public void evictSnapshot() {
        try {
            stringRedisTemplate.delete(CacheConstants.KEY_CONFIG_SNAPSHOT);
            stringRedisTemplate.delete(CacheConstants.KEY_CONFIG_VERSION);
        } catch (Exception ex) {
            log.warn("[Redis 路由缓存] 删除快照缓存失败", ex);
        }
    }

    /**
     * 按模型别名删除对应缓存。
     *
     * @param aliasName 模型别名
     */
    public void evictByAlias(String aliasName) {
        try {
            stringRedisTemplate.delete(CacheConstants.KEY_MODEL_REDIRECT_PREFIX + aliasName);
        } catch (Exception ex) {
            log.warn("[Redis 路由缓存] 删除别名缓存失败，aliasName: {}", aliasName, ex);
        }
    }

    /**
     * 按提供商编码删除对应缓存。
     *
     * @param providerCode 提供商编码
     */
    public void evictByProvider(String providerCode) {
        try {
            stringRedisTemplate.delete(CacheConstants.KEY_PROVIDER_PREFIX + providerCode);
        } catch (Exception ex) {
            log.warn("[Redis 路由缓存] 删除提供商缓存失败，providerCode: {}", providerCode, ex);
        }
    }

    /**
     * 设置脏标记。
     *
     * <p>用于标识 Redis 与本地内存快照可能不一致，
     * 便于后续补偿任务重新加载。</p>
     */
    public void markDirty() {
        try {
            long dirtyTtl = buildTtl(CacheConstants.TTL_DIRTY_FLAG);
            stringRedisTemplate.opsForValue().set(
                    CacheConstants.KEY_DIRTY_FLAG,
                    "1",
                    dirtyTtl,
                    TimeUnit.SECONDS
            );
        } catch (Exception ex) {
            log.warn("[Redis 路由缓存] 设置脏标记失败", ex);
        }
    }

    /**
     * 检查当前是否存在脏标记。
     */
    public boolean isDirty() {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(CacheConstants.KEY_DIRTY_FLAG));
        } catch (Exception ex) {
            log.warn("[Redis 路由缓存] 检查脏标记失败", ex);
            return false;
        }
    }

    /**
     * 清除脏标记。
     */
    public void clearDirty() {
        try {
            stringRedisTemplate.delete(CacheConstants.KEY_DIRTY_FLAG);
        } catch (Exception ex) {
            log.warn("[Redis 路由缓存] 清除脏标记失败", ex);
        }
    }

    /**
     * 构建带随机偏移的 TTL，避免大量键同时过期造成缓存雪崩。
     */
    private long buildTtl(long baseTtl) {
        return baseTtl + ThreadLocalRandom.current().nextInt(CacheConstants.TTL_RANDOM_RANGE);
    }
}
