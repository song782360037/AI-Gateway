package com.code.aigateway.core.runtime;

/**
 * 运行时配置缓存常量。
 * <p>
 * 统一维护 Redis Key 前缀和 TTL，避免业务代码中散落硬编码值。
 * </p>
 */
public final class CacheConstants {

    private CacheConstants() {
    }

    // key 前缀
    public static final String KEY_CONFIG_SNAPSHOT = "gateway:config:snapshot";
    public static final String KEY_MODEL_REDIRECT_PREFIX = "gateway:model-redirect:";
    public static final String KEY_PROVIDER_PREFIX = "gateway:provider:";
    public static final String KEY_CONFIG_VERSION = "gateway:config:version";
    public static final String KEY_RELOAD_LOCK = "gateway:config:reload:lock";
    public static final String KEY_DIRTY_FLAG = "gateway:config:dirty";
    public static final String KEY_DASHBOARD_OVERVIEW = "gateway:dashboard:overview";
    public static final String KEY_DASHBOARD_MODEL_RANK = "gateway:dashboard:model-rank";
    public static final String KEY_DASHBOARD_RECENT_REQUESTS = "gateway:dashboard:recent-requests";

    // TTL（秒）
    public static final long TTL_SNAPSHOT = 24 * 60 * 60L;        // 24 小时
    public static final long TTL_MODEL_REDIRECT = 30 * 60L;       // 30 分钟（基础值，实际写入时加随机偏移）
    public static final long TTL_PROVIDER = 30 * 60L;             // 30 分钟
    public static final long TTL_RELOAD_LOCK = 30L;               // 30 秒
    public static final long TTL_DIRTY_FLAG = 10 * 60L;           // 10 分钟
    public static final long TTL_DASHBOARD = 2 * 60L;             // 2 分钟

    // TTL 随机偏移范围（秒）
    public static final int TTL_RANDOM_RANGE = 5 * 60;            // 0-5 分钟随机偏移
}
