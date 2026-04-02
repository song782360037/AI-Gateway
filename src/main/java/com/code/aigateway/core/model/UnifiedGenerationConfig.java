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
     * Top-K 采样参数
     * <p>
     * Anthropic 等协议会使用该字段控制候选 token 数量。
     * </p>
     */
    private Integer topK;

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

    /**
     * 统一 reasoning / thinking 语义
     */
    private UnifiedReasoningConfig reasoning;

    /**
     * OpenAI reasoning_effort 参数
     * <p>
     * 兼容存量调用，内部会同步到 reasoning.effort。
     * </p>
     */
    private String reasoningEffort;

    /**
     * Anthropic thinking 开关
     * <p>
     * 兼容存量调用，内部会同步到 reasoning.enabled。
     * </p>
     */
    private Boolean thinkingEnabled;

    /**
     * Anthropic thinking 预算 token 数
     * <p>
     * 兼容存量调用，内部会同步到 reasoning.budgetTokens。
     * </p>
     */
    private Integer thinkingBudgetTokens;

    public void setReasoning(UnifiedReasoningConfig reasoning) {
        this.reasoning = reasoning;
        if (reasoning == null) {
            this.reasoningEffort = null;
            this.thinkingEnabled = null;
            this.thinkingBudgetTokens = null;
            return;
        }
        this.reasoningEffort = reasoning.getEffort();
        this.thinkingEnabled = reasoning.getEnabled();
        this.thinkingBudgetTokens = reasoning.getBudgetTokens();
    }

    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
        ensureReasoning().setEffort(reasoningEffort);
        if (reasoningEffort != null && !reasoningEffort.isBlank() && ensureReasoning().getEnabled() == null) {
            ensureReasoning().setEnabled(true);
        }
    }

    public void setThinkingEnabled(Boolean thinkingEnabled) {
        this.thinkingEnabled = thinkingEnabled;
        ensureReasoning().setEnabled(thinkingEnabled);
    }

    public void setThinkingBudgetTokens(Integer thinkingBudgetTokens) {
        this.thinkingBudgetTokens = thinkingBudgetTokens;
        ensureReasoning().setBudgetTokens(thinkingBudgetTokens);
        if (thinkingBudgetTokens != null && ensureReasoning().getEnabled() == null) {
            ensureReasoning().setEnabled(true);
        }
    }

    private UnifiedReasoningConfig ensureReasoning() {
        if (reasoning == null) {
            reasoning = new UnifiedReasoningConfig();
        }
        return reasoning;
    }
}
