package com.code.aigateway.core.parser;

import com.code.aigateway.api.request.AnthropicMessagesRequest;
import com.code.aigateway.core.model.UnifiedPart;
import com.code.aigateway.core.model.UnifiedRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AnthropicRequestParserTest {

    private final AnthropicRequestParser parser = new AnthropicRequestParser(new ObjectMapper());

    @Test
    void parse_mapsThinkingToGenerationConfig() {
        AnthropicMessagesRequest request = new AnthropicMessagesRequest();
        request.setModel("claude-3-7-sonnet");
        request.setMaxTokens(1024);
        request.setMessages(List.of(message("user", "你好")));

        AnthropicMessagesRequest.Thinking thinking = new AnthropicMessagesRequest.Thinking();
        thinking.setType("enabled");
        thinking.setBudgetTokens(2048);
        request.setThinking(thinking);

        UnifiedRequest unifiedRequest = parser.parse(request);

        assertNotNull(unifiedRequest.getGenerationConfig());
        assertEquals(Boolean.TRUE, unifiedRequest.getGenerationConfig().getThinkingEnabled());
        assertEquals(2048, unifiedRequest.getGenerationConfig().getThinkingBudgetTokens());
    }

    @Test
    void parse_mapsThinkingBlocksToUnifiedParts() {
        AnthropicMessagesRequest request = new AnthropicMessagesRequest();
        request.setModel("claude-3-7-sonnet");
        request.setMaxTokens(1024);
        request.setMessages(List.of(assistantMessageWithThinking()));

        UnifiedRequest unifiedRequest = parser.parse(request);

        assertEquals(1, unifiedRequest.getMessages().size());
        assertEquals("assistant", unifiedRequest.getMessages().getFirst().getRole());
        assertEquals(1, unifiedRequest.getMessages().getFirst().getParts().size());

        UnifiedPart part = unifiedRequest.getMessages().getFirst().getParts().getFirst();
        assertEquals("thinking", part.getType());
        assertEquals("先分析一下问题", part.getText());
        assertEquals("sig_1", part.getAttributes().get("signature"));
        assertEquals("thinking", part.getAttributes().get("anthropic_type"));
    }

    private AnthropicMessagesRequest.Message message(String role, String content) {
        AnthropicMessagesRequest.Message message = new AnthropicMessagesRequest.Message();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private AnthropicMessagesRequest.Message assistantMessageWithThinking() {
        AnthropicMessagesRequest.Message message = new AnthropicMessagesRequest.Message();
        message.setRole("assistant");
        message.setContent(List.of(Map.of(
                "type", "thinking",
                "thinking", "先分析一下问题",
                "signature", "sig_1"
        )));
        return message;
    }
}
