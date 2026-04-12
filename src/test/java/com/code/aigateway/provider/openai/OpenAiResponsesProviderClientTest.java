package com.code.aigateway.provider.openai;

import com.code.aigateway.config.GatewayProperties;
import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.model.UnifiedGenerationConfig;
import com.code.aigateway.core.model.UnifiedMessage;
import com.code.aigateway.core.model.UnifiedPart;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedStreamEvent;
import com.code.aigateway.core.model.UnifiedTool;
import com.code.aigateway.core.model.UnifiedToolCall;
import com.code.aigateway.core.model.UnifiedToolChoice;
import com.code.aigateway.core.capability.ReasoningSemanticMapper;

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAI Responses API 提供商客户端单元测试
 * <p>
 * 使用 JDK HttpServer 模拟上游 /v1/responses 端点。
 * </p>
 */
class OpenAiResponsesProviderClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer httpServer;
    private OpenAiResponsesProviderClient providerClient;
    private AtomicReference<String> requestPath;
    private AtomicReference<String> authorizationHeader;
    private AtomicReference<String> requestBody;

    @BeforeEach
    void setUp() {
        requestPath = new AtomicReference<>();
        authorizationHeader = new AtomicReference<>();
        requestBody = new AtomicReference<>();
    }

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    // ==================== 1. 非流式文本响应 ====================

    @Test
    void chat_textResponse_returnsUnifiedResponse() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "id": "resp_001",
                      "object": "response",
                      "model": "gpt-4o",
                      "status": "completed",
                      "output": [
                        {
                          "type": "message",
                          "role": "assistant",
                          "content": [
                            {"type": "output_text", "text": "这是 Responses API 的回复"}
                          ]
                        }
                      ],
                      "usage": {
                        "input_tokens": 15,
                        "output_tokens": 8,
                        "total_tokens": 23
                      }
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildBasicRequest(false)))
                .assertNext(response -> {
                    assertEquals("resp_001", response.getId());
                    assertEquals("gpt-4o", response.getModel());
                    assertEquals("openai-responses", response.getProvider());
                    assertEquals("stop", response.getFinishReason());
                    assertNotNull(response.getUsage());
                    assertEquals(15, response.getUsage().getInputTokens());
                    assertEquals(8, response.getUsage().getOutputTokens());
                    assertEquals(23, response.getUsage().getTotalTokens());
                    assertEquals("这是 Responses API 的回复",
                            response.getOutputs().getFirst().getParts().getFirst().getText());
                })
                .verifyComplete();

        assertEquals("/v1/responses", requestPath.get());
        assertEquals("Bearer test-responses-key", authorizationHeader.get());
        assertTrue(requestBody.get().contains("\"instructions\""));
        assertTrue(requestBody.get().contains("\"input\""));
        assertTrue(requestBody.get().contains("\"type\":\"message\""));
        assertTrue(requestBody.get().contains("\"role\":\"user\""));
        assertTrue(requestBody.get().contains("\"type\":\"input_text\""));
        assertTrue(requestBody.get().contains("\"text\":\"你好\""));
    }

    @Test
    void chat_usageWithCachedTokens_parsesCachedTokens() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "id": "resp_cached_001",
                      "object": "response",
                      "model": "gpt-4o",
                      "status": "completed",
                      "output": [
                        {
                          "type": "message",
                          "role": "assistant",
                          "content": [
                            {"type": "output_text", "text": "命中缓存"}
                          ]
                        }
                      ],
                      "usage": {
                        "input_tokens": 15,
                        "output_tokens": 8,
                        "total_tokens": 23,
                        "input_tokens_details": {
                          "cached_tokens": 6
                        }
                      }
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildBasicRequest(false)))
                .assertNext(response -> {
                    assertNotNull(response.getUsage());
                    assertEquals(6, response.getUsage().getCachedInputTokens());
                })
                .verifyComplete();
    }

    @Test
    void chat_functionCall_parsesToolCalls() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "id": "resp_fc_001",
                      "object": "response",
                      "model": "gpt-4o",
                      "status": "completed",
                      "output": [
                        {
                          "type": "function_call",
                          "id": "fc_123",
                          "call_id": "fc_123",
                          "name": "get_weather",
                          "arguments": "{\\"city\\":\\"Shanghai\\"}"
                        }
                      ],
                      "usage": {"input_tokens": 20, "output_tokens": 10, "total_tokens": 30}
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildBasicRequest(false)))
                .assertNext(response -> {
                    assertEquals("stop", response.getFinishReason());
                    assertNotNull(response.getOutputs().getFirst().getToolCalls());
                    assertEquals(1, response.getOutputs().getFirst().getToolCalls().size());

                    UnifiedToolCall tc = response.getOutputs().getFirst().getToolCalls().getFirst();
                    assertEquals("fc_123", tc.getId());
                    assertEquals("function", tc.getType());
                    assertEquals("get_weather", tc.getToolName());
                    assertTrue(tc.getArgumentsJson().contains("Shanghai"));
                })
                .verifyComplete();
    }

    @Test
    void chat_functionCall_prefersCallIdOverId() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "id": "resp_fc_prefer_call_id",
                      "object": "response",
                      "model": "gpt-4o",
                      "status": "completed",
                      "output": [
                        {
                          "type": "function_call",
                          "id": "legacy_id",
                          "call_id": "fc_preferred",
                          "name": "get_weather",
                          "arguments": "{\\\"city\\\":\\\"Shanghai\\\"}"
                        }
                      ],
                      "usage": {"input_tokens": 20, "output_tokens": 10, "total_tokens": 30}
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildBasicRequest(false)))
                .assertNext(response -> {
                    UnifiedToolCall tc = response.getOutputs().getFirst().getToolCalls().getFirst();
                    // 应优先使用 call_id，确保与流式链路一致
                    assertEquals("fc_preferred", tc.getId());
                })
                .verifyComplete();
    }

    @Test
    void chat_withToolHistory_reusesSameResponsesCallIdAcrossAssistantAndTool() throws Exception {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "id": "resp_tool_history_001",
                      "object": "response",
                      "model": "gpt-4o",
                      "status": "completed",
                      "output": [
                        {
                          "type": "message",
                          "role": "assistant",
                          "content": [
                            {"type": "output_text", "text": "处理完成"}
                          ]
                        }
                      ],
                      "usage": {"input_tokens": 12, "output_tokens": 6, "total_tokens": 18}
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildRequestWithToolHistory(false)))
                .assertNext(response -> assertEquals("resp_tool_history_001", response.getId()))
                .verifyComplete();

        JsonNode root = objectMapper.readTree(requestBody.get());
        JsonNode input = root.get("input");
        assertNotNull(input);
        assertEquals(3, input.size());
        assertEquals("message", input.get(0).get("type").asText());
        assertEquals("user", input.get(0).get("role").asText());
        assertEquals("input_text", input.get(0).get("content").get(0).get("type").asText());
        assertEquals("查天气", input.get(0).get("content").get(0).get("text").asText());
        assertEquals("function_call", input.get(1).get("type").asText());
        String responsesCallId = input.get(1).get("call_id").asText();
        assertEquals(responsesCallId, input.get(1).get("id").asText());
        assertEquals("function_call_output", input.get(2).get("type").asText());
        assertEquals(responsesCallId, input.get(2).get("call_id").asText());
    }

    @Test
    void chat_withOpaqueToolHistory_reusesMappedToolCallId() throws Exception {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "id": "resp_opaque_tool_history_001",
                      "object": "response",
                      "model": "gpt-4o",
                      "status": "completed",
                      "output": [
                        {
                          "type": "message",
                          "role": "assistant",
                          "content": [
                            {"type": "output_text", "text": "处理完成"}
                          ]
                        }
                      ],
                      "usage": {"input_tokens": 12, "output_tokens": 6, "total_tokens": 18}
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildRequestWithOpaqueToolHistory(false)))
                .assertNext(response -> assertEquals("resp_opaque_tool_history_001", response.getId()))
                .verifyComplete();

        JsonNode root = objectMapper.readTree(requestBody.get());
        JsonNode input = root.get("input");
        assertNotNull(input);
        assertEquals(3, input.size());
        String mappedCallId = input.get(1).get("call_id").asText();
        assertEquals(mappedCallId, input.get(1).get("id").asText());
        assertEquals("function_call_output", input.get(2).get("type").asText());
        assertEquals(mappedCallId, input.get(2).get("call_id").asText());
        assertTrue(mappedCallId.startsWith("fc_"));
        assertTrue(!"opaque-tool-id-42".equals(mappedCallId));
    }

    // ==================== 3. 流式文本响应 ====================

    @Test
    void streamChat_textDelta_returnsEvents() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.TEXT_EVENT_STREAM_VALUE, """
                    event: response.output_text.delta
                    data: {"type":"response.output_text.delta","delta":"你好"}

                    event: response.output_text.delta
                    data: {"type":"response.output_text.delta","delta":"，世界"}

                    event: response.completed
                    data: {"type":"response.completed","response":{"status":"completed","usage":{"input_tokens":10,"output_tokens":5,"total_tokens":15}}}

                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.streamChat(buildBasicRequest(true)))
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
                    assertEquals("stop", event.getFinishReason());
                    assertEquals(10, event.getUsage().getInputTokens());
                })
                .verifyComplete();
    }

    // ==================== 4. 流式工具调用 ====================

    @Test
    void streamChat_completedUsageWithCachedTokens_parsesCachedTokens() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.TEXT_EVENT_STREAM_VALUE, """
                    event: response.completed
                    data: {"type":"response.completed","response":{"status":"completed","usage":{"input_tokens":10,"output_tokens":5,"total_tokens":15,"input_tokens_details":{"cached_tokens":4}}}}

                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.streamChat(buildBasicRequest(true)))
                .assertNext(event -> {
                    assertEquals("done", event.getType());
                    assertNotNull(event.getUsage());
                    assertEquals(4, event.getUsage().getCachedInputTokens());
                })
                .verifyComplete();
    }

    @Test
    void streamChat_functionCall_parsesToolCallEvents() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.TEXT_EVENT_STREAM_VALUE, """
                    event: response.output_item.added
                    data: {"type":"response.output_item.added","item":{"type":"function_call","id":"fc_s1","call_id":"fc_s1","name":"search"}}

                    event: response.function_call_arguments.delta
                    data: {"type":"response.function_call_arguments.delta","delta":"{\\"query\\":\\""}

                    event: response.function_call_arguments.delta
                    data: {"type":"response.function_call_arguments.delta","delta":"test\\"}"}

                    event: response.output_item.done
                    data: {"type":"response.output_item.done"}

                    event: response.completed
                    data: {"type":"response.completed","response":{"status":"completed","usage":{"input_tokens":12,"output_tokens":8,"total_tokens":20}}}

                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.streamChat(buildBasicRequest(true)))
                .assertNext(event -> {
                    assertEquals("tool_call", event.getType());
                    assertEquals("fc_s1", event.getToolCallId());
                    assertEquals("search", event.getToolName());
                })
                .assertNext(event -> {
                    assertEquals("tool_call_delta", event.getType());
                    assertEquals("fc_s1", event.getToolCallId());
                    assertTrue(event.getArgumentsDelta().contains("query"));
                })
                .assertNext(event -> {
                    assertEquals("tool_call_delta", event.getType());
                    assertEquals("fc_s1", event.getToolCallId());
                    assertTrue(event.getArgumentsDelta().contains("test"));
                })
                .assertNext(event -> {
                    assertEquals("done", event.getType());
                    assertEquals("stop", event.getFinishReason());
                })
                .verifyComplete();
    }

    @Test
    void streamChat_functionCall_preservesOutputIndexAndItemId() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.TEXT_EVENT_STREAM_VALUE, """
                    event: response.output_item.added
                    data: {"type":"response.output_item.added","output_index":3,"item":{"type":"function_call","id":"item_fc_3","call_id":"fc_s3","name":"search"}}

                    event: response.function_call_arguments.delta
                    data: {"type":"response.function_call_arguments.delta","delta":"{\\\"query\\\":\\\"test\\\"}"}

                    event: response.completed
                    data: {"type":"response.completed","response":{"status":"completed","usage":{"input_tokens":12,"output_tokens":8,"total_tokens":20}}}

                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.streamChat(buildBasicRequest(true)))
                .assertNext(event -> {
                    assertEquals("tool_call", event.getType());
                    assertEquals(Integer.valueOf(3), event.getOutputIndex());
                    assertEquals("item_fc_3", event.getItemId());
                    assertEquals("fc_s3", event.getToolCallId());
                })
                .assertNext(event -> {
                    assertEquals("tool_call_delta", event.getType());
                    assertEquals(Integer.valueOf(3), event.getOutputIndex());
                    assertEquals("item_fc_3", event.getItemId());
                    assertEquals("fc_s3", event.getToolCallId());
                    assertTrue(event.getArgumentsDelta().contains("test"));
                })
                .assertNext(event -> assertEquals("done", event.getType()))
                .verifyComplete();
    }

    @Test
    void streamChat_interleavedFunctionCalls_resolvesByItemIdAndCallId() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.TEXT_EVENT_STREAM_VALUE, """
                    event: response.output_item.added
                    data: {"type":"response.output_item.added","output_index":1,"item":{"type":"function_call","id":"item_fc_1","call_id":"fc_1","name":"search"}}

                    event: response.output_item.added
                    data: {"type":"response.output_item.added","output_index":2,"item":{"type":"function_call","id":"item_fc_2","call_id":"fc_2","name":"lookup"}}

                    event: response.function_call_arguments.delta
                    data: {"type":"response.function_call_arguments.delta","item_id":"item_fc_2","delta":"{\\\"id\\\":1}"}

                    event: response.function_call_arguments.delta
                    data: {"type":"response.function_call_arguments.delta","call_id":"fc_1","delta":"{\\\"query\\\":\\\"abc\\\"}"}

                    event: response.completed
                    data: {"type":"response.completed","response":{"status":"completed","usage":{"input_tokens":12,"output_tokens":8,"total_tokens":20}}}

                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.streamChat(buildBasicRequest(true)))
                .assertNext(event -> {
                    assertEquals("tool_call", event.getType());
                    assertEquals(Integer.valueOf(1), event.getOutputIndex());
                    assertEquals("item_fc_1", event.getItemId());
                    assertEquals("fc_1", event.getToolCallId());
                })
                .assertNext(event -> {
                    assertEquals("tool_call", event.getType());
                    assertEquals(Integer.valueOf(2), event.getOutputIndex());
                    assertEquals("item_fc_2", event.getItemId());
                    assertEquals("fc_2", event.getToolCallId());
                })
                .assertNext(event -> {
                    assertEquals("tool_call_delta", event.getType());
                    assertEquals(Integer.valueOf(2), event.getOutputIndex());
                    assertEquals("item_fc_2", event.getItemId());
                    assertEquals("fc_2", event.getToolCallId());
                })
                .assertNext(event -> {
                    assertEquals("tool_call_delta", event.getType());
                    assertEquals(Integer.valueOf(1), event.getOutputIndex());
                    assertEquals("item_fc_1", event.getItemId());
                    assertEquals("fc_1", event.getToolCallId());
                })
                .assertNext(event -> assertEquals("done", event.getType()))
                .verifyComplete();
    }


    @Test
    void streamChat_functionCall_acceptsOutputFieldAlias() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.TEXT_EVENT_STREAM_VALUE, """
                    event: response.output_item.added
                    data: {"type":"response.output_item.added","output_index":4,"output":{"type":"function_call","id":"item_fc_4","call_id":"fc_s4","name":"search"}}

                    event: response.function_call_arguments.delta
                    data: {"type":"response.function_call_arguments.delta","delta":"{\\"query\\":\\"alias\\"}"}

                    event: response.completed
                    data: {"type":"response.completed","response":{"status":"completed","usage":{"input_tokens":12,"output_tokens":8,"total_tokens":20}}}

                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.streamChat(buildBasicRequest(true)))
                .assertNext(event -> {
                    assertEquals("tool_call", event.getType());
                    assertEquals(Integer.valueOf(4), event.getOutputIndex());
                    assertEquals("item_fc_4", event.getItemId());
                    assertEquals("fc_s4", event.getToolCallId());
                    assertEquals("search", event.getToolName());
                })
                .assertNext(event -> {
                    assertEquals("tool_call_delta", event.getType());
                    assertEquals(Integer.valueOf(4), event.getOutputIndex());
                    assertEquals("item_fc_4", event.getItemId());
                    assertEquals("fc_s4", event.getToolCallId());
                    assertTrue(event.getArgumentsDelta().contains("alias"));
                })
                .assertNext(event -> assertEquals("done", event.getType()))
                .verifyComplete();
    }

    @Test
    void chat_rateLimited_throwsProviderRateLimit() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 429, MediaType.APPLICATION_JSON_VALUE, """
                    {"error":{"message":"rate limit exceeded","type":"rate_limit_error"}}
                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildBasicRequest(false)))
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof GatewayException);
                    GatewayException ex = (GatewayException) error;
                    assertEquals(ErrorCode.PROVIDER_RATE_LIMIT, ex.getErrorCode());
                })
                .verify();
    }

    @Test
    void chat_500_throwsServerError() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 500, MediaType.APPLICATION_JSON_VALUE, """
                    {"error":{"message":"internal error"}}
                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildBasicRequest(false)))
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof GatewayException);
                    GatewayException ex = (GatewayException) error;
                    assertEquals(ErrorCode.PROVIDER_SERVER_ERROR, ex.getErrorCode());
                })
                .verify();
    }

    // ==================== 6. status 映射 ====================

    @Test
    void chat_statusCompleted_mapsToStop() {
        verifyStatusMapping("completed", "stop");
    }

    @Test
    void chat_statusIncomplete_mapsToLength() {
        verifyStatusMapping("incomplete", "length");
    }

    private void verifyStatusMapping(String apiStatus, String expected) {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "id": "resp_status",
                      "model": "gpt-4o",
                      "status": "%s",
                      "output": [
                        {"type": "message", "role": "assistant", "content": [{"type": "output_text", "text": "ok"}]}
                      ],
                      "usage": {"input_tokens": 5, "output_tokens": 2, "total_tokens": 7}
                    }
                    """.formatted(apiStatus));
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildBasicRequest(false)))
                .assertNext(response -> assertEquals(expected, response.getFinishReason()))
                .verifyComplete();
    }

    // ==================== 7. 请求体验证 ====================

    @Test
    void chat_requestBody_includesReasoningEffort() throws Exception {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "id": "resp_reasoning",
                      "model": "gpt-4o",
                      "status": "completed",
                      "output": [{"type": "message", "role": "assistant", "content": [{"type": "output_text", "text": "ok"}]}],
                      "usage": {"input_tokens": 5, "output_tokens": 2, "total_tokens": 7}
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        UnifiedRequest request = buildBasicRequest(false);
        request.getGenerationConfig().setReasoningEffort("high");

        StepVerifier.create(providerClient.chat(request))
                .assertNext(response -> assertEquals("stop", response.getFinishReason()))
                .verifyComplete();

        JsonNode body = objectMapper.readTree(requestBody.get());
        assertEquals("high", body.get("reasoning").get("effort").asText());
    }

    @Test
    void chat_requestBody_usesStopSequences() throws Exception {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "id": "resp_stop",
                      "model": "gpt-4o",
                      "status": "completed",
                      "output": [{"type": "message", "role": "assistant", "content": [{"type": "output_text", "text": "ok"}]}],
                      "usage": {"input_tokens": 5, "output_tokens": 2, "total_tokens": 7}
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        UnifiedRequest request = buildBasicRequest(false);
        request.getGenerationConfig().setStopSequences(List.of("DONE"));

        StepVerifier.create(providerClient.chat(request))
                .assertNext(response -> assertEquals("stop", response.getFinishReason()))
                .verifyComplete();

        JsonNode body = objectMapper.readTree(requestBody.get());
        assertTrue(body.has("stop"));
        assertEquals("DONE", body.get("stop").get(0).asText());
    }

    @Test
    void chat_requestBody_usesInstructionsAndInput() throws Exception {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "id": "resp_body",
                      "model": "gpt-4o",
                      "status": "completed",
                      "output": [{"type": "message", "role": "assistant", "content": [{"type": "output_text", "text": "ok"}]}],
                      "usage": {"input_tokens": 5, "output_tokens": 2, "total_tokens": 7}
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        UnifiedRequest request = buildRequestWithToolHistory(false);

        StepVerifier.create(providerClient.chat(request))
                .assertNext(response -> assertEquals("stop", response.getFinishReason()))
                .verifyComplete();

        JsonNode body = objectMapper.readTree(requestBody.get());
        assertEquals("你是一个助手", body.get("instructions").asText());
        assertTrue(body.has("input"));
        JsonNode input = body.get("input");
        assertEquals(3, input.size());
        assertEquals("user", input.get(0).get("role").asText());
        assertEquals("function_call", input.get(1).get("type").asText());
        assertEquals("get_weather", input.get(1).get("name").asText());
        String responsesCallId = input.get(1).get("call_id").asText();
        assertEquals(input.get(1).get("id").asText(), responsesCallId);
        assertEquals("fc_0", responsesCallId);
        assertEquals("function_call_output", input.get(2).get("type").asText());
        assertEquals(responsesCallId, input.get(2).get("call_id").asText());
    }

    @Test
    void chat_requestBody_usesMaxOutputTokens() throws Exception {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "id": "resp_gen",
                      "model": "gpt-4o",
                      "status": "completed",
                      "output": [{"type": "message", "role": "assistant", "content": [{"type": "output_text", "text": "ok"}]}],
                      "usage": {"input_tokens": 5, "output_tokens": 2, "total_tokens": 7}
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildBasicRequest(false)))
                .assertNext(response -> assertNotNull(response))
                .verifyComplete();

        JsonNode body = objectMapper.readTree(requestBody.get());
        // Responses API 用 max_output_tokens 而非 max_tokens
        assertTrue(body.has("max_output_tokens"));
        assertEquals(256, body.get("max_output_tokens").asInt());
    }

    // ==================== 8. 重试 ====================

    @Test
    void chat_5xxRetry_thenSuccess_returnsResponse() {
        AtomicInteger count = new AtomicInteger(0);
        startServer(exchange -> {
            captureRequest(exchange);
            if (count.incrementAndGet() <= 2) {
                writeResponse(exchange, 500, MediaType.APPLICATION_JSON_VALUE,
                        "{\"error\":{\"message\":\"server error\"}}");
            } else {
                writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                        {
                          "id": "resp_retry",
                          "model": "gpt-4o",
                          "status": "completed",
                          "output": [{"type": "message", "role": "assistant", "content": [{"type": "output_text", "text": "重试成功"}]}],
                          "usage": {"input_tokens": 5, "output_tokens": 2, "total_tokens": 7}
                        }
                        """);
            }
        });
        providerClient = newProviderClientWithRetry(5, 3, 100, 1000);

        StepVerifier.create(providerClient.chat(buildBasicRequest(false)))
                .assertNext(response ->
                        assertEquals("重试成功", response.getOutputs().getFirst().getParts().getFirst().getText()))
                .verifyComplete();

        assertEquals(3, count.get());
    }

    // ==================== 9. providerType ====================

    @Test
    void getProviderType_returnsOpenAiResponses() {
        OpenAiResponsesProviderClient client = new OpenAiResponsesProviderClient(
                new ReactorClientHttpConnector(), objectMapper, new GatewayProperties(), ProviderTestUtil.noopCircuitBreakerManager(), new ReasoningSemanticMapper());
        assertEquals(ProviderType.OPENAI_RESPONSES, client.getProviderType());
    }

    // ==================== 辅助方法 ====================

    private OpenAiResponsesProviderClient newProviderClient(int timeoutSeconds) {
        return newProviderClientWithRetry(timeoutSeconds, 0, 1000, 30000);
    }

    private OpenAiResponsesProviderClient newProviderClientWithRetry(
            int timeoutSeconds, int maxRetries, long initialIntervalMs, long maxIntervalMs) {
        GatewayProperties props = new GatewayProperties();
        if (maxRetries > 0) {
            GatewayProperties.RetryProperties retry = new GatewayProperties.RetryProperties();
            retry.setMaxRetries(maxRetries);
            retry.setInitialIntervalMs(initialIntervalMs);
            retry.setMaxIntervalMs(maxIntervalMs);
            props.setRetry(retry);
        }
        GatewayProperties.ProviderProperties providerProps = new GatewayProperties.ProviderProperties();
        providerProps.setEnabled(true);
        providerProps.setBaseUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());
        providerProps.setApiKey("test-responses-key");
        providerProps.setTimeoutSeconds(timeoutSeconds);
        props.setProviders(Map.of("openai-responses", providerProps));
        return new OpenAiResponsesProviderClient(
                new ReactorClientHttpConnector(), objectMapper, props, ProviderTestUtil.noopCircuitBreakerManager(), new ReasoningSemanticMapper());
    }

    private UnifiedRequest buildBasicRequest(boolean stream) {
        UnifiedPart userPart = new UnifiedPart();
        userPart.setType("text");
        userPart.setText("你好");

        UnifiedMessage userMsg = new UnifiedMessage();
        userMsg.setRole("user");
        userMsg.setParts(List.of(userPart));

        UnifiedGenerationConfig genConfig = new UnifiedGenerationConfig();
        genConfig.setTemperature(0.5);
        genConfig.setMaxOutputTokens(256);

        UnifiedRequest request = new UnifiedRequest();
        request.setProvider("openai-responses");
        request.setModel("gpt-4o");
        request.setSystemPrompt("你是一个助手");
        request.setMessages(List.of(userMsg));
        request.setGenerationConfig(genConfig);
        request.setStream(stream);

        UnifiedRequest.ProviderExecutionContext ctx = new UnifiedRequest.ProviderExecutionContext();
        ctx.setProviderName("openai-responses");
        ctx.setProviderBaseUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());
        request.setExecutionContext(ctx);
        return request;
    }

    /**
     * 构建带工具定义的请求，验证发送到上游的工具定义格式为扁平结构
     */
    private UnifiedRequest buildRequestWithTools(boolean stream) {
        UnifiedRequest request = buildBasicRequest(stream);

        UnifiedTool tool = new UnifiedTool();
        tool.setName("get_weather");
        tool.setDescription("获取天气信息");
        tool.setType("function");
        tool.setInputSchema(Map.of("type", "object", "properties", Map.of("city", Map.of("type", "string"))));

        request.setTools(List.of(tool));
        return request;
    }

    @Test
    void chat_tools_sendsFlatToolFormat() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "id": "resp_tools_001",
                      "object": "response",
                      "model": "gpt-4o",
                      "status": "completed",
                      "output": [
                        {
                          "type": "message",
                          "role": "assistant",
                          "content": [{"type": "output_text", "text": "好的"}]
                        }
                      ],
                      "usage": {"input_tokens": 10, "output_tokens": 5, "total_tokens": 15}
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildRequestWithTools(false)))
                .assertNext(response -> {
                    assertEquals("stop", response.getFinishReason());
                })
                .verifyComplete();

        String body = requestBody.get();
        // 验证扁平格式：顶层有 name、description、parameters，没有嵌套的 function 对象
        assertTrue(body.contains("\"name\":\"get_weather\""), "tools 应包含顶层 name");
        assertTrue(body.contains("\"description\":\"获取天气信息\""), "tools 应包含顶层 description");
        assertTrue(body.contains("\"parameters\""), "tools 应包含顶层 parameters");
        assertFalse(body.contains("\"function\":"), "tools 不应包含嵌套的 function 对象");
    }

    @Test
    void chat_withMultipleToolHistory_keepsStableDistinctResponsesIds() throws Exception {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "id": "resp_multi_tool_history_001",
                      "object": "response",
                      "model": "gpt-4o",
                      "status": "completed",
                      "output": [
                        {
                          "type": "message",
                          "role": "assistant",
                          "content": [
                            {"type": "output_text", "text": "处理完成"}
                          ]
                        }
                      ],
                      "usage": {"input_tokens": 18, "output_tokens": 7, "total_tokens": 25}
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildRequestWithMultipleToolHistory(false)))
                .assertNext(response -> assertEquals("resp_multi_tool_history_001", response.getId()))
                .verifyComplete();

        JsonNode root = objectMapper.readTree(requestBody.get());
        JsonNode input = root.get("input");
        assertNotNull(input);
        assertEquals(5, input.size());
        String firstResponsesCallId = input.get(1).get("call_id").asText();
        String secondResponsesCallId = input.get(2).get("call_id").asText();
        assertEquals("fc_0", firstResponsesCallId);
        assertEquals("fc_1", secondResponsesCallId);
        assertEquals(firstResponsesCallId, input.get(1).get("id").asText());
        assertEquals(secondResponsesCallId, input.get(2).get("id").asText());
        assertTrue(!firstResponsesCallId.isBlank());
        assertTrue(!secondResponsesCallId.isBlank());
        assertTrue(!firstResponsesCallId.equals(secondResponsesCallId));
        assertEquals(firstResponsesCallId, input.get(3).get("call_id").asText());
        assertEquals(secondResponsesCallId, input.get(4).get("call_id").asText());
    }

    private UnifiedRequest buildRequestWithToolHistory(boolean stream) {
        UnifiedPart userPart = new UnifiedPart();
        userPart.setType("text");
        userPart.setText("查天气");

        UnifiedMessage userMsg = new UnifiedMessage();
        userMsg.setRole("user");
        userMsg.setParts(List.of(userPart));

        UnifiedToolCall toolCall = new UnifiedToolCall();
        toolCall.setId("call_1");
        toolCall.setType("function");
        toolCall.setToolName("get_weather");
        toolCall.setArgumentsJson("{\"city\":\"Shanghai\"}");

        UnifiedMessage assistantMsg = new UnifiedMessage();
        assistantMsg.setRole("assistant");
        assistantMsg.setParts(List.of());
        assistantMsg.setToolCalls(List.of(toolCall));

        UnifiedMessage toolResult = new UnifiedMessage();
        toolResult.setRole("tool");
        toolResult.setToolCallId("call_1");
        toolResult.setParts(List.of(textPart("晴天")));

        return buildToolHistoryRequest(stream, List.of(userMsg, assistantMsg, toolResult));
    }

    private UnifiedRequest buildRequestWithMultipleToolHistory(boolean stream) {
        UnifiedMessage userMsg = new UnifiedMessage();
        userMsg.setRole("user");
        userMsg.setParts(List.of(textPart("查天气和时间")));

        UnifiedToolCall weatherToolCall = new UnifiedToolCall();
        weatherToolCall.setId("call_1");
        weatherToolCall.setType("function");
        weatherToolCall.setToolName("get_weather");
        weatherToolCall.setArgumentsJson("{\"city\":\"Shanghai\"}");

        UnifiedToolCall timeToolCall = new UnifiedToolCall();
        timeToolCall.setId("call_2");
        timeToolCall.setType("function");
        timeToolCall.setToolName("get_time");
        timeToolCall.setArgumentsJson("{\"timezone\":\"Asia/Shanghai\"}");

        UnifiedMessage assistantMsg = new UnifiedMessage();
        assistantMsg.setRole("assistant");
        assistantMsg.setParts(List.of());
        assistantMsg.setToolCalls(List.of(weatherToolCall, timeToolCall));

        UnifiedMessage weatherToolResult = new UnifiedMessage();
        weatherToolResult.setRole("tool");
        weatherToolResult.setToolCallId("call_1");
        weatherToolResult.setParts(List.of(textPart("晴天")));

        UnifiedMessage timeToolResult = new UnifiedMessage();
        timeToolResult.setRole("tool");
        timeToolResult.setToolCallId("call_2");
        timeToolResult.setParts(List.of(textPart("10:00")));

        return buildToolHistoryRequest(stream, List.of(userMsg, assistantMsg, weatherToolResult, timeToolResult));
    }

    private UnifiedRequest buildRequestWithOpaqueToolHistory(boolean stream) {
        UnifiedMessage userMsg = new UnifiedMessage();
        userMsg.setRole("user");
        userMsg.setParts(List.of(textPart("查天气")));

        UnifiedToolCall toolCall = new UnifiedToolCall();
        toolCall.setId("opaque-tool-id-42");
        toolCall.setType("function");
        toolCall.setToolName("get_weather");
        toolCall.setArgumentsJson("{\"city\":\"Shanghai\"}");

        UnifiedMessage assistantMsg = new UnifiedMessage();
        assistantMsg.setRole("assistant");
        assistantMsg.setParts(List.of());
        assistantMsg.setToolCalls(List.of(toolCall));

        UnifiedMessage toolResult = new UnifiedMessage();
        toolResult.setRole("tool");
        toolResult.setToolCallId("opaque-tool-id-42");
        toolResult.setParts(List.of(textPart("晴天")));

        return buildToolHistoryRequest(stream, List.of(userMsg, assistantMsg, toolResult));
    }

    private UnifiedRequest buildToolHistoryRequest(boolean stream, List<UnifiedMessage> messages) {
        UnifiedRequest request = new UnifiedRequest();
        request.setProvider("openai-responses");
        request.setModel("gpt-4o");
        request.setSystemPrompt("你是一个助手");
        request.setMessages(messages);
        request.setStream(stream);

        UnifiedRequest.ProviderExecutionContext ctx = new UnifiedRequest.ProviderExecutionContext();
        ctx.setProviderName("openai-responses");
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
            httpServer.createContext("/v1/responses", exchange -> {
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
        authorizationHeader.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
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
