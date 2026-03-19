package com.code.aigateway.core.model;

import lombok.Data;

/**
 * 统一的使用情况
 */
@Data
public class UnifiedUsage {
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
}
