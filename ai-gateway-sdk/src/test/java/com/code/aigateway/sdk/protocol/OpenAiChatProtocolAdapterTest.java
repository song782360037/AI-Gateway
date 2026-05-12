package com.code.aigateway.sdk.protocol;

import com.code.aigateway.sdk.error.ErrorCode;
import com.code.aigateway.sdk.error.ProtocolException;
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
 * OpenAI Chat Completions 协议适配器测试
 */
class OpenAiChatProtocolAdapterTest {

    private OpenAiChatProtocolAdapter adapter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        adapter = new OpenAiChatProtocolAdapter(objectMapper);
    }

    // ===================== 基本属性 =====================

    @Test
    @DisplayName("getProtocolType 返回 OPENAI_CHAT")
    void getProtocolType_shouldReturnOpenAiChat() {
        assertThat(adapter.getProtocolType()).isEqualTo(ProtocolType.OPENAI_CHAT);
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
                    "messages", List.of(
                            Map.of("role", "user", "content", "Hello")
                    ),
                    "stream", true
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getModel()).isEqualTo("gpt-4o");
            assertThat(result.getRequestProtocol()).isEqualTo("openai-chat");
            assertThat(result.getResponseProtocol()).isEqualTo("openai-chat");
            assertThat(result.getStream()).isTrue();
            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages().get(0).getRole()).isEqualTo("user");
        }

        @Test
        @DisplayName("system 提示词提取")
        void parse_systemPromptExtraction() {
            Map<String, Object> request = Map.of(
                    "model", "gpt-4o",
                    "messages", List.of(
                            Map.of("role", "system", "content", "You are a helpful assistant."),
                            Map.of("role", "user", "content", "Hi")
                    )
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getSystemPrompt()).isEqualTo("You are a helpful assistant.");
            // system 消息不应出现在 messages 中
            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages().get(0).getRole()).isEqualTo("user");
        }

        @Test
        @DisplayName("多条 system 消息合并")
        void parse_multipleSystemPrompts() {
            Map<String, Object> request = Map.of(
                    "model", "gpt-4o",
                    "messages", List.of(
                            Map.of("role", "system", "content", "Rule 1."),
                            Map.of("role", "system", "content", "Rule 2."),
                            Map.of("role", "user", "content", "Hi")
                    )
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getSystemPrompt()).isEqualTo("Rule 1.\n\nRule 2.");
        }

        @Test
        @DisplayName("工具定义解析")
        void parse_tools() {
            Map<String, Object> request = Map.of(
                    "model", "gpt-4o",
                    "messages", List.of(
                            Map.of("role", "user", "content", "What's the weather?")
                    ),
                    "tools", List.of(
                            Map.of(
                                    "type", "function",
                                    "function", Map.of(
                                            "name", "get_weather",
                                            "description", "Get weather info",
                                            "parameters", Map.of("type", "object")
                                    )
                            )
                    ),
                    "tool_choice", "auto"
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getTools()).hasSize(1);
            assertThat(result.getTools().get(0).getName()).isEqualTo("get_weather");
            assertThat(result.getTools().get(0).getDescription()).isEqualTo("Get weather info");
            assertThat(result.getToolChoice()).isNotNull();
            assertThat(result.getToolChoice().getType()).isEqualTo("auto");
        }

        @Test
        @DisplayName("生成配置解析 - temperature/top_p/max_tokens")
        void parse_generationConfig() {
            Map<String, Object> request = Map.of(
                    "model", "gpt-4o",
                    "messages", List.of(Map.of("role", "user", "content", "Hi")),
                    "temperature", 0.7,
                    "top_p", 0.9,
                    "max_tokens", 1024
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getGenerationConfig().getTemperature()).isEqualTo(0.7);
            assertThat(result.getGenerationConfig().getTopP()).isEqualTo(0.9);
            assertThat(result.getGenerationConfig().getMaxOutputTokens()).isEqualTo(1024);
        }

        @Test
        @DisplayName("max_completion_tokens 优先于 max_tokens")
        void parse_maxCompletionTokensPreferred() {
            Map<String, Object> request = Map.of(
                    "model", "gpt-4o",
                    "messages", List.of(Map.of("role", "user", "content", "Hi")),
                    "max_completion_tokens", 2048,
                    "max_tokens", 1024
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getGenerationConfig().getMaxOutputTokens()).isEqualTo(2048);
        }

        @Test
        @DisplayName("null rawRequest 抛出 NullPointerException")
        void parse_nullRequest_throwsNPE() {
            assertThatThrownBy(() -> adapter.parse(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("缺少 model 字段抛出 ProtocolException")
        void parse_missingModel_throwsProtocolException() {
            Map<String, Object> request = Map.of(
                    "messages", List.of(Map.of("role", "user", "content", "Hi"))
            );

            assertThatThrownBy(() -> adapter.parse(request))
                    .isInstanceOf(ProtocolException.class)
                    .satisfies(ex -> {
                        ProtocolException pe = (ProtocolException) ex;
                        assertThat(pe.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                        assertThat(pe.getParam()).isEqualTo("model");
                    });
        }

        @Test
        @DisplayName("tool_choice 指定具体工具名")
        void parse_specificToolChoice() {
            Map<String, Object> request = Map.of(
                    "model", "gpt-4o",
                    "messages", List.of(Map.of("role", "user", "content", "Hi")),
                    "tools", List.of(
                            Map.of("type", "function", "function", Map.of("name", "my_tool"))
                    ),
                    "tool_choice", Map.of("type", "function", "function", Map.of("name", "my_tool"))
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getToolChoice()).isNotNull();
            assertThat(result.getToolChoice().getType()).isEqualTo("specific");
            assertThat(result.getToolChoice().getToolName()).isEqualTo("my_tool");
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
            UnifiedResponse response = buildTextResponse("Hello!", "chatcmpl-123", "gpt-4o");

            Object result = adapter.encodeResponse(response);

            assertThat(result).isInstanceOf(Map.class);
            Map<String, Object> map = (Map<String, Object>) result;
            assertThat(map.get("id")).isEqualTo("chatcmpl-123");
            assertThat(map.get("object")).isEqualTo("chat.completion");
            assertThat(map.get("model")).isEqualTo("gpt-4o");

            List<Map<String, Object>> choices = (List<Map<String, Object>>) map.get("choices");
            assertThat(choices).hasSize(1);
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            assertThat(message.get("role")).isEqualTo("assistant");
            assertThat(message.get("content")).isEqualTo("Hello!");
            assertThat(choices.get(0).get("finish_reason")).isEqualTo("stop");
        }

        @Test
        @DisplayName("工具调用响应编码")
        @SuppressWarnings("unchecked")
        void encodeResponse_toolCallResponse() {
            UnifiedResponse response = new UnifiedResponse();
            response.setId("chatcmpl-456");
            response.setModel("gpt-4o");
            response.setCreated(1700000000L);
            response.setFinishReason("tool_calls");

            UnifiedOutput output = new UnifiedOutput();
            UnifiedToolCall toolCall = new UnifiedToolCall();
            toolCall.setId("call_abc");
            toolCall.setType("function");
            toolCall.setToolName("get_weather");
            toolCall.setArgumentsJson("{\"city\":\"Beijing\"}");
            output.setToolCalls(List.of(toolCall));
            response.setOutputs(List.of(output));

            Object result = adapter.encodeResponse(response);
            Map<String, Object> map = (Map<String, Object>) result;

            List<Map<String, Object>> choices = (List<Map<String, Object>>) map.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");

            assertThat(toolCalls).hasSize(1);
            assertThat(toolCalls.get(0).get("id")).isEqualTo("call_abc");
            Map<String, Object> function = (Map<String, Object>) toolCalls.get(0).get("function");
            assertThat(function.get("name")).isEqualTo("get_weather");
            assertThat(function.get("arguments")).isEqualTo("{\"city\":\"Beijing\"}");
        }

        @Test
        @DisplayName("包含 usage 的响应编码")
        @SuppressWarnings("unchecked")
        void encodeResponse_withUsage() {
            UnifiedResponse response = buildTextResponse("Hi", "chatcmpl-789", "gpt-4o");
            UnifiedUsage usage = new UnifiedUsage();
            usage.setInputTokens(10);
            usage.setOutputTokens(5);
            usage.setTotalTokens(15);
            response.setUsage(usage);

            Object result = adapter.encodeResponse(response);
            Map<String, Object> map = (Map<String, Object>) result;

            Map<String, Object> usageMap = (Map<String, Object>) map.get("usage");
            assertThat(usageMap).isNotNull();
            assertThat(usageMap.get("prompt_tokens")).isEqualTo(10);
            assertThat(usageMap.get("completion_tokens")).isEqualTo(5);
            assertThat(usageMap.get("total_tokens")).isEqualTo(15);
        }
    }

    // ===================== 流式编码 =====================

    @Nested
    @DisplayName("encodeStreamEvent 流式编码")
    class EncodeStreamEventTests {

        private StreamEncodeContext ctx;

        @BeforeEach
        void initContext() {
            ctx = new StreamEncodeContext("chatcmpl-001", 1700000000L, "gpt-4o");
        }

        @Test
        @DisplayName("text_delta 编码")
        void encodeStreamEvent_textDelta() {
            UnifiedStreamEvent event = UnifiedStreamEvent.textDelta("Hello");

            List<EncodedEvent> events = adapter.encodeStreamEvent(event, ctx);

            assertThat(events).hasSize(1);
            assertThat(events.get(0).eventName()).isNull(); // SSE data-only 事件
            assertThat(events.get(0).data()).contains("chat.completion.chunk");
            assertThat(events.get(0).data()).contains("Hello");
            // 首个 content chunk 应包含 role
            assertThat(events.get(0).data()).contains("assistant");
        }

        @Test
        @DisplayName("done 事件编码")
        void encodeStreamEvent_done() {
            UnifiedStreamEvent event = UnifiedStreamEvent.done("stop");

            List<EncodedEvent> events = adapter.encodeStreamEvent(event, ctx);

            assertThat(events).hasSize(1);
            assertThat(events.get(0).data()).contains("stop");
        }

        @Test
        @DisplayName("tool_call 事件编码")
        void encodeStreamEvent_toolCall() {
            UnifiedStreamEvent event = UnifiedStreamEvent.toolCall("call_001", "search", 0);

            List<EncodedEvent> events = adapter.encodeStreamEvent(event, ctx);

            assertThat(events).hasSize(1);
            assertThat(events.get(0).data()).contains("tool_calls");
            assertThat(events.get(0).data()).contains("call_001");
            assertThat(events.get(0).data()).contains("search");
        }

        @Test
        @DisplayName("tool_call_delta 事件编码")
        void encodeStreamEvent_toolCallDelta() {
            UnifiedStreamEvent event = UnifiedStreamEvent.toolCallDelta(0, "{\"query\":");

            List<EncodedEvent> events = adapter.encodeStreamEvent(event, ctx);

            assertThat(events).hasSize(1);
            // 验证 tool_calls delta 结构存在，且包含 arguments 字段
            assertThat(events.get(0).data()).contains("tool_calls");
            assertThat(events.get(0).data()).contains("arguments");
            // 验证 arguments 内容包含 query 关键字
            assertThat(events.get(0).data()).contains("query");
        }

        @Test
        @DisplayName("thinking_delta 事件被静默丢弃")
        void encodeStreamEvent_thinkingDelta_discarded() {
            UnifiedStreamEvent event = UnifiedStreamEvent.thinkingDelta("thinking...");

            List<EncodedEvent> events = adapter.encodeStreamEvent(event, ctx);

            assertThat(events).isEmpty();
        }

        @Test
        @DisplayName("terminalStreamEvents 返回 [DONE]")
        void terminalStreamEvents_returnsDone() {
            List<EncodedEvent> events = adapter.terminalStreamEvents(ctx);

            assertThat(events).hasSize(1);
            assertThat(events.get(0).data()).isEqualTo("[DONE]");
        }

        @Test
        @DisplayName("initialStreamEvents 返回空列表")
        void initialStreamEvents_returnsEmpty() {
            List<EncodedEvent> events = adapter.initialStreamEvents(ctx);

            assertThat(events).isEmpty();
        }
    }

    // ===================== 错误编码 =====================

    @Nested
    @DisplayName("buildError 错误编码")
    class BuildErrorTests {

        @Test
        @DisplayName("构建错误响应体")
        @SuppressWarnings("unchecked")
        void buildError_basicError() {
            Object result = adapter.buildError("model not found", "invalid_request_error", "MODEL_NOT_FOUND", null);

            Map<String, Object> map = (Map<String, Object>) result;
            Map<String, Object> error = (Map<String, Object>) map.get("error");
            assertThat(error.get("message")).isEqualTo("model not found");
            assertThat(error.get("type")).isEqualTo("invalid_request_error");
            assertThat(error.get("code")).isEqualTo("MODEL_NOT_FOUND");
            assertThat(error).doesNotContainKey("param");
        }

        @Test
        @DisplayName("带 param 的错误响应体")
        @SuppressWarnings("unchecked")
        void buildError_withParam() {
            Object result = adapter.buildError("missing field", "invalid_request_error", "INVALID_REQUEST", "model");

            Map<String, Object> map = (Map<String, Object>) result;
            Map<String, Object> error = (Map<String, Object>) map.get("error");
            assertThat(error.get("param")).isEqualTo("model");
        }

        @Test
        @DisplayName("mapErrorType 各错误码映射")
        void mapErrorType_variousCodes() {
            assertThat(adapter.mapErrorType(ErrorCode.INVALID_REQUEST)).isEqualTo("invalid_request_error");
            assertThat(adapter.mapErrorType(ErrorCode.AUTH_FAILED)).isEqualTo("authentication_error");
            assertThat(adapter.mapErrorType(ErrorCode.RATE_LIMITED)).isEqualTo("rate_limit_error");
            assertThat(adapter.mapErrorType(ErrorCode.INTERNAL_ERROR)).isEqualTo("server_error");
            assertThat(adapter.mapErrorType(ErrorCode.MODEL_NOT_FOUND)).isEqualTo("invalid_request_error");
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
