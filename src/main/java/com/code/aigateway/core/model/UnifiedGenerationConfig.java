package com.code.aigateway.core.model;

import lombok.Data;

import java.util.List;

/**
 * 统一的生成配置
 * <p>
 * 控制文本生成的各种参数，如温度、采样策略、长度限制等。
 * </p>
 *
 * @author sst
 */
@Data
public class UnifiedGenerationConfig {

    /**
     * 温度参数（0-2）
     * <p>
     * 值越高，输出越随机；值越低，输出越确定
     * </p>
     */
    private Double temperature;

    /**
     * Top-P 采样参数（0-1）
     * <p>
     * 核采样，控制从概率累计达到 P 的词中选择
     * </p>
     */
    private Double topP;

    /**
     * 最大输出 token 数
     */
    private Integer maxOutputTokens;

    /**
     * 停止序列列表
     * <p>
     * 遇到这些序列时停止生成
     * </p>
     */
    private List<String> stopSequences;

    /**
     * 是否并行调用多个工具
     */
    private Boolean parallelToolCalls;
}
