package com.code.aigateway.core.capability;

import com.code.aigateway.sdk.model.UnifiedReasoningConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReasoningSemanticMapperTest {

    private final ReasoningSemanticMapper mapper = new ReasoningSemanticMapper();

    // ===================== toOpenAiEffort =====================

    @Nested
    @DisplayName("toOpenAiEffort")
    class ToOpenAiEffort {

        @Test
        @DisplayName("null reasoning → null")
        void nullReasoning_returnsNull() {
            assertNull(mapper.toOpenAiEffort(null));
        }

        @Test
        @DisplayName("disabled → null")
        void disabled_returnsNull() {
            UnifiedReasoningConfig r = new UnifiedReasoningConfig();
            r.setEnabled(false);
            assertNull(mapper.toOpenAiEffort(r));
        }

        @Test
        @DisplayName("effort 直接透传")
        void explicitEffort_returnsEffort() {
            UnifiedReasoningConfig r = new UnifiedReasoningConfig();
            r.setEnabled(true);
            r.setEffort("low");
            assertThat(mapper.toOpenAiEffort(r)).isEqualTo("low");
        }

        @Test
        @DisplayName("budgetTokens ≤ 1024 → low")
        void budgetTokensLow_mapsToLow() {
            UnifiedReasoningConfig r = new UnifiedReasoningConfig();
            r.setEnabled(true);
            r.setBudgetTokens(512);
            assertThat(mapper.toOpenAiEffort(r)).isEqualTo("low");
        }

        @Test
        @DisplayName("budgetTokens 在 (1024, 16384] → medium")
        void budgetTokensMedium_mapsToMedium() {
            UnifiedReasoningConfig r = new UnifiedReasoningConfig();
            r.setEnabled(true);
            r.setBudgetTokens(4096);
            assertThat(mapper.toOpenAiEffort(r)).isEqualTo("medium");
        }

        @Test
        @DisplayName("budgetTokens > 16384 → high")
        void budgetTokensHigh_mapsToHigh() {
            UnifiedReasoningConfig r = new UnifiedReasoningConfig();
            r.setEnabled(true);
            r.setBudgetTokens(20000);
            assertThat(mapper.toOpenAiEffort(r)).isEqualTo("high");
        }

        @Test
        @DisplayName("无 effort 无 budget → 默认 high")
        void noEffortNoBudget_defaultsToHigh() {
            UnifiedReasoningConfig r = new UnifiedReasoningConfig();
            r.setEnabled(true);
            assertThat(mapper.toOpenAiEffort(r)).isEqualTo("high");
        }
    }

    // ===================== toAnthropicThinking =====================

    @Nested
    @DisplayName("toAnthropicThinking")
    class ToAnthropicThinking {

        @Test
        @DisplayName("null reasoning → null")
        void nullReasoning_returnsNull() {
            assertNull(mapper.toAnthropicThinking(null));
        }

        @Test
        @DisplayName("disabled → null")
        void disabled_returnsNull() {
            UnifiedReasoningConfig r = new UnifiedReasoningConfig();
            r.setEnabled(false);
            assertNull(mapper.toAnthropicThinking(r));
        }

        @Test
        @DisplayName("enabled + budgetTokens → enabled 模式")
        void budgetTokens_usesEnabledType() {
            UnifiedReasoningConfig r = new UnifiedReasoningConfig();
            r.setEnabled(true);
            r.setBudgetTokens(4096);

            Map<String, Object> thinking = mapper.toAnthropicThinking(r);
            assertThat(thinking).isNotNull();
            assertThat(thinking.get("type")).isEqualTo("enabled");
            assertThat(thinking.get("budget_tokens")).isEqualTo(4096);
        }

        @Test
        @DisplayName("effort 无 budget → adaptive 模式")
        void effortWithoutBudget_usesAdaptive() {
            UnifiedReasoningConfig r = new UnifiedReasoningConfig();
            r.setEnabled(true);
            r.setEffort("high");

            Map<String, Object> thinking = mapper.toAnthropicThinking(r);
            assertThat(thinking).isNotNull();
            assertThat(thinking.get("type")).isEqualTo("adaptive");
            @SuppressWarnings("unchecked")
            Map<String, Object> outputConfig = (Map<String, Object>) thinking.get("output_config");
            assertThat(outputConfig).isNotNull();
            assertThat(outputConfig.get("effort")).isEqualTo("high");
        }

        @Test
        @DisplayName("effort + budget → enabled 模式（budget 优先）")
        void effortWithBudget_usesEnabled() {
            UnifiedReasoningConfig r = new UnifiedReasoningConfig();
            r.setEnabled(true);
            r.setEffort("low");
            r.setBudgetTokens(8192);

            Map<String, Object> thinking = mapper.toAnthropicThinking(r);
            assertThat(thinking).isNotNull();
            assertThat(thinking.get("type")).isEqualTo("enabled");
        }
    }

    // ===================== toAnthropicBudgetTokens =====================

    @Nested
    @DisplayName("toAnthropicBudgetTokens")
    class ToAnthropicBudgetTokens {

        @Test
        @DisplayName("null → null")
        void nullReasoning_returnsNull() {
            assertNull(mapper.toAnthropicBudgetTokens(null));
        }

        @Test
        @DisplayName("disabled → null")
        void disabled_returnsNull() {
            UnifiedReasoningConfig r = new UnifiedReasoningConfig();
            r.setEnabled(false);
            assertNull(mapper.toAnthropicBudgetTokens(r));
        }

        @Test
        @DisplayName("budgetTokens 直接返回")
        void explicitBudget_returnsBudget() {
            UnifiedReasoningConfig r = new UnifiedReasoningConfig();
            r.setEnabled(true);
            r.setBudgetTokens(8192);
            assertThat(mapper.toAnthropicBudgetTokens(r)).isEqualTo(8192);
        }

        @Test
        @DisplayName("effort=low → 1024")
        void effortLow_mapsTo1024() {
            UnifiedReasoningConfig r = new UnifiedReasoningConfig();
            r.setEnabled(true);
            r.setEffort("low");
            assertThat(mapper.toAnthropicBudgetTokens(r)).isEqualTo(1024);
        }

        @Test
        @DisplayName("effort=high → 16384")
        void effortHigh_mapsTo16384() {
            UnifiedReasoningConfig r = new UnifiedReasoningConfig();
            r.setEnabled(true);
            r.setEffort("high");
            assertThat(mapper.toAnthropicBudgetTokens(r)).isEqualTo(16384);
        }

        @Test
        @DisplayName("effort=medium → 4096")
        void effortMedium_mapsTo4096() {
            UnifiedReasoningConfig r = new UnifiedReasoningConfig();
            r.setEnabled(true);
            r.setEffort("medium");
            assertThat(mapper.toAnthropicBudgetTokens(r)).isEqualTo(4096);
        }

        @Test
        @DisplayName("无 effort 无 budget → 默认 4096")
        void noEffortNoBudget_defaultsTo4096() {
            UnifiedReasoningConfig r = new UnifiedReasoningConfig();
            r.setEnabled(true);
            assertThat(mapper.toAnthropicBudgetTokens(r)).isEqualTo(4096);
        }
    }

    // ===================== toOpenAiResponsesReasoning =====================

    @Nested
    @DisplayName("toOpenAiResponsesReasoning")
    class ToOpenAiResponsesReasoning {

        @Test
        @DisplayName("null → null")
        void nullReasoning_returnsNull() {
            assertNull(mapper.toOpenAiResponsesReasoning(null));
        }

        @Test
        @DisplayName("effort + summary")
        void effortAndSummary() {
            UnifiedReasoningConfig r = new UnifiedReasoningConfig();
            r.setEnabled(true);
            r.setEffort("high");
            r.setSummary("detailed");

            Map<String, Object> result = mapper.toOpenAiResponsesReasoning(r);
            assertThat(result).isNotNull();
            assertThat(result.get("effort")).isEqualTo("high");
            assertThat(result.get("summary")).isEqualTo("detailed");
        }

        @Test
        @DisplayName("budgetTokens 映射到 effort")
        void budgetTokens_mapsToEffort() {
            UnifiedReasoningConfig r = new UnifiedReasoningConfig();
            r.setEnabled(true);
            r.setBudgetTokens(512);

            Map<String, Object> result = mapper.toOpenAiResponsesReasoning(r);
            assertThat(result).isNotNull();
            assertThat(result.get("effort")).isEqualTo("low");
        }

        @Test
        @DisplayName("无配置 → 默认 high")
        void noConfig_defaultsToHigh() {
            UnifiedReasoningConfig r = new UnifiedReasoningConfig();
            r.setEnabled(true);

            Map<String, Object> result = mapper.toOpenAiResponsesReasoning(r);
            assertThat(result).isNotNull();
            assertThat(result.get("effort")).isEqualTo("high");
        }
    }

    // ===================== toGeminiThinkingConfig =====================

    @Nested
    @DisplayName("toGeminiThinkingConfig")
    class ToGeminiThinkingConfig {

        @Test
        @DisplayName("null → null")
        void nullReasoning_returnsNull() {
            assertNull(mapper.toGeminiThinkingConfig(null));
        }

        @Test
        @DisplayName("effort → thinking_mode")
        void effort_mapsToThinkingMode() {
            UnifiedReasoningConfig r = new UnifiedReasoningConfig();
            r.setEnabled(true);
            r.setEffort("high");

            Map<String, Object> config = mapper.toGeminiThinkingConfig(r);
            assertThat(config).isNotNull();
            assertThat(config.get("thinking_mode")).isEqualTo("high");
            assertThat(config).doesNotContainKey("thinking_budget");
        }

        @Test
        @DisplayName("budgetTokens 无 effort → thinking_budget + include_thoughts")
        void budgetTokens_usesThinkingBudget() {
            UnifiedReasoningConfig r = new UnifiedReasoningConfig();
            r.setEnabled(true);
            r.setBudgetTokens(4096);

            Map<String, Object> config = mapper.toGeminiThinkingConfig(r);
            assertThat(config).isNotNull();
            assertThat(config.get("thinking_budget")).isEqualTo(4096);
            assertThat(config.get("include_thoughts")).isEqualTo(true);
        }

        @Test
        @DisplayName("无 effort 无 budget → 默认 high thinking_mode")
        void noConfig_defaultsToHighMode() {
            UnifiedReasoningConfig r = new UnifiedReasoningConfig();
            r.setEnabled(true);

            Map<String, Object> config = mapper.toGeminiThinkingConfig(r);
            assertThat(config).isNotNull();
            assertThat(config.get("thinking_mode")).isEqualTo("high");
        }

        @Test
        @DisplayName("disabled → null")
        void disabled_returnsNull() {
            UnifiedReasoningConfig r = new UnifiedReasoningConfig();
            r.setEnabled(false);
            assertNull(mapper.toGeminiThinkingConfig(r));
        }
    }
}
