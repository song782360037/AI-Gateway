package com.code.aigateway.core.encoder;

import com.code.aigateway.api.response.GeminiGenerateContentResponse;
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

class GeminiResponseEncoderTest {

    private final GeminiResponseEncoder encoder = new GeminiResponseEncoder(new ObjectMapper());

    @Test
    void encode_textAndToolCall_singleOutput() {
        UnifiedPart text = new UnifiedPart();
        text.setType("text");
        text.setText("你好");

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

        GeminiGenerateContentResponse encoded = encoder.encode(response);

        assertNotNull(encoded.getCandidates());
        assertEquals(1, encoded.getCandidates().size());
        List<Map<String, Object>> parts = encoded.getCandidates().getFirst().getContent().getParts();
        assertEquals(2, parts.size());
        assertEquals("你好", parts.get(0).get("text"));
        assertNotNull(parts.get(1).get("functionCall"));
    }

    @Test
    void encode_multipleOutputs_mergesTextAndToolCalls() {
        UnifiedPart text1 = new UnifiedPart();
        text1.setType("text");
        text1.setText("hello ");

        UnifiedOutput first = new UnifiedOutput();
        first.setRole("assistant");
        first.setParts(List.of(text1));

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

        GeminiGenerateContentResponse encoded = encoder.encode(response);

        List<Map<String, Object>> parts = encoded.getCandidates().getFirst().getContent().getParts();
        // 合并后的文本 + 1 个 functionCall
        assertEquals(2, parts.size());
        assertEquals("hello world", parts.get(0).get("text"));
        assertNotNull(parts.get(1).get("functionCall"));
    }

    @Test
    void encode_finishReasonMapping() {
        assertEquals("STOP", encoder.encode(buildResponse("stop")).getCandidates().getFirst().getFinishReason());
        assertEquals("MAX_TOKENS", encoder.encode(buildResponse("length")).getCandidates().getFirst().getFinishReason());
        assertEquals("FUNCTION_CALL", encoder.encode(buildResponse("tool_calls")).getCandidates().getFirst().getFinishReason());
    }

    private UnifiedResponse buildResponse(List<UnifiedOutput> outputs) {
        UnifiedResponse response = new UnifiedResponse();
        response.setModel("gemini-2.0-flash");
        response.setFinishReason("stop");
        response.setUsage(new UnifiedUsage());
        response.setOutputs(outputs);
        return response;
    }

    private UnifiedResponse buildResponse(String finishReason) {
        UnifiedOutput output = new UnifiedOutput();
        output.setRole("assistant");
        output.setParts(List.of());

        UnifiedResponse response = new UnifiedResponse();
        response.setModel("gemini-2.0-flash");
        response.setFinishReason(finishReason);
        response.setUsage(new UnifiedUsage());
        response.setOutputs(List.of(output));
        return response;
    }
}
