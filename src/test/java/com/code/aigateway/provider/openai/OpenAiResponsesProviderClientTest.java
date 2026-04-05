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
import com.code.aigateway.core.resilience.CircuitBreakerManager;
import com.code.aigateway.provider.ProviderType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
    }

    // ==================== 2. 非流式工具调用 ====================

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

    // ==================== 5. 错误处理 ====================

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
        assertEquals("call_1", input.get(1).get("call_id").asText());
        assertEquals("function_call_output", input.get(2).get("type").asText());
        assertEquals("call_1", input.get(2).get("call_id").asText());
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
                WebClient.builder(), objectMapper, new GatewayProperties(), mockCircuitBreakerManager(), new ReasoningSemanticMapper());
        assertEquals(ProviderType.OPENAI_RESPONSES, client.getProviderType());
    }

    // ==================== 辅助方法 ====================

    /** 创建一个永不熔断的 CircuitBreaker 实例 */
    private CircuitBreakerManager mockCircuitBreakerManager() {
        CircuitBreaker noopCb = CircuitBreaker.of("test",
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(1)
                        .failureRateThreshold(100)
                        .minimumNumberOfCalls(9999)
                        .build());
        CircuitBreakerManager cbManager = Mockito.mock(CircuitBreakerManager.class);
        Mockito.when(cbManager.getOrCreate(Mockito.anyString(), Mockito.anyString())).thenReturn(noopCb);
        return cbManager;
    }

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
                WebClient.builder(), objectMapper, props, mockCircuitBreakerManager(), new ReasoningSemanticMapper());
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

        UnifiedRequest request = new UnifiedRequest();
        request.setProvider("openai-responses");
        request.setModel("gpt-4o");
        request.setSystemPrompt("你是一个助手");
        request.setMessages(List.of(userMsg, assistantMsg, toolResult));
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
