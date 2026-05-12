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
 * Google Gemini API 协议适配器测试
 */
class GeminiProtocolAdapterTest {

    private GeminiProtocolAdapter adapter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        adapter = new GeminiProtocolAdapter(objectMapper);
    }

    // ===================== 基本属性 =====================

    @Test
    @DisplayName("getProtocolType 返回 GEMINI")
    void getProtocolType_shouldReturnGemini() {
        assertThat(adapter.getProtocolType()).isEqualTo(ProtocolType.GEMINI);
    }

    @Test
    @DisplayName("isSse 返回 false（NDJSON 格式）")
    void isSse_shouldReturnFalse() {
        assertThat(adapter.isSse()).isFalse();
    }

    // ===================== 请求解析 =====================

    @Nested
    @DisplayName("parse 请求解析")
    class ParseTests {

        @Test
        @DisplayName("基本请求解析")
        void parse_basicRequest() {
            Map<String, Object> request = Map.of(
                    "model", "gemini-2.0-flash",
                    "contents", List.of(
                            Map.of("role", "user", "parts", List.of(Map.of("text", "Hello")))
                    )
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getModel()).isEqualTo("gemini-2.0-flash");
            assertThat(result.getRequestProtocol()).isEqualTo("gemini");
            assertThat(result.getResponseProtocol()).isEqualTo("gemini");
            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages().get(0).getRole()).isEqualTo("user");
        }

        @Test
        @DisplayName("systemInstruction 提取")
        void parse_systemInstruction() {
            Map<String, Object> request = Map.of(
                    "model", "gemini-2.0-flash",
                    "contents", List.of(
                            Map.of("role", "user", "parts", List.of(Map.of("text", "Hi")))
                    ),
                    "systemInstruction", Map.of(
                            "parts", List.of(Map.of("text", "You are a translator."))
                    )
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getSystemPrompt()).isEqualTo("You are a translator.");
        }

        @Test
        @DisplayName("model 角色映射为 assistant")
        void parse_modelRoleMapsToAssistant() {
            Map<String, Object> request = Map.of(
                    "model", "gemini-2.0-flash",
                    "contents", List.of(
                            Map.of("role", "user", "parts", List.of(Map.of("text", "Hi"))),
                            Map.of("role", "model", "parts", List.of(Map.of("text", "Hello!")))
                    )
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getMessages()).hasSize(2);
            assertThat(result.getMessages().get(0).getRole()).isEqualTo("user");
            assertThat(result.getMessages().get(1).getRole()).isEqualTo("assistant");
        }

        @Test
        @DisplayName("functionDeclarations 工具定义解析")
        void parse_functionDeclarations() {
            Map<String, Object> request = Map.of(
                    "model", "gemini-2.0-flash",
                    "contents", List.of(
                            Map.of("role", "user", "parts", List.of(Map.of("text", "Search")))
                    ),
                    "tools", Map.of(
                            "functionDeclarations", List.of(
                                    Map.of(
                                            "name", "search_web",
                                            "description", "Search the web",
                                            "parameters", Map.of("type", "object")
                                    )
                            )
                    )
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getTools()).hasSize(1);
            assertThat(result.getTools().get(0).getName()).isEqualTo("search_web");
            assertThat(result.getTools().get(0).getDescription()).isEqualTo("Search the web");
        }

        @Test
        @DisplayName("tool_config 解析 - AUTO 模式")
        void parse_toolConfigAuto() {
            Map<String, Object> request = Map.of(
                    "model", "gemini-2.0-flash",
                    "contents", List.of(
                            Map.of("role", "user", "parts", List.of(Map.of("text", "Hi")))
                    ),
                    "tool_config", Map.of(
                            "function_calling_config", Map.of("mode", "AUTO")
                    )
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getToolChoice()).isNotNull();
            assertThat(result.getToolChoice().getType()).isEqualTo("auto");
        }

        @Test
        @DisplayName("生成配置解析")
        void parse_generationConfig() {
            Map<String, Object> request = Map.of(
                    "model", "gemini-2.0-flash",
                    "contents", List.of(
                            Map.of("role", "user", "parts", List.of(Map.of("text", "Hi")))
                    ),
                    "temperature", 0.5,
                    "topP", 0.8,
                    "maxOutputTokens", 512
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getGenerationConfig().getTemperature()).isEqualTo(0.5);
            assertThat(result.getGenerationConfig().getTopP()).isEqualTo(0.8);
            assertThat(result.getGenerationConfig().getMaxOutputTokens()).isEqualTo(512);
        }

        @Test
        @DisplayName("functionCall 块解析为 UnifiedToolCall")
        void parse_functionCallBlock() {
            Map<String, Object> request = Map.of(
                    "model", "gemini-2.0-flash",
                    "contents", List.of(
                            Map.of("role", "user", "parts", List.of(Map.of("text", "Search"))),
                            Map.of("role", "model", "parts", List.of(
                                    Map.of("functionCall", Map.of("name", "search", "args", Map.of("q", "test")))
                            ))
                    )
            );

            UnifiedRequest result = adapter.parse(request);

            // model 消息包含 functionCall，应生成 toolCalls 消息
            assertThat(result.getMessages().stream()
                    .anyMatch(m -> m.getToolCalls() != null && !m.getToolCalls().isEmpty()))
                    .isTrue();
        }

        @Test
        @DisplayName("function 角色映射为 tool")
        void parse_functionRoleMapsToTool() {
            Map<String, Object> request = Map.of(
                    "model", "gemini-2.0-flash",
                    "contents", List.of(
                            Map.of("role", "user", "parts", List.of(Map.of("text", "Search"))),
                            Map.of("role", "model", "parts", List.of(
                                    Map.of("functionCall", Map.of("name", "search", "args", Map.of()))
                            )),
                            Map.of("role", "function", "parts", List.of(
                                    Map.of("functionResponse", Map.of("name", "search", "response", Map.of("result", "ok")))
                            ))
                    )
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getMessages().stream()
                    .anyMatch(m -> "tool".equals(m.getRole())))
                    .isTrue();
        }

        @Test
        @DisplayName("缺少 model 字段抛出 ProtocolException")
        void parse_missingModel_throwsException() {
            Map<String, Object> request = Map.of(
                    "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", "Hi"))))
            );

            assertThatThrownBy(() -> adapter.parse(request))
                    .isInstanceOf(ProtocolException.class);
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
            UnifiedResponse response = buildTextResponse("Hello!", "gemini-1");

            Object result = adapter.encodeResponse(response);

            Map<String, Object> map = (Map<String, Object>) result;
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) map.get("candidates");
            assertThat(candidates).hasSize(1);

            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            assertThat(content.get("role")).isEqualTo("model");

            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            assertThat(parts).hasSize(1);
            assertThat(parts.get(0).get("text")).isEqualTo("Hello!");

            assertThat(candidates.get(0).get("finishReason")).isEqualTo("STOP");
        }

        @Test
        @DisplayName("工具调用响应编码")
        @SuppressWarnings("unchecked")
        void encodeResponse_toolCallResponse() {
            UnifiedResponse response = new UnifiedResponse();
            response.setModel("gemini-2.0-flash");
            response.setFinishReason("tool_calls");

            UnifiedToolCall toolCall = new UnifiedToolCall();
            toolCall.setId("fc_0");
            toolCall.setType("function");
            toolCall.setToolName("search");
            toolCall.setArgumentsJson("{\"q\":\"test\"}");

            UnifiedOutput output = new UnifiedOutput();
            output.setToolCalls(List.of(toolCall));
            response.setOutputs(List.of(output));

            Object result = adapter.encodeResponse(response);
            Map<String, Object> map = (Map<String, Object>) result;

            List<Map<String, Object>> candidates = (List<Map<String, Object>>) map.get("candidates");
            List<Map<String, Object>> parts = (List<Map<String, Object>>)
                    ((Map<String, Object>) candidates.get(0).get("content")).get("parts");

            // 应包含 functionCall 部分
            assertThat(parts.stream().anyMatch(p -> p.containsKey("functionCall"))).isTrue();
        }

        @Test
        @DisplayName("包含 usageMetadata 的响应编码")
        @SuppressWarnings("unchecked")
        void encodeResponse_withUsageMetadata() {
            UnifiedResponse response = buildTextResponse("Hi", "gemini-2");
            UnifiedUsage usage = new UnifiedUsage();
            usage.setInputTokens(20);
            usage.setOutputTokens(10);
            usage.setTotalTokens(30);
            response.setUsage(usage);

            Object result = adapter.encodeResponse(response);
            Map<String, Object> map = (Map<String, Object>) result;

            Map<String, Object> usageMeta = (Map<String, Object>) map.get("usageMetadata");
            assertThat(usageMeta).isNotNull();
            assertThat(usageMeta.get("promptTokenCount")).isEqualTo(20);
            assertThat(usageMeta.get("candidatesTokenCount")).isEqualTo(10);
            assertThat(usageMeta.get("totalTokenCount")).isEqualTo(30);
        }
    }

    // ===================== 流式编码 =====================

    @Nested
    @DisplayName("encodeStreamEvent 流式编码")
    class EncodeStreamEventTests {

        private StreamEncodeContext ctx;

        @BeforeEach
        void initContext() {
            ctx = new StreamEncodeContext("gemini-001", 1700000000L, "gemini-2.0-flash");
        }

        @Test
        @DisplayName("text_delta 编码")
        void encodeStreamEvent_textDelta() {
            UnifiedStreamEvent event = UnifiedStreamEvent.textDelta("Hello");

            List<EncodedEvent> events = adapter.encodeStreamEvent(event, ctx);

            assertThat(events).hasSize(1);
            assertThat(events.get(0).data()).contains("Hello");
            assertThat(events.get(0).data()).contains("candidates");
        }

        @Test
        @DisplayName("done 事件被静默丢弃")
        void encodeStreamEvent_done_discarded() {
            UnifiedStreamEvent event = UnifiedStreamEvent.done("stop");

            List<EncodedEvent> events = adapter.encodeStreamEvent(event, ctx);

            assertThat(events).isEmpty();
        }

        @Test
        @DisplayName("thinking_delta 事件被静默丢弃")
        void encodeStreamEvent_thinkingDelta_discarded() {
            UnifiedStreamEvent event = UnifiedStreamEvent.thinkingDelta("thinking...");

            List<EncodedEvent> events = adapter.encodeStreamEvent(event, ctx);

            assertThat(events).isEmpty();
        }

        @Test
        @DisplayName("tool_call 事件编码")
        void encodeStreamEvent_toolCall() {
            UnifiedStreamEvent event = UnifiedStreamEvent.toolCall("fc_001", "search", null);

            List<EncodedEvent> events = adapter.encodeStreamEvent(event, ctx);

            assertThat(events).hasSize(1);
            assertThat(events.get(0).data()).contains("functionCall");
            assertThat(events.get(0).data()).contains("search");
        }

        @Test
        @DisplayName("tool_call_delta 事件编码")
        void encodeStreamEvent_toolCallDelta() {
            UnifiedStreamEvent event = UnifiedStreamEvent.toolCallDelta(0, "{\"q\":\"test\"}");

            List<EncodedEvent> events = adapter.encodeStreamEvent(event, ctx);

            assertThat(events).hasSize(1);
            assertThat(events.get(0).data()).contains("functionCall");
        }

        @Test
        @DisplayName("initialStreamEvents 返回空列表")
        void initialStreamEvents_returnsEmpty() {
            assertThat(adapter.initialStreamEvents(ctx)).isEmpty();
        }

        @Test
        @DisplayName("terminalStreamEvents 返回空列表")
        void terminalStreamEvents_returnsEmpty() {
            assertThat(adapter.terminalStreamEvents(ctx)).isEmpty();
        }
    }

    // ===================== 错误编码 =====================

    @Nested
    @DisplayName("buildError 错误编码")
    class BuildErrorTests {

        @Test
        @DisplayName("Gemini 错误格式")
        @SuppressWarnings("unchecked")
        void buildError_geminiFormat() {
            Object result = adapter.buildError("invalid argument", "INVALID_ARGUMENT", "INVALID_REQUEST", null);

            Map<String, Object> map = (Map<String, Object>) result;
            // Gemini 错误格式：{error:{code, message, status}}，无顶层 type 字段
            assertThat(map).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) map.get("error");
            assertThat(error.get("message")).isEqualTo("invalid argument");
            assertThat(error.get("status")).isEqualTo("INVALID_ARGUMENT");
            // code 字段为 Integer 类型的 HTTP 状态码
            assertThat(error.get("code")).isEqualTo(400);
        }

        @Test
        @DisplayName("mapErrorType 各错误码映射")
        void mapErrorType_variousCodes() {
            assertThat(adapter.mapErrorType(ErrorCode.INVALID_REQUEST)).isEqualTo("INVALID_ARGUMENT");
            assertThat(adapter.mapErrorType(ErrorCode.AUTH_FAILED)).isEqualTo("UNAUTHENTICATED");
            assertThat(adapter.mapErrorType(ErrorCode.RATE_LIMITED)).isEqualTo("RESOURCE_EXHAUSTED");
            assertThat(adapter.mapErrorType(ErrorCode.INTERNAL_ERROR)).isEqualTo("INTERNAL");
            assertThat(adapter.mapErrorType(ErrorCode.MODEL_NOT_FOUND)).isEqualTo("INVALID_ARGUMENT");
        }
    }

    // ===================== 辅助方法 =====================

    /** 构建文本响应 */
    private UnifiedResponse buildTextResponse(String text, String id) {
        UnifiedResponse response = new UnifiedResponse();
        response.setId(id);
        response.setModel("gemini-2.0-flash");
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
