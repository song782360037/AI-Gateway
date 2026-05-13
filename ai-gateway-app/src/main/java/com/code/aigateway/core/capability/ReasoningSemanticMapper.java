package com.code.aigateway.core.capability;

import com.code.aigateway.sdk.model.UnifiedReasoningConfig;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 推理语义映射器
 * <p>
 * 负责在 Anthropic thinking、OpenAI reasoning_effort、Gemini thinking_level/mode 等
 * 多种协议语义之间做近似映射，优先避免静默丢弃。
 * </p>
 */
@Component
public class ReasoningSemanticMapper {

    private static final int LOW_BUDGET = 1024;
    private static final int MEDIUM_BUDGET = 4096;
    // Claude 4+ 推荐的 high 级别预算为 16384，确保深度思考有足够的 token 空间
    private static final int HIGH_BUDGET = 16384;
    private static final String DEFAULT_EFFORT = "high";

    // ===================== OpenAI =====================

    /**
     * 将统一语义映射为 OpenAI reasoning_effort（Chat Completions）。
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
        if (budgetTokens < HIGH_BUDGET) {
            return "medium";
        }
        return "high";
    }

    /**
     * 将统一语义映射为 OpenAI Responses reasoning 对象。
     */
    public Map<String, Object> toOpenAiResponsesReasoning(UnifiedReasoningConfig reasoning) {
        if (reasoning == null || !Boolean.TRUE.equals(reasoning.getEnabled())) {
            return null;
        }
        Map<String, Object> reasoningMap = new LinkedHashMap<>();
        String effort = reasoning.getEffort();
        if (effort != null && !effort.isBlank()) {
            reasoningMap.put("effort", effort);
        } else if (reasoning.getBudgetTokens() != null) {
            reasoningMap.put("effort", budgetToEffort(reasoning.getBudgetTokens()));
        } else {
            reasoningMap.put("effort", DEFAULT_EFFORT);
        }
        if (reasoning.getSummary() != null && !reasoning.getSummary().isBlank()) {
            reasoningMap.put("summary", reasoning.getSummary());
        }
        return reasoningMap;
    }

    // ===================== Anthropic =====================

    /**
     * 将统一语义映射为 Anthropic thinking 配置（支持 enabled 和 adaptive）。
     */
    public Map<String, Object> toAnthropicThinking(UnifiedReasoningConfig reasoning) {
        return toAnthropicThinking(reasoning, false);
    }

    /**
     * 将统一语义映射为 Anthropic thinking 配置（支持 enabled 和 adaptive）。
     * <p>
     * 当 simplified=true 时，仅输出 {"type":"enabled"} 或 {"type":"disabled"}，
     * 适用于第三方 Anthropic 兼容 API（如 MiMo），这些 API 不支持 budget_tokens、summary、output_config 等扩展字段，
     * 收到不认识的字段会返回 400 Param Incorrect。
     * </p>
     *
     * @param reasoning   统一推理配置
     * @param simplified  是否使用简化模式（仅输出 type 字段，不输出扩展字段）
     */
    public Map<String, Object> toAnthropicThinking(UnifiedReasoningConfig reasoning, boolean simplified) {
        if (reasoning == null) {
            return null;
        }
        Map<String, Object> thinking = new LinkedHashMap<>();

        // 简化模式：仅输出 type 字段，不输出 budget_tokens、summary、output_config
        // 适用于 MiMo 等第三方 Anthropic 兼容 API，这些 API 不支持扩展字段
        // 注意：简化模式下 enabled=false 时不发送 thinking 字段，与完整模式保持语义一致（不传即不启用）
        if (simplified) {
            if (!Boolean.TRUE.equals(reasoning.getEnabled())) {
                return null;
            }
            thinking.put("type", "enabled");
            return thinking;
        }

        // 完整模式下，enabled=false 不发送 thinking 参数（与官方 API 语义一致：不传即不启用）
        if (!Boolean.TRUE.equals(reasoning.getEnabled())) {
            return null;
        }

        // 完整模式：输出所有 Anthropic 原生 thinking 字段
        // 有 effort 时优先使用 adaptive 模式（Claude 4.6+），budgetTokens 仅作为 enabled 模式的 fallback
        if (reasoning.getEffort() != null && !reasoning.getEffort().isBlank()) {
            thinking.put("type", "adaptive");
            thinking.put("output_config", Map.of("effort", reasoning.getEffort()));
        } else {
            thinking.put("type", "enabled");
            thinking.put("budget_tokens", toAnthropicBudgetTokens(reasoning));
        }

        // 传递 summary 参数，确保思考内容可见（Anthropic 默认需要 summary 才能返回思考内容）
        if (reasoning.getSummary() != null && !reasoning.getSummary().isBlank()) {
            thinking.put("summary", reasoning.getSummary());
        } else {
            thinking.put("summary", "auto");
        }
        return thinking;
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

    // ===================== Gemini =====================

    /**
     * 将统一语义映射为 Gemini thinking_config（thinking_level 或 thinking_mode）。
     * <p>
     * 注意：同时输出 thinking_level 和 thinking_mode 两个不同名字段，
     * 是因为 Gemini 3.0 仅识别 thinking_level，Gemini 3.1 仅识别 thinking_mode。
     * 经验证，各版本对未识别字段采用忽略策略（不会报错）。
     * 若后续 Gemini API 版本变更此行为，需重新评估此方案。
     * </p>
     */
    public Map<String, Object> toGeminiThinkingConfig(UnifiedReasoningConfig reasoning) {
        if (reasoning == null || !Boolean.TRUE.equals(reasoning.getEnabled())) {
            return null;
        }
        Map<String, Object> config = new LinkedHashMap<>();

        // 有 budgetTokens 时使用旧版 thinking_budget
        if (reasoning.getBudgetTokens() != null && reasoning.getBudgetTokens() > 0
                && reasoning.getEffort() == null) {
            config.put("thinking_budget", reasoning.getBudgetTokens());
            // 同时设置 include_thoughts 使思考内容可见
            config.put("include_thoughts", true);
        } else {
            // 使用 thinking_level（Gemini 3.0）或 thinking_mode（Gemini 3.1）
            String level = reasoning.getEffort() != null && !reasoning.getEffort().isBlank()
                    ? reasoning.getEffort()
                    : DEFAULT_EFFORT;
            // 同时输出两种字段名：Gemini 3.0 仅识别 thinking_level，3.1 仅识别 thinking_mode
            // 各版本对未知字段采用忽略策略，不会报错（已验证）
            config.put("thinking_level", level);
            config.put("thinking_mode", level);
        }
        return config;
    }

    // ===================== 内部工具方法 =====================

    private String budgetToEffort(int budgetTokens) {
        if (budgetTokens <= LOW_BUDGET) return "low";
        if (budgetTokens < HIGH_BUDGET) return "medium";
        return "high";
    }
}
