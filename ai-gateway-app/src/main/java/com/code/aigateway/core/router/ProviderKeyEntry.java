package com.code.aigateway.core.router;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 提供商 API Key 运行时条目
 *
 * <p>存储在路由快照中的 Key 信息，apiKey 为解密后的明文。
 * 序列化时排除 apiKey 以防止泄露到 Redis/日志。</p>
 */
public record ProviderKeyEntry(
        /** Key 记录 ID */
        long id,
        /** 解密后的明文 API Key，序列化时排除 */
        @JsonIgnore String apiKey,
        /** API Key 脱敏标识（前8后4格式），用于日志记录 */
        String apiKeyPrefix,
        /** 权重（用于加权随机策略） */
        int weight,
        /** 排序号（用于 FALLBACK 策略） */
        int sortOrder
) {}
