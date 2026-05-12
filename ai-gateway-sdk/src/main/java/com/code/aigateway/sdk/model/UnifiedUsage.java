package com.code.aigateway.sdk.model;

import lombok.Data;

/**
 * 统一的 Token 使用统计
 */
@Data
public class UnifiedUsage {

    /** 输入 Token 数量 */
    private Integer inputTokens;

    /** 输入命中缓存（读取）的 Token 数量 */
    private Integer cachedInputTokens;

    /** 输入写入缓存的 Token 数量（Anthropic cache_creation_input_tokens） */
    private Integer cacheCreationInputTokens;

    /** 输出 Token 数量 */
    private Integer outputTokens;

    /** 总 Token 数量 */
    private Integer totalTokens;
}
