package com.code.aigateway.core.capability;

import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.model.UnifiedGenerationConfig;
import com.code.aigateway.core.model.UnifiedMessage;
import com.code.aigateway.core.model.UnifiedPart;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedTool;
import com.code.aigateway.core.model.UnifiedToolCall;
import com.code.aigateway.core.router.RouteResult;
import com.code.aigateway.provider.ProviderType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultCapabilityCheckerTest {

    private final DefaultCapabilityChecker capabilityChecker = new DefaultCapabilityChecker();

    @Test
    void validate_toolsRequested_passesThrough() {
        // tools 请求现在由 ProviderClient 负责转发，不再被拦截
        UnifiedTool tool = new UnifiedTool();
        tool.setName("search_docs");
        tool.setType("function");
        tool.setInputSchema(Map.of("type", "object"));

        UnifiedRequest request = new UnifiedRequest();
        request.setTools(List.of(tool));

        assertDoesNotThrow(() -> capabilityChecker.validate(request, openAiRoute()));
    }

    @Test
    void validate_toolHistoryRequested_passesThrough() {
        // tool 角色消息和 assistant 历史 tool_calls 由 ProviderClient 转发
        UnifiedToolCall toolCall = new UnifiedToolCall();
        toolCall.setId("call_1");
        toolCall.setType("function");
        toolCall.setToolName("search_docs");
        toolCall.setArgumentsJson("{}");

        UnifiedPart textPart = new UnifiedPart();
        textPart.setType("text");
        textPart.setText("工具结果");

        UnifiedMessage assistantMessage = new UnifiedMessage();
        assistantMessage.setRole("assistant");
        assistantMessage.setToolCalls(List.of(toolCall));

        UnifiedMessage toolMessage = new UnifiedMessage();
        toolMessage.setRole("tool");
        toolMessage.setToolCallId("call_1");
        toolMessage.setParts(List.of(textPart));

        UnifiedRequest request = new UnifiedRequest();
        request.setMessages(List.of(assistantMessage, toolMessage));

        assertDoesNotThrow(() -> capabilityChecker.validate(request, openAiRoute()));
    }

    @Test
    void validate_plainTextRequest_doesNotThrow() {
        UnifiedPart textPart = new UnifiedPart();
        textPart.setType("text");
        textPart.setText("你好");

        UnifiedMessage userMessage = new UnifiedMessage();
        userMessage.setRole("user");
        userMessage.setParts(List.of(textPart));

        UnifiedRequest request = new UnifiedRequest();
        request.setMessages(List.of(userMessage));

        assertDoesNotThrow(() -> capabilityChecker.validate(request, openAiRoute()));
    }

    @Test
    void validate_anthropicThinkingOnOpenAi_passesThrough() {
        UnifiedGenerationConfig config = new UnifiedGenerationConfig();
        config.setThinkingEnabled(true);
        config.setThinkingBudgetTokens(1024);

        UnifiedRequest request = new UnifiedRequest();
        request.setGenerationConfig(config);

        assertDoesNotThrow(() -> capabilityChecker.validate(request, openAiRoute()));
    }

    @Test
    void validate_anthropicThinkingOnAnthropic_passes() {
        UnifiedGenerationConfig config = new UnifiedGenerationConfig();
        config.setThinkingEnabled(true);
        config.setThinkingBudgetTokens(1024);

        UnifiedRequest request = new UnifiedRequest();
        request.setGenerationConfig(config);

        assertDoesNotThrow(() -> capabilityChecker.validate(request, anthropicRoute()));
    }

    @Test
    void validate_reasoningEffortOnAnthropic_passesThrough() {
        UnifiedGenerationConfig config = new UnifiedGenerationConfig();
        config.setReasoningEffort("high");

        UnifiedRequest request = new UnifiedRequest();
        request.setGenerationConfig(config);

        assertDoesNotThrow(() -> capabilityChecker.validate(request, anthropicRoute()));
    }

    @Test
    void validate_reasoningEffortOnOpenAiResponses_passes() {
        UnifiedGenerationConfig config = new UnifiedGenerationConfig();
        config.setReasoningEffort("medium");

        UnifiedRequest request = new UnifiedRequest();
        request.setGenerationConfig(config);

        assertDoesNotThrow(() -> capabilityChecker.validate(request, openAiResponsesRoute()));
    }

    private RouteResult openAiRoute() {
        return RouteResult.builder()
                .providerType(ProviderType.OPENAI)
                .providerName("openai")
                .targetModel("gpt-5.4")
                .build();
    }

    private RouteResult anthropicRoute() {
        return RouteResult.builder()
                .providerType(ProviderType.ANTHROPIC)
                .providerName("anthropic")
                .targetModel("claude-3-7-sonnet")
                .build();
    }

    private RouteResult openAiResponsesRoute() {
        return RouteResult.builder()
                .providerType(ProviderType.OPENAI_RESPONSES)
                .providerName("openai-responses")
                .targetModel("o3")
                .build();
    }
}
