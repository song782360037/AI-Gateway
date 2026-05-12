package com.code.aigateway.sdk.model;

import lombok.Data;

/**
 * 统一的 Token 使用统计
 */
@Data
public class UnifiedUsage {

    /** 输入 Token 数量 */
    private Integer inputTokens;

    /** 输入命中缓存的 Token 数量 */
    private Integer cachedInputTokens;

    /** 输出 Token 数量 */
    private Integer outputTokens;

    /** 总 Token 数量 */
    private Integer totalTokens;
}
