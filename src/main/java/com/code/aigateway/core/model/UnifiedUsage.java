package com.code.aigateway.core.model;

import lombok.Data;

/**
 * 统一的 Token 使用统计
 * <p>
 * 记录请求和响应中消耗的 Token 数量。
 * </p>
 *
 * @author sst
 */
@Data
public class UnifiedUsage {

    /**
     * 输入 Token 数量
     */
    private Integer inputTokens;

    /**
     * 输出 Token 数量
     */
    private Integer outputTokens;

    /**
     * 总 Token 数量
     */
    private Integer totalTokens;
}
