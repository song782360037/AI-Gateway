package com.code.aigateway.sdk.model;

import lombok.Data;

import java.util.List;

/**
 * 统一的生成配置
 */
@Data
public class UnifiedGenerationConfig {

    /** 温度参数（0-2） */
    private Double temperature;

    /** Top-P 采样参数 */
    private Double topP;

    /** Top-K 采样参数 */
    private Integer topK;

    /** 最大输出 token 数 */
    private Integer maxOutputTokens;

    /** 停止序列 */
    private List<String> stopSequences;

    /** 是否并行调用多个工具 */
    private Boolean parallelToolCalls;

    /** reasoning/thinking 配置 */
    private UnifiedReasoningConfig reasoning;

    /** OpenAI reasoning_effort 参数（兼容存量调用，内部同步到 reasoning.effort） */
    private String reasoningEffort;

    /** Anthropic thinking 开关（兼容存量调用，内部同步到 reasoning.enabled） */
    private Boolean thinkingEnabled;

    /** Anthropic thinking 预算 token 数（兼容存量调用，内部同步到 reasoning.budgetTokens） */
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
