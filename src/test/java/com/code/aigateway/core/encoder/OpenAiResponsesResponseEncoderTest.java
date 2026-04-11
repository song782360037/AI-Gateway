package com.code.aigateway.core.encoder;

import com.code.aigateway.api.response.OpenAiResponsesResponse;
import com.code.aigateway.core.model.UnifiedOutput;
import com.code.aigateway.core.model.UnifiedPart;
import com.code.aigateway.core.model.UnifiedResponse;
import com.code.aigateway.core.model.UnifiedToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class OpenAiResponsesResponseEncoderTest {

    private final OpenAiResponsesResponseEncoder encoder = new OpenAiResponsesResponseEncoder();

    @Test
    void encode_withThinkingTextAndToolCall_keepsResponsesOutputTypesValid() {
        UnifiedPart thinkingPart = new UnifiedPart();
        thinkingPart.setType("thinking");
        thinkingPart.setText("先思考一下");

        UnifiedPart textPart = new UnifiedPart();
        textPart.setType("text");
        textPart.setText("最终答案");

        UnifiedToolCall toolCall = new UnifiedToolCall();
        toolCall.setId("toolu_123");
        toolCall.setType("function");
        toolCall.setToolName("get_weather");
        toolCall.setArgumentsJson("{\"city\":\"Shanghai\"}");

        UnifiedOutput output = new UnifiedOutput();
        output.setRole("assistant");
        output.setParts(List.of(thinkingPart, textPart));
        output.setToolCalls(List.of(toolCall));

        UnifiedResponse response = new UnifiedResponse();
        response.setId("resp_1");
        response.setModel("claude-sonnet");
        response.setCreated(1800000000L);
        response.setFinishReason("tool_calls");
        response.setOutputs(List.of(output));

        OpenAiResponsesResponse encoded = encoder.encode(response);

        assertEquals("completed", encoded.getStatus());
        assertEquals(1800000000L, encoded.getCreatedAt());
        assertNotNull(encoded.getOutput());
        assertEquals(3, encoded.getOutput().size());

        OpenAiResponsesResponse.OutputItem reasoningItem = encoded.getOutput().get(0);
        assertEquals("reasoning", reasoningItem.getType());
        assertNotNull(reasoningItem.getSummary());
        assertEquals("summary_text", reasoningItem.getSummary().getFirst().getType());
        assertEquals("先思考一下", reasoningItem.getSummary().getFirst().getText());
        assertNull(reasoningItem.getContent());

        OpenAiResponsesResponse.OutputItem messageItem = encoded.getOutput().get(1);
        assertEquals("message", messageItem.getType());
        assertNotNull(messageItem.getContent());
        assertEquals("output_text", messageItem.getContent().getFirst().getType());
        assertEquals("最终答案", messageItem.getContent().getFirst().getText());

        OpenAiResponsesResponse.OutputItem functionCallItem = encoded.getOutput().get(2);
        assertEquals("function_call", functionCallItem.getType());
        assertEquals("toolu_123", functionCallItem.getCallId());
        assertEquals("get_weather", functionCallItem.getName());
        assertEquals("{\"city\":\"Shanghai\"}", functionCallItem.getArguments());
    }

    @Test
    void encode_withMultipleOutputs_mergesTextThinkingAndToolCalls() {
        UnifiedPart firstThinking = new UnifiedPart();
        firstThinking.setType("thinking");
        firstThinking.setText("thinking-1");

        UnifiedPart firstText = new UnifiedPart();
        firstText.setType("text");
        firstText.setText("hello ");

        UnifiedOutput firstOutput = new UnifiedOutput();
        firstOutput.setRole("assistant");
        firstOutput.setParts(List.of(firstThinking, firstText));

        UnifiedPart secondText = new UnifiedPart();
        secondText.setType("text");
        secondText.setText("world");

        UnifiedToolCall secondToolCall = new UnifiedToolCall();
        secondToolCall.setId("toolu_2");
        secondToolCall.setType("function");
        secondToolCall.setToolName("lookup");
        secondToolCall.setArgumentsJson("{}");

        UnifiedOutput secondOutput = new UnifiedOutput();
        secondOutput.setRole("assistant");
        secondOutput.setParts(List.of(secondText));
        secondOutput.setToolCalls(List.of(secondToolCall));

        UnifiedResponse response = new UnifiedResponse();
        response.setId("resp_2");
        response.setModel("claude-sonnet");
        response.setCreated(1800000001L);
        response.setOutputs(List.of(firstOutput, secondOutput));

        OpenAiResponsesResponse encoded = encoder.encode(response);

        assertEquals(1800000001L, encoded.getCreatedAt());
        assertEquals(3, encoded.getOutput().size());
        assertEquals("thinking-1", encoded.getOutput().get(0).getSummary().getFirst().getText());
        assertEquals("hello world", encoded.getOutput().get(1).getContent().getFirst().getText());
        assertEquals("toolu_2", encoded.getOutput().get(2).getCallId());
    }
}
