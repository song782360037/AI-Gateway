package com.code.aigateway.core.capability;

import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultCapabilityCheckerTest {

    private final DefaultCapabilityChecker capabilityChecker = new DefaultCapabilityChecker();

    @Test
    void validate_toolsRequested_throwsCapabilityNotSupported() {
        UnifiedTool tool = new UnifiedTool();
        tool.setName("search_docs");
        tool.setType("function");
        tool.setInputSchema(Map.of("type", "object"));

        UnifiedRequest request = new UnifiedRequest();
        request.setTools(List.of(tool));

        GatewayException exception = org.junit.jupiter.api.Assertions.assertThrows(
                GatewayException.class,
                () -> capabilityChecker.validate(request, openAiRoute())
        );

        assertEquals(ErrorCode.CAPABILITY_NOT_SUPPORTED, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("tools are not supported"));
    }

    @Test
    void validate_toolHistoryRequested_throwsCapabilityNotSupported() {
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

        GatewayException exception = org.junit.jupiter.api.Assertions.assertThrows(
                GatewayException.class,
                () -> capabilityChecker.validate(request, openAiRoute())
        );

        assertEquals(ErrorCode.CAPABILITY_NOT_SUPPORTED, exception.getErrorCode());
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

    private RouteResult openAiRoute() {
        return RouteResult.builder()
                .providerType(ProviderType.OPENAI)
                .providerName("openai")
                .targetModel("gpt-5.4")
                .build();
    }
}
