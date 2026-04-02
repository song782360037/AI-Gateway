package com.code.aigateway.core.model;

import lombok.Data;

/**
 * 统一的 reasoning / thinking 语义配置
 * <p>
 * 用于在不同协议之间表达一致的推理语义，避免在统一层暴露
 * 过多 provider-specific 字段。
 * </p>
 */
@Data
public class UnifiedReasoningConfig {

    /**
     * 是否启用推理/思考能力
     */
    private Boolean enabled;

    /**
     * 推理预算 token 数
     * <p>
     * 主要对 Anthropic thinking 语义友好。
     * </p>
     */
    private Integer budgetTokens;

    /**
     * 推理强度
     * <p>
     * 主要对 OpenAI reasoning_effort 语义友好。
     * </p>
     */
    private String effort;
}
