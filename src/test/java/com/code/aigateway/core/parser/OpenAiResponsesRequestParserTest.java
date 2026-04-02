package com.code.aigateway.core.parser;

import com.code.aigateway.api.request.OpenAiResponsesRequest;
import com.code.aigateway.core.model.UnifiedRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OpenAiResponsesRequestParserTest {

    private final OpenAiResponsesRequestParser parser = new OpenAiResponsesRequestParser();

    @Test
    void parse_mapsReasoningToGenerationConfigAndMetadata() {
        OpenAiResponsesRequest request = new OpenAiResponsesRequest();
        request.setModel("o3");
        request.setInstructions("你是一个助手");
        request.setInput(List.of(message("user", "你好")));
        request.setReasoning(Map.of("effort", "medium"));

        UnifiedRequest unifiedRequest = parser.parse(request);

        assertNotNull(unifiedRequest.getGenerationConfig());
        assertEquals("medium", unifiedRequest.getGenerationConfig().getReasoningEffort());
        assertEquals("medium", ((Map<?, ?>) unifiedRequest.getMetadata().get("openai_reasoning")).get("effort"));
    }

    @Test
    void parse_stopString_mapsToStopSequences() {
        OpenAiResponsesRequest request = new OpenAiResponsesRequest();
        request.setModel("o3");
        request.setInput(List.of(message("user", "你好")));
        request.setStop("DONE");

        UnifiedRequest unifiedRequest = parser.parse(request);

        assertNotNull(unifiedRequest.getGenerationConfig());
        assertEquals(List.of("DONE"), unifiedRequest.getGenerationConfig().getStopSequences());
    }

    private OpenAiResponsesRequest.InputItem message(String role, String content) {
        OpenAiResponsesRequest.InputItem item = new OpenAiResponsesRequest.InputItem();
        item.setType("message");
        item.setRole(role);
        item.setContent(content);
        return item;
    }
}
