package com.code.aigateway.core.encoder;

import com.code.aigateway.api.response.AnthropicMessagesResponse;
import com.code.aigateway.core.model.UnifiedOutput;
import com.code.aigateway.core.model.UnifiedPart;
import com.code.aigateway.core.model.UnifiedResponse;
import com.code.aigateway.core.model.UnifiedToolCall;
import com.code.aigateway.core.model.UnifiedUsage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnthropicResponseEncoderTest {

    private final AnthropicResponseEncoder encoder = new AnthropicResponseEncoder(new ObjectMapper());

    @Test
    void encode_thinkingTextAndToolCall_acrossSingleOutput() {
        UnifiedPart thinking = new UnifiedPart();
        thinking.setType("thinking");
        thinking.setText("思考内容");

        UnifiedPart text = new UnifiedPart();
        text.setType("text");
        text.setText("最终答案");

        UnifiedToolCall toolCall = new UnifiedToolCall();
        toolCall.setId("toolu_1");
        toolCall.setType("function");
        toolCall.setToolName("get_weather");
        toolCall.setArgumentsJson("{\"city\":\"Shanghai\"}");

        UnifiedOutput output = new UnifiedOutput();
        output.setRole("assistant");
        output.setParts(List.of(thinking, text));
        output.setToolCalls(List.of(toolCall));

        UnifiedResponse response = buildResponse(List.of(output));

        AnthropicMessagesResponse encoded = encoder.encode(response);

        // thinking → text → tool_use 顺序
        assertEquals(3, encoded.getContent().size());
        assertEquals("thinking", encoded.getContent().get(0).getType());
        assertEquals("思考内容", encoded.getContent().get(0).getThinking());
        assertEquals("text", encoded.getContent().get(1).getType());
        assertEquals("最终答案", encoded.getContent().get(1).getText());
        assertEquals("tool_use", encoded.getContent().get(2).getType());
        assertEquals("toolu_1", encoded.getContent().get(2).getId());
        assertEquals("end_turn", encoded.getStopReason());
    }

    @Test
    void encode_multipleOutputs_mergesAllThinkingTextAndToolCalls() {
        // 第一个 output: thinking + text
        UnifiedPart thinking1 = new UnifiedPart();
        thinking1.setType("thinking");
        thinking1.setText("思考-A");

        UnifiedPart text1 = new UnifiedPart();
        text1.setType("text");
        text1.setText("hello ");

        UnifiedOutput first = new UnifiedOutput();
        first.setRole("assistant");
        first.setParts(List.of(thinking1, text1));

        // 第二个 output: text + toolCall
        UnifiedPart text2 = new UnifiedPart();
        text2.setType("text");
        text2.setText("world");

        UnifiedToolCall tc = new UnifiedToolCall();
        tc.setId("toolu_2");
        tc.setType("function");
        tc.setToolName("lookup");
        tc.setArgumentsJson("{}");

        UnifiedOutput second = new UnifiedOutput();
        second.setRole("assistant");
        second.setParts(List.of(text2));
        second.setToolCalls(List.of(tc));

        UnifiedResponse response = buildResponse(List.of(first, second));

        AnthropicMessagesResponse encoded = encoder.encode(response);

        // 聚合后: thinking-A → "hello world" → tool_use(lookup)
        assertEquals(3, encoded.getContent().size());
        assertEquals("thinking", encoded.getContent().get(0).getType());
        assertEquals("思考-A", encoded.getContent().get(0).getThinking());
        assertEquals("text", encoded.getContent().get(1).getType());
        assertEquals("hello world", encoded.getContent().get(1).getText());
        assertEquals("tool_use", encoded.getContent().get(2).getType());
        assertEquals("lookup", encoded.getContent().get(2).getName());
    }

    @Test
    void encode_thinkingWithSignature_preservesSignature() {
        UnifiedPart thinking = new UnifiedPart();
        thinking.setType("thinking");
        thinking.setText("有签名的思考");
        thinking.setAttributes(Map.of("signature", "sig_abc123", "anthropic_type", "thinking"));

        UnifiedOutput output = new UnifiedOutput();
        output.setRole("assistant");
        output.setParts(List.of(thinking));

        UnifiedResponse response = buildResponse(List.of(output));

        AnthropicMessagesResponse encoded = encoder.encode(response);

        assertEquals(1, encoded.getContent().size());
        assertEquals("sig_abc123", encoded.getContent().getFirst().getSignature());
    }

    @Test
    void encode_emptyOutputs_returnsEmptyContent() {
        UnifiedResponse response = new UnifiedResponse();
        response.setId("msg_empty");
        response.setModel("claude-sonnet");
        response.setOutputs(List.of());

        AnthropicMessagesResponse encoded = encoder.encode(response);

        assertNotNull(encoded.getContent());
        assertEquals(0, encoded.getContent().size());
    }

    @Test
    void encode_mapStopReason_variousFinishReasons() {
        assertEquals("end_turn", encoder.encode(buildResponse("stop")).getStopReason());
        assertEquals("max_tokens", encoder.encode(buildResponse("length")).getStopReason());
        assertEquals("tool_use", encoder.encode(buildResponse("tool_calls")).getStopReason());
    }

    private UnifiedResponse buildResponse(String finishReason) {
        UnifiedOutput output = new UnifiedOutput();
        output.setRole("assistant");
        output.setParts(List.of());

        UnifiedResponse response = new UnifiedResponse();
        response.setId("msg_test");
        response.setModel("claude-sonnet");
        response.setFinishReason(finishReason);
        response.setUsage(new UnifiedUsage());
        response.setOutputs(List.of(output));
        return response;
    }

    private UnifiedResponse buildResponse(List<UnifiedOutput> outputs) {
        UnifiedResponse response = new UnifiedResponse();
        response.setId("msg_test");
        response.setModel("claude-sonnet");
        response.setFinishReason("stop");
        response.setUsage(new UnifiedUsage());
        response.setOutputs(outputs);
        return response;
    }
}
