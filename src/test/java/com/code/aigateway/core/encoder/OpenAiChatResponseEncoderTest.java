package com.code.aigateway.core.encoder;

import com.code.aigateway.api.response.OpenAiChatCompletionResponse;
import com.code.aigateway.core.model.UnifiedOutput;
import com.code.aigateway.core.model.UnifiedPart;
import com.code.aigateway.core.model.UnifiedResponse;
import com.code.aigateway.core.model.UnifiedToolCall;
import com.code.aigateway.core.model.UnifiedUsage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class OpenAiChatResponseEncoderTest {

    private final OpenAiChatResponseEncoder encoder = new OpenAiChatResponseEncoder();

    @Test
    void encode_textAndToolCalls_singleOutput() {
        UnifiedPart text = new UnifiedPart();
        text.setType("text");
        text.setText("你好世界");

        UnifiedToolCall tc = new UnifiedToolCall();
        tc.setId("call_1");
        tc.setType("function");
        tc.setToolName("get_weather");
        tc.setArgumentsJson("{\"city\":\"Shanghai\"}");

        UnifiedOutput output = new UnifiedOutput();
        output.setRole("assistant");
        output.setParts(List.of(text));
        output.setToolCalls(List.of(tc));

        UnifiedResponse response = buildResponse(List.of(output));

        OpenAiChatCompletionResponse encoded = encoder.encode(response);

        assertEquals("chat.completion", encoded.getObject());
        assertEquals(1, encoded.getChoices().size());
        assertEquals("你好世界", encoded.getChoices().getFirst().getMessage().getContent());
        assertEquals(1700000000L, encoded.getCreated());
        assertNotNull(encoded.getChoices().getFirst().getMessage().getToolCalls());
        assertEquals(1, encoded.getChoices().getFirst().getMessage().getToolCalls().size());
        assertEquals("get_weather", encoded.getChoices().getFirst().getMessage().getToolCalls().getFirst().getFunction().getName());
    }

    @Test
    void encode_multipleOutputs_mergesTextAndToolCalls() {
        // 第一个 output: text
        UnifiedPart text1 = new UnifiedPart();
        text1.setType("text");
        text1.setText("hello ");

        UnifiedOutput first = new UnifiedOutput();
        first.setRole("assistant");
        first.setParts(List.of(text1));

        // 第二个 output: text + toolCall
        UnifiedPart text2 = new UnifiedPart();
        text2.setType("text");
        text2.setText("world");

        UnifiedToolCall tc = new UnifiedToolCall();
        tc.setId("call_2");
        tc.setType("function");
        tc.setToolName("lookup");
        tc.setArgumentsJson("{}");

        UnifiedOutput second = new UnifiedOutput();
        second.setRole("assistant");
        second.setParts(List.of(text2));
        second.setToolCalls(List.of(tc));

        UnifiedResponse response = buildResponse(List.of(first, second));

        OpenAiChatCompletionResponse encoded = encoder.encode(response);

        // 文本应合并为 "hello world"，toolCalls 应聚合
        assertEquals("hello world", encoded.getChoices().getFirst().getMessage().getContent());
        assertNotNull(encoded.getChoices().getFirst().getMessage().getToolCalls());
        assertEquals(1, encoded.getChoices().getFirst().getMessage().getToolCalls().size());
        assertEquals("lookup", encoded.getChoices().getFirst().getMessage().getToolCalls().getFirst().getFunction().getName());
    }

    @Test
    void encode_thinkingParts_ignoredInChatOutput() {
        // OpenAI Chat 不支持 thinking，thinking parts 应被静默忽略，且不应生成 toolCalls
        UnifiedPart thinking = new UnifiedPart();
        thinking.setType("thinking");
        thinking.setText("内部思考");

        UnifiedPart text = new UnifiedPart();
        text.setType("text");
        text.setText("最终答案");

        UnifiedOutput output = new UnifiedOutput();
        output.setRole("assistant");
        output.setParts(List.of(thinking, text));

        UnifiedResponse response = buildResponse(List.of(output));

        OpenAiChatCompletionResponse encoded = encoder.encode(response);

        assertEquals("最终答案", encoded.getChoices().getFirst().getMessage().getContent());
        assertNull(encoded.getChoices().getFirst().getMessage().getToolCalls());
        assertEquals("stop", encoded.getChoices().getFirst().getFinishReason());
    }

    @Test
    void encode_emptyOutputs_returnsEmptyContent() {
        UnifiedResponse response = new UnifiedResponse();
        response.setId("chat_empty");
        response.setModel("gpt-4o");
        response.setOutputs(List.of());

        OpenAiChatCompletionResponse encoded = encoder.encode(response);

        assertEquals("", encoded.getChoices().getFirst().getMessage().getContent());
        assertNull(encoded.getChoices().getFirst().getMessage().getToolCalls());
    }

    private UnifiedResponse buildResponse(List<UnifiedOutput> outputs) {
        UnifiedResponse response = new UnifiedResponse();
        response.setId("chatcmpl-test");
        response.setModel("gpt-4o");
        response.setCreated(1700000000L);
        response.setFinishReason("stop");
        response.setUsage(new UnifiedUsage());
        response.setOutputs(outputs);
        return response;
    }
}
