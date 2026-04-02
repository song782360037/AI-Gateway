package com.code.aigateway.core.capability;

import com.code.aigateway.core.model.UnifiedReasoningConfig;
import org.springframework.stereotype.Component;

/**
 * 推理语义映射器
 * <p>
 * 负责在 Anthropic thinking 与 OpenAI reasoning_effort 之间做近似语义映射。
 * 首版仅覆盖最小可用规则，优先避免静默丢弃。
 * </p>
 */
@Component
public class ReasoningSemanticMapper {

    private static final int LOW_BUDGET = 1024;
    private static final int MEDIUM_BUDGET = 4096;
    private static final int HIGH_BUDGET = 8192;
    private static final String DEFAULT_EFFORT = "medium";

    /**
     * 将统一语义映射为 OpenAI reasoning_effort。
     */
    public String toOpenAiEffort(UnifiedReasoningConfig reasoning) {
        if (reasoning == null || !Boolean.TRUE.equals(reasoning.getEnabled())) {
            return null;
        }
        if (reasoning.getEffort() != null && !reasoning.getEffort().isBlank()) {
            return reasoning.getEffort();
        }
        Integer budgetTokens = reasoning.getBudgetTokens();
        if (budgetTokens == null) {
            return DEFAULT_EFFORT;
        }
        if (budgetTokens <= LOW_BUDGET) {
            return "low";
        }
        if (budgetTokens <= HIGH_BUDGET) {
            return "medium";
        }
        return "high";
    }

    /**
     * 将统一语义映射为 Anthropic thinking budget。
     */
    public Integer toAnthropicBudgetTokens(UnifiedReasoningConfig reasoning) {
        if (reasoning == null || !Boolean.TRUE.equals(reasoning.getEnabled())) {
            return null;
        }
        if (reasoning.getBudgetTokens() != null) {
            return reasoning.getBudgetTokens();
        }
        String effort = reasoning.getEffort();
        if (effort == null || effort.isBlank()) {
            return MEDIUM_BUDGET;
        }
        return switch (effort) {
            case "low" -> LOW_BUDGET;
            case "high" -> HIGH_BUDGET;
            default -> MEDIUM_BUDGET;
        };
    }
}
