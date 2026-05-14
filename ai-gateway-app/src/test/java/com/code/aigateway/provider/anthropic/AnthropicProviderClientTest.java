package com.code.aigateway.provider.anthropic;

import com.code.aigateway.config.GatewayProperties;
import com.code.aigateway.core.capability.ReasoningSemanticMapper;
import com.code.aigateway.sdk.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.sdk.model.UnifiedGenerationConfig;
import com.code.aigateway.sdk.model.UnifiedMessage;
import com.code.aigateway.sdk.model.UnifiedPart;
import com.code.aigateway.sdk.model.UnifiedRequest;
import com.code.aigateway.sdk.model.UnifiedResponse;
import com.code.aigateway.sdk.model.UnifiedStreamEvent;
import com.code.aigateway.sdk.model.UnifiedTool;
import com.code.aigateway.sdk.model.UnifiedToolCall;
import com.code.aigateway.sdk.model.UnifiedToolChoice;

import com.code.aigateway.provider.ProviderType;
import com.code.aigateway.provider.util.ProviderTestUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Anthropic 提供商客户端单元测试
 * <p>
 * 使用 JDK HttpServer 模拟上游 Anthropic Messages API。
 * </p>
 */
class AnthropicProviderClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer httpServer;
    private AnthropicProviderClient providerClient;
    private AtomicReference<String> requestPath;
    private AtomicReference<String> apiKeyHeader;
    private AtomicReference<String> anthropicVersionHeader;
    private AtomicReference<String> requestBody;

    @BeforeEach
    void setUp() {
        requestPath = new AtomicReference<>();
        apiKeyHeader = new AtomicReference<>();
        anthropicVersionHeader = new AtomicReference<>();
        requestBody = new AtomicReference<>();
    }

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    // ==================== 1. 非流式文本响应解析 ====================

    @Test
    void chat_textResponse_returnsUnifiedResponse() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "id": "msg_123",
                      "type": "message",
                      "role": "assistant",
                      "model": "claude-3-5-sonnet-20241022",
                      "content": [
                        {"type": "text", "text": "你好，这是 Anthropic 的文本响应"}
                      ],
                      "stop_reason": "end_turn",
                      "usage": {"input_tokens": 12, "output_tokens": 8}
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildBasicRequest(false)))
                .assertNext(response -> {
                    assertEquals("msg_123", response.getId());
                    assertEquals("claude-3-5-sonnet-20241022", response.getModel());
                    assertEquals("anthropic", response.getProvider());
                    assertEquals("stop", response.getFinishReason());
                    assertNotNull(response.getUsage());
                    assertEquals(12, response.getUsage().getInputTokens());
                    assertEquals(8, response.getUsage().getOutputTokens());
                    assertEquals(20, response.getUsage().getTotalTokens());
                    assertEquals("assistant", response.getOutputs().getFirst().getRole());
                    assertEquals("你好，这是 Anthropic 的文本响应",
                            response.getOutputs().getFirst().getParts().getFirst().getText());
                    assertNull(response.getOutputs().getFirst().getToolCalls());
                })
                .verifyComplete();

        // 验证请求头
        assertEquals("/v1/messages", requestPath.get());
        assertEquals("test-anthropic-key", apiKeyHeader.get());
        assertEquals("2023-06-01", anthropicVersionHeader.get());
    }

    // ==================== 2. 非流式工具调用解析 ====================

    @Test
    void chat_toolUseResponse_parsesToolCalls() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "id": "msg_tool_1",
                      "type": "message",
                      "role": "assistant",
                      "model": "claude-3-5-sonnet-20241022",
                      "content": [
                        {
                          "type": "tool_use",
                          "id": "toolu_123",
                          "name": "get_weather",
                          "input": {"city": "Shanghai", "unit": "celsius"}
                        }
                      ],
                      "stop_reason": "tool_use",
                      "usage": {"input_tokens": 21, "output_tokens": 13}
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildBasicRequest(false)))
                .assertNext(response -> {
                    assertEquals("tool_calls", response.getFinishReason());
                    assertNotNull(response.getOutputs().getFirst().getToolCalls());
                    assertEquals(1, response.getOutputs().getFirst().getToolCalls().size());

                    UnifiedToolCall tc = response.getOutputs().getFirst().getToolCalls().getFirst();
                    assertEquals("toolu_123", tc.getId());
                    assertEquals("function", tc.getType());
                    assertEquals("get_weather", tc.getToolName());
                    assertTrue(tc.getArgumentsJson().contains("Shanghai"));
                    assertEquals(34, response.getUsage().getTotalTokens());
                })
                .verifyComplete();
    }

    // ==================== 3. 流式文本响应解析 ====================

    @Test
    void streamChat_textResponse_parsesDeltaAndDone() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.TEXT_EVENT_STREAM_VALUE, """
                    data: {"type":"message_start","message":{"id":"msg_s1","type":"message","role":"assistant","model":"claude-3-5-sonnet-20241022","usage":{"input_tokens":17}}}

                    data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"你好"}}

                    data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"，世界"}}

                    data: {"type":"message_delta","delta":{"stop_reason":"max_tokens"},"usage":{"output_tokens":9}}

                    data: {"type":"message_stop"}

                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.streamChat(buildBasicRequest(true)))
                .assertNext(event -> {
                    // message_start 事件产生 usage_only 事件，携带初始 input_tokens
                    assertEquals(UnifiedStreamEvent.TYPE_USAGE_ONLY, event.getType());
                    assertEquals(17, event.getUsage().getInputTokens());
                    // 无缓存时 rawInputTokens 等于 inputTokens
                    assertEquals(17, event.getUsage().getRawInputTokens());
                })
                .assertNext(event -> {
                    assertEquals("text_delta", event.getType());
                    assertEquals("你好", event.getTextDelta());
                })
                .assertNext(event -> {
                    assertEquals("text_delta", event.getType());
                    assertEquals("，世界", event.getTextDelta());
                })
                .assertNext(event -> {
                    assertEquals("done", event.getType());
                    assertEquals("length", event.getFinishReason());
                    assertEquals(17, event.getUsage().getInputTokens());
                    assertEquals(17, event.getUsage().getRawInputTokens());
                    assertEquals(9, event.getUsage().getOutputTokens());
                })
                .verifyComplete();
    }

    @Test
    void streamChat_inputTokensAppearInMessageDelta_keepsUsageComplete() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.TEXT_EVENT_STREAM_VALUE, """
                    data: {"type":"message_start","message":{"id":"msg_s2","type":"message","role":"assistant","model":"claude-sonnet-4-20250514","usage":{}}}

                    data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"hello"}}

                    data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"input_tokens":23,"output_tokens":7}}

                    data: {"type":"message_stop"}

                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.streamChat(buildBasicRequest(true)))
                .assertNext(event -> {
                    // message_start 事件产生 usage_only 事件（此时 usage 为空，input_tokens 在 message_delta 中延迟出现）
                    assertEquals(UnifiedStreamEvent.TYPE_USAGE_ONLY, event.getType());
                })
                .assertNext(event -> {
                    assertEquals("text_delta", event.getType());
                    assertEquals("hello", event.getTextDelta());
                })
                .assertNext(event -> {
                    assertEquals("done", event.getType());
                    assertNotNull(event.getUsage());
                    assertEquals(23, event.getUsage().getInputTokens());
                    assertEquals(7, event.getUsage().getOutputTokens());
                    assertEquals(30, event.getUsage().getTotalTokens());
                })
                .verifyComplete();
    }

    @Test
    void streamChat_withCachedInputTokens_parsesCacheReadInputTokens() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.TEXT_EVENT_STREAM_VALUE, """
                    data: {"type":"message_start","message":{"id":"msg_cache","type":"message","role":"assistant","model":"claude-3-5-sonnet-20241022","usage":{"input_tokens":100,"cache_read_input_tokens":50}}}

                    data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"cached"}}

                    data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":10}}

                    data: {"type":"message_stop"}

                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.streamChat(buildBasicRequest(true)))
                .assertNext(event -> {
                    // message_start 携带 input_tokens 和 cache_read_input_tokens
                    assertEquals(UnifiedStreamEvent.TYPE_USAGE_ONLY, event.getType());
                    assertNotNull(event.getUsage());
                    // 归一化后 inputTokens 包含缓存部分（100 + 50）
                    assertEquals(150, event.getUsage().getInputTokens());
                    assertEquals(100, event.getUsage().getRawInputTokens());
                    assertEquals(50, event.getUsage().getCachedInputTokens());
                })
                .assertNext(event -> {
                    assertEquals("text_delta", event.getType());
                    assertEquals("cached", event.getTextDelta());
                })
                .assertNext(event -> {
                    assertEquals("done", event.getType());
                    assertNotNull(event.getUsage());
                    assertEquals(150, event.getUsage().getInputTokens());
                    assertEquals(100, event.getUsage().getRawInputTokens());
                    assertEquals(10, event.getUsage().getOutputTokens());
                    assertEquals(50, event.getUsage().getCachedInputTokens());
                })
                .verifyComplete();
    }

    @Test
    void streamChat_withCacheCreationInputTokens_parsesBothCacheFields() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.TEXT_EVENT_STREAM_VALUE, """
                    data: {"type":"message_start","message":{"id":"msg_cc","type":"message","role":"assistant","model":"claude-3-5-sonnet-20241022","usage":{"input_tokens":100,"cache_read_input_tokens":50,"cache_creation_input_tokens":30}}}

                    data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"hi"}}

                    data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":5}}

                    data: {"type":"message_stop"}

                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.streamChat(buildBasicRequest(true)))
                .assertNext(event -> {
                    // message_start 携带 input_tokens、cache_read_input_tokens 和 cache_creation_input_tokens
                    assertEquals(UnifiedStreamEvent.TYPE_USAGE_ONLY, event.getType());
                    assertNotNull(event.getUsage());
                    // 归一化后 inputTokens 包含缓存部分（100 + 50 + 30）
                    assertEquals(180, event.getUsage().getInputTokens());
                    assertEquals(100, event.getUsage().getRawInputTokens());
                    assertEquals(50, event.getUsage().getCachedInputTokens());
                    assertEquals(30, event.getUsage().getCacheCreationInputTokens());
                })
                .assertNext(event -> {
                    assertEquals("text_delta", event.getType());
                    assertEquals("hi", event.getTextDelta());
                })
                .assertNext(event -> {
                    assertEquals("done", event.getType());
                    assertNotNull(event.getUsage());
                    assertEquals(180, event.getUsage().getInputTokens());
                    assertEquals(100, event.getUsage().getRawInputTokens());
                    assertEquals(5, event.getUsage().getOutputTokens());
                    assertEquals(50, event.getUsage().getCachedInputTokens());
                    assertEquals(30, event.getUsage().getCacheCreationInputTokens());
                })
                .verifyComplete();
    }


    @Test
    void streamChat_toolUse_parsesToolCallLifecycle() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.TEXT_EVENT_STREAM_VALUE, """
                    data: {"type":"message_start","message":{"id":"msg_st","type":"message","role":"assistant","model":"claude-3-5-sonnet-20241022","usage":{"input_tokens":19}}}

                    data: {"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"toolu_s1","name":"get_weather","input":{}}}

                    data: {"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\\"city\\":\\"Shang"}}

                    data: {"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"hai\\"}"}}

                    data: {"type":"content_block_stop","index":1}

                    data: {"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":11}}

                    data: {"type":"message_stop"}

                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.streamChat(buildBasicRequest(true)))
                .assertNext(event -> {
                    // message_start 事件产生 usage_only 事件，携带初始 input_tokens
                    assertEquals(UnifiedStreamEvent.TYPE_USAGE_ONLY, event.getType());
                    assertEquals(19, event.getUsage().getInputTokens());
                })
                .assertNext(event -> {
                    assertEquals("tool_call", event.getType());
                    assertEquals("toolu_s1", event.getToolCallId());
                    assertEquals("get_weather", event.getToolName());
                })
                .assertNext(event -> {
                    assertEquals("tool_call_delta", event.getType());
                    assertEquals("toolu_s1", event.getToolCallId());
                    assertTrue(event.getArgumentsDelta().contains("Shang"));
                })
                .assertNext(event -> {
                    assertEquals("tool_call_delta", event.getType());
                    assertEquals("toolu_s1", event.getToolCallId());
                    assertTrue(event.getArgumentsDelta().contains("hai"));
                })
                .assertNext(event -> {
                    assertEquals("done", event.getType());
                    assertEquals("tool_calls", event.getFinishReason());
                })
                .verifyComplete();
    }

    // ==================== 5. 错误响应处理 ====================

    @Test
    void chat_rateLimited_throwsProviderRateLimit() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 429, MediaType.APPLICATION_JSON_VALUE, """
                    {"type":"error","error":{"type":"rate_limit_error","message":"too many requests"}}
                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildBasicRequest(false)))
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof GatewayException);
                    GatewayException ex = (GatewayException) error;
                    assertEquals(ErrorCode.PROVIDER_RATE_LIMIT, ex.getErrorCode());
                    assertTrue(ex.getMessage().contains("too many requests"));
                })
                .verify();
    }

    @Test
    void streamChat_errorEvent_throwsProviderError() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.TEXT_EVENT_STREAM_VALUE, """
                    data: {"type":"error","error":{"type":"invalid_request_error","message":"stream denied"}}

                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.streamChat(buildBasicRequest(true)))
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof GatewayException);
                    GatewayException ex = (GatewayException) error;
                    assertEquals(ErrorCode.PROVIDER_ERROR, ex.getErrorCode());
                })
                .verify();
    }

    // ==================== 6. stop_reason 映射 ====================

    @Test
    void chat_stopReasonMappings_mapsCorrectly() {
        verifyStopReason("end_turn", "stop");
        // 需要重启 server，重新创建
    }

    @Test
    void chat_stopReasonMaxTokens_mapsToLength() {
        verifyStopReason("max_tokens", "length");
    }

    @Test
    void chat_stopReasonToolUse_mapsToToolCalls() {
        verifyStopReason("tool_use", "tool_calls");
    }

    private void verifyStopReason(String anthropicReason, String expected) {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "id": "msg_stop",
                      "type": "message",
                      "role": "assistant",
                      "model": "claude-3-5-sonnet-20241022",
                      "content": [{"type":"text","text":"ok"}],
                      "stop_reason": "%s",
                      "usage": {"input_tokens": 5, "output_tokens": 2}
                    }
                    """.formatted(anthropicReason));
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildBasicRequest(false)))
                .assertNext(response -> assertEquals(expected, response.getFinishReason()))
                .verifyComplete();
    }

    // ==================== 7. 消息构建 ====================

    @Test
    void chat_requestBody_includesThinkingConfig() throws Exception {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "id": "msg_thinking",
                      "type": "message",
                      "role": "assistant",
                      "model": "claude-3-5-sonnet-20241022",
                      "content": [{"type":"text","text":"ok"}],
                      "stop_reason": "end_turn",
                      "usage": {"input_tokens": 9, "output_tokens": 1}
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        UnifiedRequest request = buildBasicRequest(false);
        request.getGenerationConfig().setThinkingEnabled(true);
        request.getGenerationConfig().setThinkingBudgetTokens(4096);

        StepVerifier.create(providerClient.chat(request))
                .assertNext(response -> assertEquals("stop", response.getFinishReason()))
                .verifyComplete();

        JsonNode body = objectMapper.readTree(requestBody.get());
        assertEquals("enabled", body.get("thinking").get("type").asText());
        assertEquals(4096, body.get("thinking").get("budget_tokens").asInt());
    }

    @Test
    void chat_requestBody_mapsRolesAndMergesConsecutiveToolResults() throws Exception {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "id": "msg_merged",
                      "type": "message",
                      "role": "assistant",
                      "model": "claude-3-5-sonnet-20241022",
                      "content": [{"type":"text","text":"ok"}],
                      "stop_reason": "end_turn",
                      "usage": {"input_tokens": 10, "output_tokens": 2}
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        UnifiedRequest request = buildRequestWithToolHistory(false);

        StepVerifier.create(providerClient.chat(request))
                .assertNext(response -> assertEquals("stop", response.getFinishReason()))
                .verifyComplete();

        JsonNode body = objectMapper.readTree(requestBody.get());
        assertEquals("你是一个助手", body.get("system").asText());

        JsonNode messages = body.get("messages");
        assertEquals(3, messages.size());
        assertEquals("user", messages.get(0).get("role").asText());
        assertEquals("请帮我查询天气", messages.get(0).get("content").asText());
        assertEquals("assistant", messages.get(1).get("role").asText());
        assertTrue(messages.get(1).get("content").isArray());
        // content[0]=text, content[1]=thinking(兼容注入), content[2]=tool_use
        assertEquals("text", messages.get(1).get("content").get(0).get("type").asText());
        assertEquals("thinking", messages.get(1).get("content").get(1).get("type").asText());
        assertEquals("tool_use", messages.get(1).get("content").get(2).get("type").asText());
        assertEquals("user", messages.get(2).get("role").asText());
        assertEquals(2, messages.get(2).get("content").size());
        assertEquals("tool_result", messages.get(2).get("content").get(0).get("type").asText());
        assertEquals("tool_result", messages.get(2).get("content").get(1).get("type").asText());
    }

    // ==================== 8. 跨协议兼容：thinking 块注入 ====================

    @Test
    void chat_requestBody_openAiFormatWithoutThinking_injectsThinkingBeforeToolUse() throws Exception {
        // 模拟 OpenAI 格式请求：assistant 消息只有 text + tool_calls，无 thinking 块
        // 路由到 Anthropic 兼容 Provider 时需注入 thinking 块
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "id": "msg_compat",
                      "type": "message",
                      "role": "assistant",
                      "model": "deepseek-chat",
                      "content": [{"type":"text","text":"ok"}],
                      "stop_reason": "end_turn",
                      "usage": {"input_tokens": 10, "output_tokens": 2}
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        // 构造 OpenAI 格式的 assistant 消息（无 thinking 部分）
        UnifiedPart textPart = new UnifiedPart();
        textPart.setType("text");
        textPart.setText("帮你查询");

        UnifiedToolCall toolCall = new UnifiedToolCall();
        toolCall.setId("call_openai_1");
        toolCall.setType("function");
        toolCall.setToolName("search");
        toolCall.setArgumentsJson("{\"query\":\"weather\"}");

        UnifiedMessage assistantMsg = new UnifiedMessage();
        assistantMsg.setRole("assistant");
        assistantMsg.setParts(List.of(textPart));
        assistantMsg.setToolCalls(List.of(toolCall));

        UnifiedRequest request = buildCompatTestRequest("deepseek-chat", assistantMsg, "查询天气");

        StepVerifier.create(providerClient.chat(request))
                .assertNext(response -> assertEquals("stop", response.getFinishReason()))
                .verifyComplete();

        JsonNode body = objectMapper.readTree(requestBody.get());
        JsonNode messages = body.get("messages");
        assertEquals(2, messages.size());
        // messages[0]=user, messages[1]=assistant
        JsonNode assistantContent = messages.get(1).get("content");
        assertTrue(assistantContent.isArray());
        // content[0]=text, content[1]=thinking(兼容注入), content[2]=tool_use
        assertEquals(3, assistantContent.size());
        assertEquals("text", assistantContent.get(0).get("type").asText());
        assertEquals("thinking", assistantContent.get(1).get("type").asText());
        assertEquals("", assistantContent.get(1).get("thinking").asText());
        assertEquals("tool_use", assistantContent.get(2).get("type").asText());
        assertEquals("call_openai_1", assistantContent.get(2).get("id").asText());
    }

    @Test
    void chat_requestBody_withExistingThinking_doesNotInjectDuplicate() throws Exception {
        // Anthropic 格式请求已包含 thinking 块时，不应重复注入
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "id": "msg_no_dup",
                      "type": "message",
                      "role": "assistant",
                      "model": "claude-3-5-sonnet-20241022",
                      "content": [{"type":"text","text":"ok"}],
                      "stop_reason": "end_turn",
                      "usage": {"input_tokens": 10, "output_tokens": 2}
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        UnifiedPart textPart = new UnifiedPart();
        textPart.setType("text");
        textPart.setText("思考中");

        UnifiedPart thinkingPart = new UnifiedPart();
        thinkingPart.setType("thinking");
        thinkingPart.setText("用户需要天气信息");

        UnifiedToolCall toolCall = new UnifiedToolCall();
        toolCall.setId("call_1");
        toolCall.setType("function");
        toolCall.setToolName("get_weather");
        toolCall.setArgumentsJson("{\"city\":\"Beijing\"}");

        UnifiedMessage assistantMsg = new UnifiedMessage();
        assistantMsg.setRole("assistant");
        assistantMsg.setParts(List.of(thinkingPart, textPart));
        assistantMsg.setToolCalls(List.of(toolCall));

        UnifiedRequest request = buildCompatTestRequest("claude-3-5-sonnet-20241022", assistantMsg, "查天气");

        StepVerifier.create(providerClient.chat(request))
                .assertNext(response -> assertEquals("stop", response.getFinishReason()))
                .verifyComplete();

        JsonNode body = objectMapper.readTree(requestBody.get());
        JsonNode messages = body.get("messages");
        assertEquals(2, messages.size());
        // messages[0]=user, messages[1]=assistant
        JsonNode assistantContent = messages.get(1).get("content");
        // content[0]=thinking(原始), content[1]=text, content[2]=tool_use — 无重复注入
        assertEquals(3, assistantContent.size());
        assertEquals("thinking", assistantContent.get(0).get("type").asText());
        assertEquals("用户需要天气信息", assistantContent.get(0).get("thinking").asText());
        assertEquals("text", assistantContent.get(1).get("type").asText());
        assertEquals("tool_use", assistantContent.get(2).get("type").asText());
    }

    @Test
    void chat_requestBody_noPartsWithToolCalls_injectsThinking() throws Exception {
        // OpenAI 格式：assistant 消息只有 tool_calls 无 parts
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "id": "msg_no_parts",
                      "type": "message",
                      "role": "assistant",
                      "model": "deepseek-chat",
                      "content": [{"type":"text","text":"ok"}],
                      "stop_reason": "end_turn",
                      "usage": {"input_tokens": 5, "output_tokens": 1}
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        UnifiedToolCall toolCall = new UnifiedToolCall();
        toolCall.setId("call_no_parts");
        toolCall.setType("function");
        toolCall.setToolName("search");
        toolCall.setArgumentsJson("{\"q\":\"test\"}");

        UnifiedMessage assistantMsg = new UnifiedMessage();
        assistantMsg.setRole("assistant");
        assistantMsg.setParts(null);
        assistantMsg.setToolCalls(List.of(toolCall));

        UnifiedRequest request = buildCompatTestRequest("deepseek-chat", assistantMsg, "查一下");

        StepVerifier.create(providerClient.chat(request))
                .assertNext(response -> assertEquals("stop", response.getFinishReason()))
                .verifyComplete();

        JsonNode body = objectMapper.readTree(requestBody.get());
        JsonNode assistantContent = body.get("messages").get(1).get("content");
        // content[0]=thinking(注入), content[1]=tool_use — 无 text 块
        assertEquals(2, assistantContent.size());
        assertEquals("thinking", assistantContent.get(0).get("type").asText());
        assertEquals("tool_use", assistantContent.get(1).get("type").asText());
        assertEquals("call_no_parts", assistantContent.get(1).get("id").asText());
    }

    // ==================== 9. 工具定义构建 ====================

    @Test
    void chat_requestBody_usesInputSchema() throws Exception {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "id": "msg_tools",
                      "type": "message",
                      "role": "assistant",
                      "model": "claude-3-5-sonnet-20241022",
                      "content": [{"type":"text","text":"ok"}],
                      "stop_reason": "end_turn",
                      "usage": {"input_tokens": 9, "output_tokens": 1}
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        UnifiedRequest request = buildRequestWithTools(false);

        StepVerifier.create(providerClient.chat(request))
                .assertNext(response -> assertEquals("stop", response.getFinishReason()))
                .verifyComplete();

        JsonNode body = objectMapper.readTree(requestBody.get());

        // 工具定义使用 input_schema 而非 parameters
        JsonNode tools = body.get("tools");
        assertNotNull(tools);
        assertEquals(1, tools.size());
        assertTrue(tools.get(0).has("input_schema"));
        assertFalse(tools.get(0).has("parameters"));

        // tool_choice 映射为 {"type":"tool","name":"get_weather"}
        JsonNode toolChoice = body.get("tool_choice");
        assertNotNull(toolChoice);
        assertEquals("tool", toolChoice.get("type").asText());
        assertEquals("get_weather", toolChoice.get("name").asText());
    }

    // ==================== 10. providerType ====================

    @Test
    void getProviderType_returnsAnthropic() {
        // 不需要启动 server，直接构造 client
        GatewayProperties props = new GatewayProperties();
        AnthropicProviderClient client = new AnthropicProviderClient(
                new ReactorClientHttpConnector(), objectMapper, props, ProviderTestUtil.noopCircuitBreakerManager(), new ReasoningSemanticMapper());
        assertEquals(ProviderType.ANTHROPIC, client.getProviderType());
    }

    // ==================== 辅助方法 ====================

    private AnthropicProviderClient newProviderClient(int timeoutSeconds) {
        GatewayProperties props = new GatewayProperties();
        GatewayProperties.ProviderProperties providerProps = new GatewayProperties.ProviderProperties();
        providerProps.setEnabled(true);
        providerProps.setBaseUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());
        providerProps.setApiKey("test-anthropic-key");
        providerProps.setTimeoutSeconds(timeoutSeconds);
        props.setProviders(Map.of("anthropic", providerProps));
        return new AnthropicProviderClient(
                new ReactorClientHttpConnector(), objectMapper, props, ProviderTestUtil.noopCircuitBreakerManager(), new ReasoningSemanticMapper());
    }

    private UnifiedRequest buildBasicRequest(boolean stream) {
        UnifiedPart userPart = new UnifiedPart();
        userPart.setType("text");
        userPart.setText("你好，帮我总结一下");

        UnifiedMessage userMessage = new UnifiedMessage();
        userMessage.setRole("user");
        userMessage.setParts(List.of(userPart));

        UnifiedGenerationConfig genConfig = new UnifiedGenerationConfig();
        genConfig.setTemperature(0.3);
        genConfig.setTopP(0.8);
        genConfig.setMaxOutputTokens(256);

        UnifiedRequest request = new UnifiedRequest();
        request.setProvider("anthropic");
        request.setModel("claude-3-5-sonnet-20241022");
        request.setSystemPrompt("你是一个严谨的助手");
        request.setMessages(List.of(userMessage));
        request.setGenerationConfig(genConfig);
        request.setStream(stream);

        UnifiedRequest.ProviderExecutionContext ctx = new UnifiedRequest.ProviderExecutionContext();
        ctx.setProviderName("anthropic");
        ctx.setProviderBaseUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());
        request.setExecutionContext(ctx);
        return request;
    }

    private UnifiedRequest buildRequestWithToolHistory(boolean stream) {
        UnifiedPart userPart = new UnifiedPart();
        userPart.setType("text");
        userPart.setText("请帮我查询天气");

        UnifiedMessage userMsg = new UnifiedMessage();
        userMsg.setRole("user");
        userMsg.setParts(List.of(userPart));

        UnifiedPart assistantPart = new UnifiedPart();
        assistantPart.setType("text");
        assistantPart.setText("先帮你查询");

        UnifiedToolCall toolCall = new UnifiedToolCall();
        toolCall.setId("call_1");
        toolCall.setType("function");
        toolCall.setToolName("get_weather");
        toolCall.setArgumentsJson("{\"city\":\"Shanghai\"}");

        UnifiedMessage assistantMsg = new UnifiedMessage();
        assistantMsg.setRole("assistant");
        assistantMsg.setParts(List.of(assistantPart));
        assistantMsg.setToolCalls(List.of(toolCall));

        UnifiedMessage toolResult1 = new UnifiedMessage();
        toolResult1.setRole("tool");
        toolResult1.setToolCallId("call_1");
        toolResult1.setParts(List.of(textPart("晴天")));

        UnifiedMessage toolResult2 = new UnifiedMessage();
        toolResult2.setRole("tool");
        toolResult2.setToolCallId("call_2");
        toolResult2.setParts(List.of(textPart("25°C")));

        UnifiedRequest request = new UnifiedRequest();
        request.setProvider("anthropic");
        request.setModel("claude-3-5-sonnet-20241022");
        request.setSystemPrompt("你是一个助手");
        request.setMessages(List.of(userMsg, assistantMsg, toolResult1, toolResult2));
        request.setStream(stream);

        UnifiedRequest.ProviderExecutionContext ctx = new UnifiedRequest.ProviderExecutionContext();
        ctx.setProviderName("anthropic");
        ctx.setProviderBaseUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());
        request.setExecutionContext(ctx);
        return request;
    }

    private UnifiedRequest buildRequestWithTools(boolean stream) {
        UnifiedTool tool = new UnifiedTool();
        tool.setName("get_weather");
        tool.setDescription("获取天气");
        tool.setInputSchema(Map.of(
                "type", "object",
                "properties", Map.of("city", Map.of("type", "string")),
                "required", List.of("city")
        ));

        UnifiedToolChoice choice = new UnifiedToolChoice();
        choice.setType("specific");
        choice.setToolName("get_weather");

        UnifiedRequest request = buildBasicRequest(stream);
        request.setTools(List.of(tool));
        request.setToolChoice(choice);
        return request;
    }

    /**
     * 构建跨协议兼容测试请求（公共辅助方法，消除重复代码）
     *
     * @param model        模型名称
     * @param assistantMsg assistant 消息（含/不含 thinking 块）
     * @param userText     user 消息文本
     */
    private UnifiedRequest buildCompatTestRequest(String model, UnifiedMessage assistantMsg, String userText) {
        UnifiedMessage userMsg = new UnifiedMessage();
        userMsg.setRole("user");
        userMsg.setParts(List.of(textPart(userText)));

        UnifiedRequest request = new UnifiedRequest();
        request.setProvider("anthropic");
        request.setModel(model);
        request.setMessages(List.of(userMsg, assistantMsg));
        request.setStream(false);

        UnifiedGenerationConfig genConfig = new UnifiedGenerationConfig();
        request.setGenerationConfig(genConfig);

        UnifiedRequest.ProviderExecutionContext ctx = new UnifiedRequest.ProviderExecutionContext();
        ctx.setProviderName("anthropic");
        ctx.setProviderBaseUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());
        request.setExecutionContext(ctx);
        return request;
    }

    private UnifiedPart textPart(String text) {
        UnifiedPart part = new UnifiedPart();
        part.setType("text");
        part.setText(text);
        return part;
    }

    private void startServer(ThrowingHandler handler) {
        try {
            httpServer = HttpServer.create(new InetSocketAddress(0), 0);
            httpServer.createContext("/v1/messages", exchange -> {
                try {
                    handler.handle(exchange);
                } finally {
                    exchange.close();
                }
            });
            httpServer.setExecutor(Executors.newCachedThreadPool());
            httpServer.start();
        } catch (IOException e) {
            throw new IllegalStateException("failed to start test server", e);
        }
    }

    private void captureRequest(HttpExchange exchange) throws IOException {
        requestPath.set(exchange.getRequestURI().getPath());
        apiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-api-key"));
        anthropicVersionHeader.set(exchange.getRequestHeaders().getFirst("anthropic-version"));
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    }

    private void writeResponse(HttpExchange exchange, int statusCode, String contentType, String body) {
        try {
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, contentType);
            exchange.sendResponseHeaders(statusCode, bodyBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bodyBytes);
                os.flush();
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to write response", e);
        }
    }

    @FunctionalInterface
    private interface ThrowingHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
