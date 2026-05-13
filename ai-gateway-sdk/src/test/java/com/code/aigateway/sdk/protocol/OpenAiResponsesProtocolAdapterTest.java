package com.code.aigateway.sdk.protocol;

import com.code.aigateway.sdk.error.ErrorCode;
import com.code.aigateway.sdk.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * OpenAI Responses API 协议适配器测试
 */
class OpenAiResponsesProtocolAdapterTest {

    private OpenAiResponsesProtocolAdapter adapter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        adapter = new OpenAiResponsesProtocolAdapter(objectMapper);
    }

    // ===================== 基本属性 =====================

    @Test
    @DisplayName("getProtocolType 返回 OPENAI_RESPONSES")
    void getProtocolType_shouldReturnOpenAiResponses() {
        assertThat(adapter.getProtocolType()).isEqualTo(ProtocolType.OPENAI_RESPONSES);
    }

    @Test
    @DisplayName("isSse 返回 true")
    void isSse_shouldReturnTrue() {
        assertThat(adapter.isSse()).isTrue();
    }

    // ===================== 请求解析 =====================

    @Nested
    @DisplayName("parse 请求解析")
    class ParseTests {

        @Test
        @DisplayName("基本请求解析")
        void parse_basicRequest() {
            Map<String, Object> request = Map.of(
                    "model", "gpt-4o",
                    "input", List.of(
                            Map.of("type", "message", "role", "user", "content", "Hello")
                    )
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getModel()).isEqualTo("gpt-4o");
            assertThat(result.getRequestProtocol()).isEqualTo("openai-responses");
            assertThat(result.getResponseProtocol()).isEqualTo("openai-responses");
            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages().get(0).getRole()).isEqualTo("user");
        }

        @Test
        @DisplayName("instructions 提取为 systemPrompt")
        void parse_instructionsToSystemPrompt() {
            Map<String, Object> request = Map.of(
                    "model", "gpt-4o",
                    "instructions", "You are a translator.",
                    "input", List.of(
                            Map.of("type", "message", "role", "user", "content", "Hi")
                    )
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getSystemPrompt()).isEqualTo("You are a translator.");
        }

        @Test
        @DisplayName("input 字符串格式解析")
        void parse_inputAsString() {
            Map<String, Object> request = Map.of(
                    "model", "gpt-4o",
                    "input", "Hello"
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages().get(0).getRole()).isEqualTo("user");
        }

        @Test
        @DisplayName("工具定义解析（嵌套格式）")
        void parse_toolsNestedFormat() {
            Map<String, Object> request = Map.of(
                    "model", "gpt-4o",
                    "input", List.of(Map.of("type", "message", "role", "user", "content", "Hi")),
                    "tools", List.of(
                            Map.of(
                                    "type", "function",
                                    "function", Map.of(
                                            "name", "search",
                                            "description", "Search the web",
                                            "parameters", Map.of("type", "object")
                                    )
                            )
                    )
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getTools()).hasSize(1);
            assertThat(result.getTools().get(0).getName()).isEqualTo("search");
        }

        @Test
        @DisplayName("工具定义解析（扁平格式）")
        void parse_toolsFlatFormat() {
            Map<String, Object> request = Map.of(
                    "model", "gpt-4o",
                    "input", List.of(Map.of("type", "message", "role", "user", "content", "Hi")),
                    "tools", List.of(
                            Map.of(
                                    "type", "function",
                                    "name", "search",
                                    "description", "Search the web",
                                    "parameters", Map.of("type", "object")
                            )
                    )
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getTools()).hasSize(1);
            assertThat(result.getTools().get(0).getName()).isEqualTo("search");
        }

        @Test
        @DisplayName("function_call 输入类型解析")
        void parse_functionCallInput() {
            Map<String, Object> request = Map.of(
                    "model", "gpt-4o",
                    "input", List.of(
                            Map.of("type", "message", "role", "user", "content", "Search"),
                            Map.of(
                                    "type", "function_call",
                                    "call_id", "fc_001",
                                    "name", "search",
                                    "arguments", "{\"q\":\"test\"}"
                            )
                    )
            );

            UnifiedRequest result = adapter.parse(request);

            // 应有 user 消息 + assistant 的 toolCalls 消息
            assertThat(result.getMessages().stream()
                    .anyMatch(m -> m.getToolCalls() != null && !m.getToolCalls().isEmpty()))
                    .isTrue();
        }

        @Test
        @DisplayName("function_call_output 输入类型解析")
        void parse_functionCallOutput() {
            Map<String, Object> request = Map.of(
                    "model", "gpt-4o",
                    "input", List.of(
                            Map.of(
                                    "type", "function_call_output",
                                    "call_id", "fc_001",
                                    "output", "result data"
                            )
                    )
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getMessages().stream()
                    .anyMatch(m -> "tool".equals(m.getRole())))
                    .isTrue();
        }

        @Test
        @DisplayName("reasoning 配置解析")
        void parse_reasoningConfig() {
            Map<String, Object> request = Map.of(
                    "model", "gpt-4o",
                    "input", List.of(Map.of("type", "message", "role", "user", "content", "Hi")),
                    "reasoning", Map.of("effort", "high", "summary", "auto")
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getGenerationConfig().getReasoning()).isNotNull();
            assertThat(result.getGenerationConfig().getReasoning().getEffort()).isEqualTo("high");
            assertThat(result.getGenerationConfig().getReasoning().getEnabled()).isTrue();
            assertThat(result.getGenerationConfig().getReasoning().getSummary()).isEqualTo("auto");
        }

        @Test
        @DisplayName("tool_choice 解析")
        void parse_toolChoice() {
            Map<String, Object> request = Map.of(
                    "model", "gpt-4o",
                    "input", List.of(Map.of("type", "message", "role", "user", "content", "Hi")),
                    "tool_choice", "required"
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getToolChoice()).isNotNull();
            assertThat(result.getToolChoice().getType()).isEqualTo("required");
        }

        @Test
        @DisplayName("input_text 和 text 混合类型内容解析")
        void parse_mixedContentTypes() {
            Map<String, Object> request = Map.of(
                    "model", "gpt-4o",
                    "input", List.of(
                            Map.of(
                                    "type", "message",
                                    "role", "user",
                                    "content", List.of(
                                            Map.of("type", "input_text", "text", "你好"),
                                            Map.of("type", "text", "text", "世界")
                                    )
                            )
                    )
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages().get(0).getParts()).hasSize(2);
            assertThat(result.getMessages().get(0).getParts().get(0).getText()).isEqualTo("你好");
            assertThat(result.getMessages().get(0).getParts().get(1).getText()).isEqualTo("世界");
        }

        @Test
        @DisplayName("input_image URL 格式解析为 image part")
        void parse_inputImageUrl() {
            Map<String, Object> request = Map.of(
                    "model", "gpt-4o",
                    "input", List.of(
                            Map.of(
                                    "type", "message",
                                    "role", "user",
                                    "content", List.of(
                                            Map.of("type", "input_text", "text", "描述图片"),
                                            Map.of("type", "input_image", "image_url", "https://example.com/test.png", "detail", "high")
                                    )
                            )
                    )
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getMessages()).hasSize(1);
            List<UnifiedPart> parts = result.getMessages().get(0).getParts();
            assertThat(parts).hasSize(2);
            assertThat(parts.get(0).getType()).isEqualTo("text");
            assertThat(parts.get(1).getType()).isEqualTo("image");
            assertThat(parts.get(1).getUrl()).isEqualTo("https://example.com/test.png");
        }

        @Test
        @DisplayName("input_image Base64 格式解析为 image part")
        void parse_inputImageBase64() {
            Map<String, Object> request = Map.of(
                    "model", "gpt-4o",
                    "input", List.of(
                            Map.of(
                                    "type", "message",
                                    "role", "user",
                                    "content", List.of(
                                            Map.of("type", "input_image", "image_url", "data:image/png;base64,QUJDRA==")
                                    )
                            )
                    )
            );

            UnifiedRequest result = adapter.parse(request);

            List<UnifiedPart> parts = result.getMessages().get(0).getParts();
            assertThat(parts).hasSize(1);
            assertThat(parts.get(0).getType()).isEqualTo("image");
            assertThat(parts.get(0).getMimeType()).isEqualTo("image/png");
            assertThat(parts.get(0).getBase64Data()).isEqualTo("QUJDRA==");
        }

        @Test
        @DisplayName("不支持的 content 类型回退为空文本")
        void parse_unsupportedContentType_fallback() {
            Map<String, Object> request = Map.of(
                    "model", "gpt-4o",
                    "input", List.of(
                            Map.of(
                                    "type", "message",
                                    "role", "user",
                                    "content", List.of(
                                            Map.of("type", "input_file", "file_data", "dGVzdA==")
                                    )
                            )
                    )
            );

            UnifiedRequest result = adapter.parse(request);

            // SDK 对不支持的类型回退为空文本，不抛异常
            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages().get(0).getParts()).hasSize(1);
        }
    }

    // ===================== 响应编码 =====================

    @Nested
    @DisplayName("encodeResponse 响应编码")
    class EncodeResponseTests {

        @Test
        @DisplayName("文本响应编码")
        @SuppressWarnings("unchecked")
        void encodeResponse_textResponse() {
            UnifiedResponse response = buildTextResponse("Hello!", "resp-123", "gpt-4o");

            Object result = adapter.encodeResponse(response);

            Map<String, Object> map = (Map<String, Object>) result;
            assertThat(map.get("id")).isEqualTo("resp-123");
            assertThat(map.get("object")).isEqualTo("response");
            assertThat(map.get("model")).isEqualTo("gpt-4o");
            assertThat(map.get("status")).isEqualTo("completed");

            List<Map<String, Object>> output = (List<Map<String, Object>>) map.get("output");
            assertThat(output).hasSize(1);
            assertThat(output.get(0).get("type")).isEqualTo("message");
            assertThat(output.get(0).get("role")).isEqualTo("assistant");
        }

        @Test
        @DisplayName("工具调用响应编码")
        @SuppressWarnings("unchecked")
        void encodeResponse_toolCallResponse() {
            UnifiedResponse response = new UnifiedResponse();
            response.setId("resp-456");
            response.setModel("gpt-4o");
            response.setCreated(1700000000L);

            UnifiedToolCall toolCall = new UnifiedToolCall();
            toolCall.setId("fc_001");
            toolCall.setType("function");
            toolCall.setToolName("search");
            toolCall.setArgumentsJson("{\"q\":\"test\"}");

            UnifiedOutput output = new UnifiedOutput();
            output.setToolCalls(List.of(toolCall));
            response.setOutputs(List.of(output));

            Object result = adapter.encodeResponse(response);
            Map<String, Object> map = (Map<String, Object>) result;

            List<Map<String, Object>> outputItems = (List<Map<String, Object>>) map.get("output");
            assertThat(outputItems).hasSize(1);
            assertThat(outputItems.get(0).get("type")).isEqualTo("function_call");
            assertThat(outputItems.get(0).get("name")).isEqualTo("search");
            assertThat(outputItems.get(0).get("arguments")).isEqualTo("{\"q\":\"test\"}");
        }

        @Test
        @DisplayName("thinking 内容编码为 reasoning item")
        @SuppressWarnings("unchecked")
        void encodeResponse_thinkingAsReasoning() {
            UnifiedResponse response = new UnifiedResponse();
            response.setId("resp-789");
            response.setModel("gpt-4o");
            response.setCreated(1700000000L);

            UnifiedPart thinkingPart = new UnifiedPart();
            thinkingPart.setType("thinking");
            thinkingPart.setText("I need to analyze...");

            UnifiedPart textPart = new UnifiedPart();
            textPart.setType("text");
            textPart.setText("Here is the answer.");

            UnifiedOutput output = new UnifiedOutput();
            output.setParts(List.of(thinkingPart, textPart));
            response.setOutputs(List.of(output));

            Object result = adapter.encodeResponse(response);
            Map<String, Object> map = (Map<String, Object>) result;

            List<Map<String, Object>> outputItems = (List<Map<String, Object>>) map.get("output");
            // 应包含 reasoning 和 message 两个 item
            assertThat(outputItems).hasSize(2);
            assertThat(outputItems.get(0).get("type")).isEqualTo("reasoning");
            assertThat(outputItems.get(1).get("type")).isEqualTo("message");
        }
    }

    // ===================== 流式编码 =====================

    @Nested
    @DisplayName("encodeStreamEvent 流式编码")
    class EncodeStreamEventTests {

        private StreamEncodeContext ctx;

        @BeforeEach
        void initContext() {
            ctx = new StreamEncodeContext("resp-001", 1700000000L, "gpt-4o");
        }

        @Test
        @DisplayName("text_delta 首次生成 output_item.added + output_text.delta")
        void encodeStreamEvent_textDelta_firstTime() {
            UnifiedStreamEvent event = UnifiedStreamEvent.textDelta("Hello");

            List<EncodedEvent> events = adapter.encodeStreamEvent(event, ctx);

            // 首次应生成 output_item.added + output_text.delta
            assertThat(events).hasSize(2);
            assertThat(events.get(0).eventName()).isEqualTo("response.output_item.added");
            assertThat(events.get(1).eventName()).isEqualTo("response.output_text.delta");
            assertThat(events.get(1).data()).contains("Hello");
        }

        @Test
        @DisplayName("连续 text_delta 只生成 output_text.delta")
        void encodeStreamEvent_textDelta_subsequent() {
            // 第一次
            adapter.encodeStreamEvent(UnifiedStreamEvent.textDelta("Hello"), ctx);
            // 第二次
            List<EncodedEvent> events = adapter.encodeStreamEvent(UnifiedStreamEvent.textDelta(" World"), ctx);

            assertThat(events).hasSize(1);
            assertThat(events.get(0).eventName()).isEqualTo("response.output_text.delta");
        }

        @Test
        @DisplayName("thinking_delta 生成 output_item.added(reasoning) + reasoning_summary_text.delta")
        void encodeStreamEvent_thinkingDelta() {
            UnifiedStreamEvent event = UnifiedStreamEvent.thinkingDelta("Analyzing...");

            List<EncodedEvent> events = adapter.encodeStreamEvent(event, ctx);

            assertThat(events).hasSize(2);
            assertThat(events.get(0).eventName()).isEqualTo("response.output_item.added");
            assertThat(events.get(1).eventName()).isEqualTo("response.reasoning_summary_text.delta");
        }

        @Test
        @DisplayName("tool_call 生成 output_item.added(function_call)")
        void encodeStreamEvent_toolCall() {
            UnifiedStreamEvent event = UnifiedStreamEvent.toolCall("fc_001", "search", null);

            List<EncodedEvent> events = adapter.encodeStreamEvent(event, ctx);

            assertThat(events).hasSizeGreaterThanOrEqualTo(1);
            assertThat(events.stream()
                    .anyMatch(e -> "response.output_item.added".equals(e.eventName())))
                    .isTrue();
        }

        @Test
        @DisplayName("tool_call_delta 生成 function_call_arguments.delta")
        void encodeStreamEvent_toolCallDelta() {
            // 先打开一个 tool_call
            adapter.encodeStreamEvent(UnifiedStreamEvent.toolCall("fc_001", "search", null), ctx);

            UnifiedStreamEvent delta = UnifiedStreamEvent.toolCallDelta(0, "{\"q\":");
            List<EncodedEvent> events = adapter.encodeStreamEvent(delta, ctx);

            assertThat(events).hasSize(1);
            assertThat(events.get(0).eventName()).isEqualTo("response.function_call_arguments.delta");
        }

        @Test
        @DisplayName("done 生成 response.completed")
        void encodeStreamEvent_done() {
            UnifiedStreamEvent event = UnifiedStreamEvent.done("stop");

            List<EncodedEvent> events = adapter.encodeStreamEvent(event, ctx);

            assertThat(events.stream()
                    .anyMatch(e -> e.eventName() != null && e.eventName().equals("response.completed")))
                    .isTrue();
        }

        @Test
        @DisplayName("terminalStreamEvents 返回空列表")
        void terminalStreamEvents_returnsEmpty() {
            assertThat(adapter.terminalStreamEvents(ctx)).isEmpty();
        }

        @Test
        @DisplayName("initialStreamEvents 返回空列表")
        void initialStreamEvents_returnsEmpty() {
            assertThat(adapter.initialStreamEvents(ctx)).isEmpty();
        }
    }

    // ===================== 错误编码 =====================

    @Nested
    @DisplayName("buildError 错误编码")
    class BuildErrorTests {

        @Test
        @DisplayName("OpenAI 错误格式（与 Chat 相同）")
        @SuppressWarnings("unchecked")
        void buildError_openAiFormat() {
            Object result = adapter.buildError("model not found", "invalid_request_error", "MODEL_NOT_FOUND", null);

            Map<String, Object> map = (Map<String, Object>) result;
            Map<String, Object> error = (Map<String, Object>) map.get("error");
            assertThat(error.get("message")).isEqualTo("model not found");
            assertThat(error.get("type")).isEqualTo("invalid_request_error");
            assertThat(error.get("code")).isEqualTo("MODEL_NOT_FOUND");
        }

        @Test
        @DisplayName("mapErrorType 各错误码映射")
        void mapErrorType_variousCodes() {
            assertThat(adapter.mapErrorType(ErrorCode.INVALID_REQUEST)).isEqualTo("invalid_request_error");
            assertThat(adapter.mapErrorType(ErrorCode.AUTH_FAILED)).isEqualTo("authentication_error");
            assertThat(adapter.mapErrorType(ErrorCode.RATE_LIMITED)).isEqualTo("rate_limit_error");
            assertThat(adapter.mapErrorType(ErrorCode.INTERNAL_ERROR)).isEqualTo("server_error");
        }
    }

    // ===================== 辅助方法 =====================

    /** 构建文本响应 */
    private UnifiedResponse buildTextResponse(String text, String id, String model) {
        UnifiedResponse response = new UnifiedResponse();
        response.setId(id);
        response.setModel(model);
        response.setCreated(1700000000L);
        response.setFinishReason("stop");

        UnifiedPart part = new UnifiedPart();
        part.setType("text");
        part.setText(text);

        UnifiedOutput output = new UnifiedOutput();
        output.setParts(List.of(part));
        response.setOutputs(List.of(output));
        return response;
    }
}
