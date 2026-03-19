package com.code.aigateway.core.model;

import lombok.Data;

import java.util.List;

/**
 * 统一的生成配置
 */
@Data
public class UnifiedGenerationConfig {
    private Double temperature;
    private Double topP;
    private Integer maxOutputTokens;
    private List<String> stopSequences;
    private Boolean parallelToolCalls;
}
