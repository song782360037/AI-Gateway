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
 * Anthropic Messages API 协议适配器测试
 */
class AnthropicProtocolAdapterTest {

    private AnthropicProtocolAdapter adapter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        adapter = new AnthropicProtocolAdapter(objectMapper);
    }

    // ===================== 基本属性 =====================

    @Test
    @DisplayName("getProtocolType 返回 ANTHROPIC")
    void getProtocolType_shouldReturnAnthropic() {
        assertThat(adapter.getProtocolType()).isEqualTo(ProtocolType.ANTHROPIC);
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
                    "model", "claude-3-5-sonnet-20241022",
                    "messages", List.of(
                            Map.of("role", "user", "content", "Hello")
                    ),
                    "max_tokens", 1024
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getModel()).isEqualTo("claude-3-5-sonnet-20241022");
            assertThat(result.getRequestProtocol()).isEqualTo("anthropic");
            assertThat(result.getResponseProtocol()).isEqualTo("anthropic");
            assertThat(result.getGenerationConfig().getMaxOutputTokens()).isEqualTo(1024);
            assertThat(result.getMessages()).hasSize(1);
        }

        @Test
        @DisplayName("system 字符串格式提取")
        void parse_systemString() {
            Map<String, Object> request = Map.of(
                    "model", "claude-3-5-sonnet-20241022",
                    "system", "You are a helpful assistant.",
                    "messages", List.of(
                            Map.of("role", "user", "content", "Hi")
                    ),
                    "max_tokens", 1024
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getSystemPrompt()).isEqualTo("You are a helpful assistant.");
        }

        @Test
        @DisplayName("system 数组格式提取")
        void parse_systemArray() {
            Map<String, Object> request = Map.of(
                    "model", "claude-3-5-sonnet-20241022",
                    "system", List.of(
                            Map.of("type", "text", "text", "Rule 1."),
                            Map.of("type", "text", "text", "Rule 2.")
                    ),
                    "messages", List.of(
                            Map.of("role", "user", "content", "Hi")
                    ),
                    "max_tokens", 1024
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getSystemPrompt()).isEqualTo("Rule 1.\nRule 2.");
        }

        @Test
        @DisplayName("工具定义解析（使用 input_schema）")
        void parse_toolsWithInputSchema() {
            Map<String, Object> request = Map.of(
                    "model", "claude-3-5-sonnet-20241022",
                    "messages", List.of(
                            Map.of("role", "user", "content", "Search")
                    ),
                    "max_tokens", 1024,
                    "tools", List.of(
                            Map.of(
                                    "name", "web_search",
                                    "description", "Search the web",
                                    "input_schema", Map.of("type", "object")
                            )
                    )
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getTools()).hasSize(1);
            assertThat(result.getTools().get(0).getName()).isEqualTo("web_search");
            assertThat(result.getTools().get(0).getInputSchema()).containsEntry("type", "object");
        }

        @Test
        @DisplayName("tool_choice auto 解析")
        void parse_toolChoiceAuto() {
            Map<String, Object> request = Map.of(
                    "model", "claude-3-5-sonnet-20241022",
                    "messages", List.of(Map.of("role", "user", "content", "Hi")),
                    "max_tokens", 1024,
                    "tool_choice", "auto"
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getToolChoice()).isNotNull();
            assertThat(result.getToolChoice().getType()).isEqualTo("auto");
        }

        @Test
        @DisplayName("tool_choice 对象格式解析（指定工具名）")
        void parse_toolChoiceObject() {
            Map<String, Object> request = Map.of(
                    "model", "claude-3-5-sonnet-20241022",
                    "messages", List.of(Map.of("role", "user", "content", "Hi")),
                    "max_tokens", 1024,
                    "tool_choice", Map.of("type", "tool", "name", "my_func")
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getToolChoice()).isNotNull();
            assertThat(result.getToolChoice().getType()).isEqualTo("specific");
            assertThat(result.getToolChoice().getToolName()).isEqualTo("my_func");
        }

        @Test
        @DisplayName("thinking 配置解析")
        void parse_thinkingConfig() {
            Map<String, Object> request = Map.of(
                    "model", "claude-3-5-sonnet-20241022",
                    "messages", List.of(Map.of("role", "user", "content", "Hi")),
                    "max_tokens", 1024,
                    "thinking", Map.of("type", "enabled", "budget_tokens", 5000)
            );

            UnifiedRequest result = adapter.parse(request);

            assertThat(result.getGenerationConfig().getReasoning()).isNotNull();
            assertThat(result.getGenerationConfig().getReasoning().getEnabled()).isTrue();
            assertThat(result.getGenerationConfig().getReasoning().getBudgetTokens()).isEqualTo(5000);
        }

        @Test
        @DisplayName("assistant 消息中 tool_use 块解析")
        void parse_assistantToolUse() {
            Map<String, Object> request = Map.of(
                    "model", "claude-3-5-sonnet-20241022",
                    "messages", List.of(
                            Map.of(
                                    "role", "assistant",
                                    "content", List.of(
                                            Map.of("type", "text", "text", "Let me search"),
                                            Map.of("type", "tool_use", "id", "tu_1", "name", "search",
                                                    "input", Map.of("q", "test"))
                                    )
                            )
                    ),
                    "max_tokens", 1024
            );

            UnifiedRequest result = adapter.parse(request);

            // assistant 消息合并为一条，同时包含 text parts 和 toolCalls
            assertThat(result.getMessages()).hasSize(1);
            UnifiedMessage assistantMsg = result.getMessages().get(0);
            assertThat(assistantMsg.getRole()).isEqualTo("assistant");
            assertThat(assistantMsg.getParts()).isNotEmpty();
            assertThat(assistantMsg.getToolCalls()).isNotEmpty();
        }

        @Test
        @DisplayName("assistant 消息中仅有 tool_use（无 text/thinking）时保持单条消息")
        void parse_assistantToolUseOnly_noText() {
            Map<String, Object> request = Map.of(
                    "model", "claude-3-5-sonnet-20241022",
                    "messages", List.of(
                            Map.of(
                                    "role", "assistant",
                                    "content", List.of(
                                            Map.of("type", "tool_use", "id", "tu_1", "name", "search",
                                                    "input", Map.of("q", "test")),
                                            Map.of("type", "tool_use", "id", "tu_2", "name", "read",
                                                    "input", Map.of("path", "/tmp"))
                                    )
                            )
                    ),
                    "max_tokens", 1024
            );

            UnifiedRequest result = adapter.parse(request);

            // 回归测试：tool_use-only 消息必须保持在单条 assistant 消息中，
            // 不能拆分成独立消息（否则 DeepSeek 等 Provider 会因缺少 thinking 块返回 400）
            assertThat(result.getMessages()).hasSize(1);
            UnifiedMessage assistantMsg = result.getMessages().get(0);
            assertThat(assistantMsg.getRole()).isEqualTo("assistant");
            assertThat(assistantMsg.getToolCalls()).hasSize(2);
        }

        @Test
        @DisplayName("tool_choice 无效字符串抛出异常")
        void parse_invalidToolChoice_throwsException() {
            Map<String, Object> request = Map.of(
                    "model", "claude-3-5-sonnet-20241022",
                    "messages", List.of(Map.of("role", "user", "content", "Hi")),
                    "max_tokens", 1024,
                    "tool_choice", "invalid"
            );

            assertThatThrownBy(() -> adapter.parse(request))
                    .isInstanceOf(ProtocolException.class)
                    .satisfies(ex -> {
                        ProtocolException pe = (ProtocolException) ex;
                        assertThat(pe.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                        assertThat(pe.getParam()).isEqualTo("tool_choice");
                    });
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
            UnifiedResponse response = buildTextResponse("Bonjour!", "msg-123", "claude-3-5-sonnet-20241022");

            Object result = adapter.encodeResponse(response);

            Map<String, Object> map = (Map<String, Object>) result;
            assertThat(map.get("id")).isEqualTo("msg-123");
            assertThat(map.get("type")).isEqualTo("message");
            assertThat(map.get("role")).isEqualTo("assistant");
            assertThat(map.get("model")).isEqualTo("claude-3-5-sonnet-20241022");
            assertThat(map.get("stop_reason")).isEqualTo("end_turn");

            List<Map<String, Object>> content = (List<Map<String, Object>>) map.get("content");
            assertThat(content).hasSize(1);
            assertThat(content.get(0).get("type")).isEqualTo("text");
            assertThat(content.get(0).get("text")).isEqualTo("Bonjour!");
        }

        @Test
        @DisplayName("工具调用响应编码")
        @SuppressWarnings("unchecked")
        void encodeResponse_toolCallResponse() {
            UnifiedResponse response = new UnifiedResponse();
            response.setId("msg-456");
            response.setModel("claude-3-5-sonnet-20241022");
            response.setFinishReason("tool_calls");

            UnifiedToolCall toolCall = new UnifiedToolCall();
            toolCall.setId("tu_001");
            toolCall.setType("function");
            toolCall.setToolName("search");
            toolCall.setArgumentsJson("{\"q\":\"test\"}");

            UnifiedOutput output = new UnifiedOutput();
            output.setToolCalls(List.of(toolCall));
            response.setOutputs(List.of(output));

            Object result = adapter.encodeResponse(response);
            Map<String, Object> map = (Map<String, Object>) result;

            List<Map<String, Object>> content = (List<Map<String, Object>>) map.get("content");
            assertThat(content).hasSize(1);
            assertThat(content.get(0).get("type")).isEqualTo("tool_use");
            assertThat(content.get(0).get("id")).isEqualTo("tu_001");
            assertThat(content.get(0).get("name")).isEqualTo("search");
            assertThat(content.get(0).get("input")).isInstanceOf(Map.class);
        }

        @Test
        @DisplayName("stop_reason 映射正确")
        @SuppressWarnings("unchecked")
        void encodeResponse_stopReasonMapping() {
            UnifiedResponse response = buildTextResponse("Hi", "msg-789", "claude-3");
            response.setFinishReason("length");

            Object result = adapter.encodeResponse(response);
            Map<String, Object> map = (Map<String, Object>) result;

            assertThat(map.get("stop_reason")).isEqualTo("max_tokens");
        }
    }

    // ===================== 流式编码 =====================

    @Nested
    @DisplayName("encodeStreamEvent 流式编码")
    class EncodeStreamEventTests {

        private StreamEncodeContext ctx;

        @BeforeEach
        void initContext() {
            ctx = new StreamEncodeContext("msg-001", 1700000000L, "claude-3-5-sonnet-20241022");
        }

        @Test
        @DisplayName("initialStreamEvents 延迟生成 message_start，返回空列表")
        void initialStreamEvents_deferred() {
            List<EncodedEvent> events = adapter.initialStreamEvents(ctx);

            // message_start 延迟到首个事件到达时生成，以便包含真实的 input_tokens 和 cache_read_input_tokens
            assertThat(events).isEmpty();
        }

        @Test
        @DisplayName("首个事件到达时自动生成 message_start")
        void encodeStreamEvent_firstEvent_generatesMessageStart() {
            UnifiedStreamEvent event = UnifiedStreamEvent.textDelta("Hello");

            List<EncodedEvent> events = adapter.encodeStreamEvent(event, ctx);

            // 首个事件应先发送 message_start，再发送内容事件
            assertThat(events).hasSizeGreaterThanOrEqualTo(3);
            assertThat(events.get(0).eventName()).isEqualTo("message_start");
            assertThat(events.get(0).data()).contains("msg-001");
        }

        @Test
        @DisplayName("usage_only 事件仅触发 message_start，不产生额外 SSE 事件")
        void encodeStreamEvent_usageOnly_generatesMessageStart() {
            UnifiedStreamEvent usageEvent = new UnifiedStreamEvent();
            usageEvent.setType(UnifiedStreamEvent.TYPE_USAGE_ONLY);
            UnifiedUsage usage = new UnifiedUsage();
            usage.setInputTokens(100);
            usage.setCachedInputTokens(50);
            usageEvent.setUsage(usage);

            List<EncodedEvent> events = adapter.encodeStreamEvent(usageEvent, ctx);

            // usage_only 事件仅触发 message_start
            assertThat(events).hasSize(1);
            assertThat(events.get(0).eventName()).isEqualTo("message_start");
            // input_tokens = inputTokens - cachedInputTokens = 100 - 50 = 50（非 Anthropic 上游，rawInputTokens 未设置）
            assertThat(events.get(0).data()).contains("\"input_tokens\":50");
            assertThat(events.get(0).data()).contains("\"cache_read_input_tokens\":50");
        }

        @Test
        @DisplayName("usage_only 事件携带 cache_creation_input_tokens 时正确编码到 message_start")
        void encodeStreamEvent_usageOnly_withCacheCreationInputTokens() {
            UnifiedStreamEvent usageEvent = new UnifiedStreamEvent();
            usageEvent.setType(UnifiedStreamEvent.TYPE_USAGE_ONLY);
            UnifiedUsage usage = new UnifiedUsage();
            usage.setInputTokens(100);
            usage.setCachedInputTokens(50);
            usage.setCacheCreationInputTokens(30);
            usageEvent.setUsage(usage);

            List<EncodedEvent> events = adapter.encodeStreamEvent(usageEvent, ctx);

            // usage_only 事件仅触发 message_start
            assertThat(events).hasSize(1);
            assertThat(events.get(0).eventName()).isEqualTo("message_start");
            // input_tokens = 100 - 50 - 30 = 20（总量减去缓存部分）
            assertThat(events.get(0).data()).contains("\"input_tokens\":20");
            assertThat(events.get(0).data()).contains("\"cache_read_input_tokens\":50");
            assertThat(events.get(0).data()).contains("\"cache_creation_input_tokens\":30");
        }

        @Test
        @DisplayName("done 之后 usage_only 到达，补发 message_delta 携带完整 usage")
        void encodeStreamEvent_usageOnly_afterDone_sendsMessageDelta() {
            // 先发一个 text_delta 以生成 message_start 并打开 content block
            adapter.encodeStreamEvent(UnifiedStreamEvent.textDelta("Hello"), ctx);
            // done 不携带 usage（模拟 OpenAI Chat Completions usage 延迟到达）
            adapter.encodeStreamEvent(UnifiedStreamEvent.done("stop"), ctx);

            // usage_only 事件延迟到达
            UnifiedStreamEvent usageEvent = new UnifiedStreamEvent();
            usageEvent.setType(UnifiedStreamEvent.TYPE_USAGE_ONLY);
            UnifiedUsage lateUsage = new UnifiedUsage();
            lateUsage.setOutputTokens(10);
            lateUsage.setCachedInputTokens(50);
            lateUsage.setInputTokens(100);
            usageEvent.setUsage(lateUsage);

            List<EncodedEvent> events = adapter.encodeStreamEvent(usageEvent, ctx);

            // 应补发一个 message_delta 携带完整 usage
            assertThat(events).hasSize(1);
            assertThat(events.get(0).eventName()).isEqualTo("message_delta");
            assertThat(events.get(0).data()).contains("message_delta");
            assertThat(events.get(0).data()).contains("\"output_tokens\":10");
            assertThat(events.get(0).data()).contains("\"cache_read_input_tokens\":50");
        }

        @Test
        @DisplayName("done 之后 usage_only 到达，若 outputTokens 已发送则不补发")
        void encodeStreamEvent_usageOnly_afterDone_withOutputTokensSent_noop() {
            // 先发一个 text_delta 以生成 message_start
            adapter.encodeStreamEvent(UnifiedStreamEvent.textDelta("Hello"), ctx);
            // 发送 done 事件（携带 usage，outputTokensSent 会被标记为 true）
            UnifiedUsage doneUsage = new UnifiedUsage();
            doneUsage.setOutputTokens(10);
            adapter.encodeStreamEvent(UnifiedStreamEvent.done("stop", doneUsage), ctx);

            // 再次收到 usage_only
            UnifiedStreamEvent usageEvent = new UnifiedStreamEvent();
            usageEvent.setType(UnifiedStreamEvent.TYPE_USAGE_ONLY);
            UnifiedUsage usage = new UnifiedUsage();
            usage.setOutputTokens(10);
            usageEvent.setUsage(usage);

            List<EncodedEvent> events = adapter.encodeStreamEvent(usageEvent, ctx);

            // 已发送过 outputTokens，不补发
            assertThat(events).isEmpty();
        }

        @Test
        @DisplayName("done 未处理时 usage_only 仅触发 message_start，不补发 message_delta")
        void encodeStreamEvent_usageOnly_beforeDone_noMessageDelta() {
            UnifiedStreamEvent usageEvent = new UnifiedStreamEvent();
            usageEvent.setType(UnifiedStreamEvent.TYPE_USAGE_ONLY);
            UnifiedUsage usage = new UnifiedUsage();
            usage.setInputTokens(100);
            usage.setCachedInputTokens(50);
            usageEvent.setUsage(usage);

            List<EncodedEvent> events = adapter.encodeStreamEvent(usageEvent, ctx);

            // done 未处理，仅触发 message_start，不补发 message_delta
            assertThat(events).hasSize(1);
            assertThat(events.get(0).eventName()).isEqualTo("message_start");
        }

        @Test
        @DisplayName("text_delta 编码生成 message_start + content_block_start + content_block_delta")
        void encodeStreamEvent_textDelta() {
            UnifiedStreamEvent event = UnifiedStreamEvent.textDelta("Hello");

            List<EncodedEvent> events = adapter.encodeStreamEvent(event, ctx);

            // 首次 text_delta：message_start + content_block_start + content_block_delta
            assertThat(events).hasSize(3);
            assertThat(events.get(0).eventName()).isEqualTo("message_start");
            assertThat(events.get(1).eventName()).isEqualTo("content_block_start");
            assertThat(events.get(2).eventName()).isEqualTo("content_block_delta");
            assertThat(events.get(2).data()).contains("text_delta");
            assertThat(events.get(2).data()).contains("Hello");
        }

        @Test
        @DisplayName("连续 text_delta 不重复创建 message_start 和 content_block_start")
        void encodeStreamEvent_consecutiveTextDelta() {
            // 第一次 text_delta（包含 message_start）
            adapter.encodeStreamEvent(UnifiedStreamEvent.textDelta("Hello"), ctx);
            // 第二次 text_delta
            List<EncodedEvent> events = adapter.encodeStreamEvent(UnifiedStreamEvent.textDelta(" World"), ctx);

            // 已经有 text 块打开，只需发送 delta
            assertThat(events).hasSize(1);
            assertThat(events.get(0).eventName()).isEqualTo("content_block_delta");
        }

        @Test
        @DisplayName("thinking_delta 编码生成 message_start + content_block_start(thinking) + delta")
        void encodeStreamEvent_thinkingDelta() {
            UnifiedStreamEvent event = UnifiedStreamEvent.thinkingDelta("thinking...");

            List<EncodedEvent> events = adapter.encodeStreamEvent(event, ctx);

            assertThat(events).hasSize(3);
            assertThat(events.get(0).eventName()).isEqualTo("message_start");
            assertThat(events.get(1).eventName()).isEqualTo("content_block_start");
            assertThat(events.get(2).eventName()).isEqualTo("content_block_delta");
            assertThat(events.get(2).data()).contains("thinking_delta");
        }

        @Test
        @DisplayName("tool_call 编码生成 message_start + content_block_start(tool_use)")
        void encodeStreamEvent_toolCall() {
            UnifiedStreamEvent event = UnifiedStreamEvent.toolCall("tu_001", "search", null);

            List<EncodedEvent> events = adapter.encodeStreamEvent(event, ctx);

            assertThat(events).hasSize(2);
            assertThat(events.get(0).eventName()).isEqualTo("message_start");
            assertThat(events.get(1).eventName()).isEqualTo("content_block_start");
            assertThat(events.get(1).data()).contains("tool_use");
            assertThat(events.get(1).data()).contains("search");
        }

        @Test
        @DisplayName("tool_call_delta 编码生成 input_json_delta")
        void encodeStreamEvent_toolCallDelta() {
            // 先打开一个 tool_use 块（同时触发 message_start）
            adapter.encodeStreamEvent(UnifiedStreamEvent.toolCall("tu_001", "search", null), ctx);

            UnifiedStreamEvent delta = UnifiedStreamEvent.toolCallDelta(0, "{\"q\":");
            List<EncodedEvent> events = adapter.encodeStreamEvent(delta, ctx);

            // message_start 已发送，后续事件只产生 content_block_delta
            assertThat(events).hasSize(1);
            assertThat(events.get(0).eventName()).isEqualTo("content_block_delta");
            assertThat(events.get(0).data()).contains("input_json_delta");
        }

        @Test
        @DisplayName("done 事件生成 message_delta + 关闭打开的块")
        void encodeStreamEvent_done() {
            // 先打开一个 text 块（同时触发 message_start）
            adapter.encodeStreamEvent(UnifiedStreamEvent.textDelta("Hi"), ctx);

            UnifiedStreamEvent done = UnifiedStreamEvent.done("stop");
            List<EncodedEvent> events = adapter.encodeStreamEvent(done, ctx);

            // 应包含 content_block_stop + message_delta
            assertThat(events).hasSizeGreaterThanOrEqualTo(1);
            assertThat(events.get(events.size() - 1).eventName()).isEqualTo("message_delta");
        }

        @Test
        @DisplayName("terminalStreamEvents 生成 message_stop")
        void terminalStreamEvents_generatesMessageStop() {
            List<EncodedEvent> events = adapter.terminalStreamEvents(ctx);

            assertThat(events).hasSize(1);
            assertThat(events.get(0).eventName()).isEqualTo("message_stop");
        }
    }

    // ===================== 错误编码 =====================

    @Nested
    @DisplayName("buildError 错误编码")
    class BuildErrorTests {

        @Test
        @DisplayName("Anthropic 错误格式")
        @SuppressWarnings("unchecked")
        void buildError_anthropicFormat() {
            Object result = adapter.buildError("invalid request", "invalid_request_error", "INVALID_REQUEST", null);

            Map<String, Object> map = (Map<String, Object>) result;
            assertThat(map.get("type")).isEqualTo("error");
            Map<String, Object> error = (Map<String, Object>) map.get("error");
            assertThat(error.get("type")).isEqualTo("invalid_request_error");
            assertThat(error.get("message")).isEqualTo("invalid request");
        }

        @Test
        @DisplayName("mapErrorType 各错误码映射")
        void mapErrorType_variousCodes() {
            assertThat(adapter.mapErrorType(ErrorCode.INVALID_REQUEST)).isEqualTo("invalid_request_error");
            assertThat(adapter.mapErrorType(ErrorCode.AUTH_FAILED)).isEqualTo("authentication_error");
            assertThat(adapter.mapErrorType(ErrorCode.RATE_LIMITED)).isEqualTo("rate_limit_error");
            assertThat(adapter.mapErrorType(ErrorCode.INTERNAL_ERROR)).isEqualTo("api_error");
        }
    }

    // ===================== 辅助方法 =====================

    /** 构建文本响应 */
    private UnifiedResponse buildTextResponse(String text, String id, String model) {
        UnifiedResponse response = new UnifiedResponse();
        response.setId(id);
        response.setModel(model);
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
