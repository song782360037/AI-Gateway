package com.code.aigateway.sdk.model;

import lombok.Data;

/**
 * 统一的 reasoning/thinking 语义配置
 */
@Data
public class UnifiedReasoningConfig {

    /** 是否启用推理/思考能力 */
    private Boolean enabled;

    /** 推理预算 token 数 */
    private Integer budgetTokens;

    /** 推理强度：low / medium / high */
    private String effort;

    /** 推理内容可见性：auto / concise / detailed */
    private String summary;
}
