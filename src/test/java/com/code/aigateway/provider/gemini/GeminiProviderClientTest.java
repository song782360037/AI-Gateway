package com.code.aigateway.provider.gemini;

import com.code.aigateway.config.GatewayProperties;
import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.model.*;

import com.code.aigateway.provider.ProviderType;
import com.code.aigateway.provider.util.ProviderTestUtil;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * GeminiProviderClient 单元测试
 * <p>
 * 覆盖要点：
 * <ul>
 *   <li>非流式文本响应 / 函数调用响应解析</li>
 *   <li>流式 JSON 流响应解析（非 SSE）</li>
 *   <li>错误响应处理（Gemini 错误格式）</li>
 *   <li>finishReason 映射（STOP/MAX_TOKENS/FUNCTION_CALL 等）</li>
 *   <li>消息角色映射（assistant->model, tool->user+functionResponse）</li>
 *   <li>工具定义构建（functionDeclarations）</li>
 *   <li>URI 构建（含 model 和 API key）</li>
 *   <li>重试与超时行为</li>
 * </ul>
 */
class GeminiProviderClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer httpServer;
    private GeminiProviderClient providerClient;
    private AtomicReference<String> requestPath;
    private AtomicReference<String> requestQuery;
    private AtomicReference<String> authorizationHeader;
    private AtomicReference<String> requestBody;

    @BeforeEach
    void setUp() {
        requestPath = new AtomicReference<>();
        requestQuery = new AtomicReference<>();
        authorizationHeader = new AtomicReference<>();
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
    void chat_textResponse_parsesSuccessfully() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "candidates": [{
                        "content": {
                          "parts": [{"text": "来自 Gemini 的响应"}],
                          "role": "model"
                        },
                        "finishReason": "STOP"
                      }],
                      "usageMetadata": {
                        "promptTokenCount": 10,
                        "candidatesTokenCount": 5,
                        "totalTokenCount": 15
                      },
                      "modelVersion": "gemini-2.0-flash"
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildTextRequest(false)))
                .assertNext(response -> {
                    // 验证响应基本字段
                    assertNotNull(response.getId());
                    assertTrue(response.getId().startsWith("gemini-"));
                    assertEquals("gemini-2.0-flash", response.getModel());
                    assertEquals("gemini", response.getProvider());
                    assertEquals("stop", response.getFinishReason());

                    // 验证 usage
                    assertNotNull(response.getUsage());
                    assertEquals(10, response.getUsage().getInputTokens());
                    assertEquals(5, response.getUsage().getOutputTokens());
                    assertEquals(15, response.getUsage().getTotalTokens());

                    // 验证输出内容
                    assertEquals(1, response.getOutputs().size());
                    UnifiedOutput output = response.getOutputs().get(0);
                    assertEquals("assistant", output.getRole());
                    assertEquals("来自 Gemini 的响应", output.getParts().get(0).getText());
                })
                .verifyComplete();

        // 验证请求 URI 包含 model 和 API key
        assertTrue(requestPath.get().startsWith("/v1beta/models/gemini-2.0-flash"));
        assertTrue(requestPath.get().contains(":generateContent"));
        assertTrue(requestQuery.get().contains("key=test-gemini-key"));
        // Gemini 不使用 Authorization header
        assertNull(authorizationHeader.get());
    }

    // ==================== 2. 非流式函数调用解析 ====================

    @Test
    void chat_functionCall_parsesToolCalls() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "candidates": [{
                        "content": {
                          "parts": [{"functionCall": {"name": "get_weather", "args": {"city": "Beijing"}}}],
                          "role": "model"
                        },
                        "finishReason": "FUNCTION_CALL"
                      }],
                      "usageMetadata": {
                        "promptTokenCount": 20,
                        "candidatesTokenCount": 10,
                        "totalTokenCount": 30
                      }
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildTextRequest(false)))
                .assertNext(response -> {
                    // finishReason 映射: FUNCTION_CALL -> tool_calls
                    assertEquals("tool_calls", response.getFinishReason());

                    // 验证工具调用解析
                    UnifiedOutput output = response.getOutputs().get(0);
                    assertNotNull(output.getToolCalls());
                    assertEquals(1, output.getToolCalls().size());

                    UnifiedToolCall call = output.getToolCalls().get(0);
                    assertEquals("get_weather", call.getToolName());
                    assertTrue(call.getArgumentsJson().contains("Beijing"));
                    assertTrue(call.getId().startsWith("call_"));
                })
                .verifyComplete();
    }

    @Test
    void chat_mixedTextAndFunctionCall_parsesBoth() {
        // 文本 + 函数调用混合响应
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "candidates": [{
                        "content": {
                          "parts": [
                            {"text": "让我帮你查一下天气"},
                            {"functionCall": {"name": "get_weather", "args": {"city": "Shanghai"}}}
                          ],
                          "role": "model"
                        },
                        "finishReason": "FUNCTION_CALL"
                      }]
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildTextRequest(false)))
                .assertNext(response -> {
                    UnifiedOutput output = response.getOutputs().get(0);
                    // 文本和工具调用都应存在
                    assertEquals("让我帮你查一下天气", output.getParts().get(0).getText());
                    assertNotNull(output.getToolCalls());
                    assertEquals(1, output.getToolCalls().size());
                    assertEquals("get_weather", output.getToolCalls().get(0).getToolName());
                })
                .verifyComplete();
    }

    // ==================== 3. 流式 JSON 流响应解析 ====================

    @Test
    void streamChat_jsonStream_returnsTextDeltaAndDone() {
        startServer(exchange -> {
            captureRequest(exchange);
            // Gemini 流式是 JSON 流，每个 chunk 是一个完整 JSON 对象
            String chunk1 = """
                    {"candidates":[{"content":{"parts":[{"text":"你好"}],"role":"model"},"finishReason":null}]}""";
            String chunk2 = """
                    {"candidates":[{"content":{"parts":[{"text":"，世界"}],"role":"model"},"finishReason":null}]}""";
            String chunk3 = """
                    {"candidates":[{"content":{"parts":[],"role":"model"},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":5,"candidatesTokenCount":4,"totalTokenCount":9}}""";
            writeChunkedResponse(exchange, 200, chunk1, chunk2, chunk3);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.streamChat(buildTextRequest(true)))
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
                    assertNotNull(event.getUsage());
                    assertEquals(5, event.getUsage().getInputTokens());
                })
                .verifyComplete();

        // 验证流式 URI 使用 streamGenerateContent
        assertTrue(requestPath.get().contains(":streamGenerateContent"));
        assertTrue(requestQuery.get().contains("key=test-gemini-key"));
    }

    @Test
    void streamChat_functionCallInStream_returnsToolCallEvents() {
        startServer(exchange -> {
            captureRequest(exchange);
            String chunk = """
                    {"candidates":[{"content":{"parts":[{"functionCall":{"name":"search","args":{"query":"test"}}}],"role":"model"},"finishReason":"FUNCTION_CALL"}]}""";
            writeChunkedResponse(exchange, 200, chunk);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.streamChat(buildTextRequest(true)))
                .assertNext(event -> {
                    assertEquals("tool_call", event.getType());
                    assertEquals("search", event.getToolName());
                    assertNotNull(event.getToolCallId());
                })
                .assertNext(event -> {
                    assertEquals("tool_call_delta", event.getType());
                    assertTrue(event.getArgumentsDelta().contains("test"));
                })
                .assertNext(event -> {
                    assertEquals("done", event.getType());
                    assertEquals("tool_calls", event.getFinishReason());
                })
                .verifyComplete();
    }

    @Test
    void streamChat_jsonArrayChunk_parsesMultipleObjects() {
        // Gemini 有时返回 JSON 数组（多个 chunk 拼在一个响应中）
        startServer(exchange -> {
            captureRequest(exchange);
            String arrayChunk = """
                    [{"candidates":[{"content":{"parts":[{"text":"A"}],"role":"model"},"finishReason":null}]},{"candidates":[{"content":{"parts":[{"text":"B"}],"role":"model"},"finishReason":"STOP"}]}]""";
            writeChunkedResponse(exchange, 200, arrayChunk);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.streamChat(buildTextRequest(true)))
                .assertNext(event -> {
                    assertEquals("text_delta", event.getType());
                    assertEquals("A", event.getTextDelta());
                })
                .assertNext(event -> {
                    assertEquals("text_delta", event.getType());
                    assertEquals("B", event.getTextDelta());
                })
                .assertNext(event -> {
                    assertEquals("done", event.getType());
                    assertEquals("stop", event.getFinishReason());
                })
                .verifyComplete();
    }

    // ==================== 4. 错误响应处理 ====================

    @Test
    void chat_geminiError400_throwsProviderError() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 400, MediaType.APPLICATION_JSON_VALUE, """
                    {"error":{"code":400,"message":"Invalid argument: model not found","status":"INVALID_ARGUMENT"}}
                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildTextRequest(false)))
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof GatewayException);
                    GatewayException ex = (GatewayException) error;
                    assertEquals(ErrorCode.PROVIDER_BAD_REQUEST, ex.getErrorCode());
                    assertTrue(ex.getMessage().contains("Invalid argument"));
                })
                .verify();
    }

    @Test
    void chat_geminiError429_throwsRateLimit() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 429, MediaType.APPLICATION_JSON_VALUE, """
                    {"error":{"code":429,"message":"Resource exhausted","status":"RESOURCE_EXHAUSTED"}}
                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildTextRequest(false)))
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof GatewayException);
                    GatewayException ex = (GatewayException) error;
                    assertEquals(ErrorCode.PROVIDER_RATE_LIMIT, ex.getErrorCode());
                    assertTrue(ex.getMessage().contains("Resource exhausted"));
                })
                .verify();
    }

    @Test
    void chat_geminiError500_throwsServerError() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 500, MediaType.APPLICATION_JSON_VALUE, """
                    {"error":{"code":500,"message":"Internal server error","status":"INTERNAL"}}
                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildTextRequest(false)))
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof GatewayException);
                    GatewayException ex = (GatewayException) error;
                    assertEquals(ErrorCode.PROVIDER_SERVER_ERROR, ex.getErrorCode());
                })
                .verify();
    }

    @Test
    void chat_noCandidates_throwsProviderError() {
        startServer(exchange -> {
            captureRequest(exchange);
            // 空响应无 candidates
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, "{}");
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildTextRequest(false)))
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof GatewayException);
                    GatewayException ex = (GatewayException) error;
                    assertEquals(ErrorCode.PROVIDER_ERROR, ex.getErrorCode());
                    assertTrue(ex.getMessage().contains("no candidates"));
                })
                .verify();
    }

    @Test
    void chat_promptBlocked_throwsProviderError() {
        startServer(exchange -> {
            captureRequest(exchange);
            // 安全过滤触发 promptFeedback
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {"promptFeedback":{"blockReason":"SAFETY","blockReasonMessage":"unsafe content"}}
                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildTextRequest(false)))
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof GatewayException);
                    GatewayException ex = (GatewayException) error;
                    assertEquals(ErrorCode.PROVIDER_ERROR, ex.getErrorCode());
                    assertTrue(ex.getMessage().contains("prompt blocked"));
                })
                .verify();
    }

    @Test
    void chat_errorInResponseJson_throwsProviderError() {
        startServer(exchange -> {
            captureRequest(exchange);
            // Gemini 在 response body 中内嵌 error 字段
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {"error":{"message":"quota exceeded","code":429}}
                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildTextRequest(false)))
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof GatewayException);
                    GatewayException ex = (GatewayException) error;
                    assertEquals(ErrorCode.PROVIDER_ERROR, ex.getErrorCode());
                    assertTrue(ex.getMessage().contains("quota exceeded"));
                })
                .verify();
    }

    // ==================== 5. finishReason 映射 ====================

    @Test
    void chat_finishReasonSTOP_mapsToStop() {
        verifyFinishReason("STOP", "stop");
    }

    @Test
    void chat_finishReasonMAX_TOKENS_mapsToLength() {
        verifyFinishReason("MAX_TOKENS", "length");
    }

    @Test
    void chat_finishReasonFUNCTION_CALL_mapsToToolCalls() {
        verifyFinishReason("FUNCTION_CALL", "tool_calls");
    }

    @Test
    void chat_finishReasonSAFETY_mapsToContentFilter() {
        verifyFinishReason("SAFETY", "content_filter");
    }

    @Test
    void chat_finishReasonRECITATION_mapsToContentFilter() {
        verifyFinishReason("RECITATION", "content_filter");
    }

    private void verifyFinishReason(String geminiReason, String expectedUnified) {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "candidates": [{
                        "content": {"parts": [{"text": "test"}], "role": "model"},
                        "finishReason": "%s"
                      }]
                    }
                    """.formatted(geminiReason));
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildTextRequest(false)))
                .assertNext(response -> assertEquals(expectedUnified, response.getFinishReason()))
                .verifyComplete();
    }

    // ==================== 6. 消息角色映射 ====================

    @Test
    void chat_requestBody_mapsUserAndAssistantRoles() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "candidates": [{
                        "content": {"parts": [{"text": "ok"}], "role": "model"},
                        "finishReason": "STOP"
                      }]
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        // 构建含 user + assistant 消息的请求
        UnifiedPart userPart = new UnifiedPart();
        userPart.setType("text");
        userPart.setText("用户消息");

        UnifiedMessage userMsg = new UnifiedMessage();
        userMsg.setRole("user");
        userMsg.setParts(List.of(userPart));

        UnifiedPart assistantPart = new UnifiedPart();
        assistantPart.setType("text");
        assistantPart.setText("助手回复");

        UnifiedMessage assistantMsg = new UnifiedMessage();
        assistantMsg.setRole("assistant");
        assistantMsg.setParts(List.of(assistantPart));

        UnifiedRequest request = buildTextRequest(false);
        request.setMessages(List.of(userMsg, assistantMsg, userMsg));

        StepVerifier.create(providerClient.chat(request))
                .assertNext(response -> assertEquals("stop", response.getFinishReason()))
                .verifyComplete();

        // 验证请求体：assistant 应映射为 model
        assertTrue(requestBody.get().contains("\"role\":\"model\""));
        assertTrue(requestBody.get().contains("\"role\":\"user\""));
        assertFalse(requestBody.get().contains("\"role\":\"assistant\""));
    }

    @Test
    void chat_requestBody_mapsToolRoleToFunctionResponse() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "candidates": [{
                        "content": {"parts": [{"text": "result"}], "role": "model"},
                        "finishReason": "STOP"
                      }]
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        // 构建含 tool 角色消息的请求
        UnifiedPart userPart = new UnifiedPart();
        userPart.setType("text");
        userPart.setText("查天气");

        UnifiedMessage userMsg = new UnifiedMessage();
        userMsg.setRole("user");
        userMsg.setParts(List.of(userPart));

        UnifiedPart toolResult = new UnifiedPart();
        toolResult.setType("text");
        toolResult.setText("晴天，25度");

        UnifiedMessage toolMsg = new UnifiedMessage();
        toolMsg.setRole("tool");
        toolMsg.setToolName("get_weather");
        toolMsg.setParts(List.of(toolResult));

        UnifiedRequest request = buildTextRequest(false);
        request.setMessages(List.of(userMsg, toolMsg));

        StepVerifier.create(providerClient.chat(request))
                .assertNext(response -> assertEquals("stop", response.getFinishReason()))
                .verifyComplete();

        // 验证请求体：tool 角色应映射为 functionResponse，且 role 为 user
        assertTrue(requestBody.get().contains("functionResponse"));
        assertTrue(requestBody.get().contains("\"name\":\"get_weather\""));
        assertTrue(requestBody.get().contains("晴天，25度"));
        // tool 映射后的 role 是 user
        assertFalse(requestBody.get().contains("\"role\":\"tool\""));
    }

    // ==================== 7. 工具定义构建 ====================

    @Test
    void chat_requestBody_includesFunctionDeclarations() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "candidates": [{
                        "content": {"parts": [{"text": "调用工具"}], "role": "model"},
                        "finishReason": "STOP"
                      }]
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        // 构建含工具的请求
        UnifiedTool tool = new UnifiedTool();
        tool.setName("get_weather");
        tool.setDescription("获取指定城市的天气信息");
        tool.setInputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                        "city", Map.of("type", "string", "description", "城市名称")
                ),
                "required", List.of("city")
        ));

        UnifiedRequest request = buildTextRequest(false);
        request.setTools(List.of(tool));

        StepVerifier.create(providerClient.chat(request))
                .assertNext(response -> assertEquals("stop", response.getFinishReason()))
                .verifyComplete();

        // 验证请求体使用 functionDeclarations 格式
        assertTrue(requestBody.get().contains("functionDeclarations"));
        assertTrue(requestBody.get().contains("get_weather"));
        assertTrue(requestBody.get().contains("获取指定城市的天气信息"));
    }

    @Test
    void chat_requestBody_includesToolConfig() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "candidates": [{
                        "content": {"parts": [{"text": "ok"}], "role": "model"},
                        "finishReason": "STOP"
                      }]
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        UnifiedTool tool = new UnifiedTool();
        tool.setName("get_weather");
        tool.setDescription("获取天气");

        UnifiedToolChoice choice = new UnifiedToolChoice();
        choice.setType("specific");
        choice.setToolName("get_weather");

        UnifiedRequest request = buildTextRequest(false);
        request.setTools(List.of(tool));
        request.setToolChoice(choice);

        StepVerifier.create(providerClient.chat(request))
                .assertNext(response -> assertEquals("stop", response.getFinishReason()))
                .verifyComplete();

        // 验证 toolConfig 包含 functionCallingConfig
        assertTrue(requestBody.get().contains("functionCallingConfig"));
        assertTrue(requestBody.get().contains("\"mode\":\"ANY\""));
        assertTrue(requestBody.get().contains("allowedFunctionNames"));
    }

    // ==================== 8. URI 构建 ====================

    @Test
    void chat_uriContainsModelAndApiKey() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "candidates": [{
                        "content": {"parts": [{"text": "ok"}], "role": "model"},
                        "finishReason": "STOP"
                      }]
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildTextRequest(false)))
                .assertNext(response -> assertNotNull(response))
                .verifyComplete();

        // 验证 URI 格式: /v1beta/models/{model}:generateContent?key={apiKey}
        assertEquals("/v1beta/models/gemini-2.0-flash:generateContent", requestPath.get());
        assertEquals("key=test-gemini-key", requestQuery.get());
    }

    @Test
    void streamChat_uriContainsStreamEndpoint() {
        startServer(exchange -> {
            captureRequest(exchange);
            String chunk = """
                    {"candidates":[{"content":{"parts":[{"text":"x"}],"role":"model"},"finishReason":"STOP"}]}""";
            writeChunkedResponse(exchange, 200, chunk);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.streamChat(buildTextRequest(true)))
                .expectNextCount(2) // text_delta + done
                .verifyComplete();

        // 流式 URI: /v1beta/models/{model}:streamGenerateContent?alt=sse&key={apiKey}
        assertTrue(requestPath.get().contains(":streamGenerateContent"));
        assertTrue(requestQuery.get().contains("alt=sse"));
        assertTrue(requestQuery.get().contains("key=test-gemini-key"));
    }

    // ==================== 9. 请求体结构验证 ====================

    @Test
    void chat_requestBody_includesSystemInstruction() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "candidates": [{
                        "content": {"parts": [{"text": "ok"}], "role": "model"},
                        "finishReason": "STOP"
                      }]
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        UnifiedRequest request = buildTextRequest(false);
        request.setSystemPrompt("你是一个天气助手");

        StepVerifier.create(providerClient.chat(request))
                .assertNext(response -> assertNotNull(response))
                .verifyComplete();

        // 验证 systemInstruction 格式
        assertTrue(requestBody.get().contains("systemInstruction"));
        assertTrue(requestBody.get().contains("你是一个天气助手"));
    }

    @Test
    void chat_requestBody_includesGenerationConfig() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "candidates": [{
                        "content": {"parts": [{"text": "ok"}], "role": "model"},
                        "finishReason": "STOP"
                      }]
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildTextRequest(false)))
                .assertNext(response -> assertNotNull(response))
                .verifyComplete();

        // 验证 generationConfig 参数
        assertTrue(requestBody.get().contains("generationConfig"));
        assertTrue(requestBody.get().contains("temperature"));
        assertTrue(requestBody.get().contains("topP"));
        assertTrue(requestBody.get().contains("maxOutputTokens"));
    }

    @Test
    void chat_requestBody_emptyMessages_emptyContents() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "candidates": [{
                        "content": {"parts": [{"text": "ok"}], "role": "model"},
                        "finishReason": "STOP"
                      }]
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        UnifiedRequest request = buildTextRequest(false);
        request.setMessages(null);

        StepVerifier.create(providerClient.chat(request))
                .assertNext(response -> assertNotNull(response))
                .verifyComplete();

        // 空消息列表应生成空 contents 数组
        assertTrue(requestBody.get().contains("\"contents\":[]"));
    }

    // ==================== 10. 超时与重试 ====================

    @Test
    void chat_timeout_throwsProviderTimeout() {
        startServer(exchange -> {
            captureRequest(exchange);
            sleep(1500);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {"candidates":[{"content":{"parts":[{"text":"late"}],"role":"model"},"finishReason":"STOP"}]}
                    """);
        });
        providerClient = newProviderClient(1);

        StepVerifier.create(providerClient.chat(buildTextRequest(false)))
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof GatewayException);
                    GatewayException ex = (GatewayException) error;
                    assertEquals(ErrorCode.PROVIDER_TIMEOUT, ex.getErrorCode());
                })
                .verify(Duration.ofSeconds(3));
    }

    @Test
    void chat_5xxRetry_thenSuccess_returnsResponse() {
        AtomicInteger requestCount = new AtomicInteger(0);
        startServer(exchange -> {
            captureRequest(exchange);
            int count = requestCount.incrementAndGet();
            if (count <= 2) {
                writeResponse(exchange, 500, MediaType.APPLICATION_JSON_VALUE,
                        "{\"error\":{\"message\":\"internal error\"}}");
            } else {
                writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                        {
                          "candidates": [{
                            "content": {"parts": [{"text": "重试成功"}], "role": "model"},
                            "finishReason": "STOP"
                          }]
                        }
                        """);
            }
        });
        providerClient = newProviderClientWithRetry(5, 3, 100, 1000);

        StepVerifier.create(providerClient.chat(buildTextRequest(false)))
                .assertNext(response -> {
                    assertEquals("重试成功", response.getOutputs().get(0).getParts().get(0).getText());
                })
                .verifyComplete();

        assertEquals(3, requestCount.get());
    }

    @Test
    void chat_5xxRetry_exhausted_throwsServerError() {
        AtomicInteger requestCount = new AtomicInteger(0);
        startServer(exchange -> {
            requestCount.incrementAndGet();
            writeResponse(exchange, 500, MediaType.APPLICATION_JSON_VALUE,
                    "{\"error\":{\"message\":\"persistent failure\"}}");
        });
        providerClient = newProviderClientWithRetry(5, 2, 100, 1000);

        StepVerifier.create(providerClient.chat(buildTextRequest(false)))
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof GatewayException);
                    GatewayException ex = (GatewayException) error;
                    assertEquals(ErrorCode.PROVIDER_SERVER_ERROR, ex.getErrorCode());
                })
                .verify(Duration.ofSeconds(10));

        assertEquals(3, requestCount.get());
    }

    @Test
    void chat_4xx_noRetry_failsImmediately() {
        AtomicInteger requestCount = new AtomicInteger(0);
        startServer(exchange -> {
            requestCount.incrementAndGet();
            writeResponse(exchange, 400, MediaType.APPLICATION_JSON_VALUE,
                    "{\"error\":{\"message\":\"bad request\"}}");
        });
        providerClient = newProviderClientWithRetry(5, 3, 100, 1000);

        StepVerifier.create(providerClient.chat(buildTextRequest(false)))
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof GatewayException);
                    GatewayException ex = (GatewayException) error;
                    assertEquals(ErrorCode.PROVIDER_BAD_REQUEST, ex.getErrorCode());
                })
                .verify(Duration.ofSeconds(5));

        assertEquals(1, requestCount.get());
    }

    @Test
    void chat_noRetryConfig_500_failsImmediately() {
        AtomicInteger requestCount = new AtomicInteger(0);
        startServer(exchange -> {
            requestCount.incrementAndGet();
            writeResponse(exchange, 500, MediaType.APPLICATION_JSON_VALUE,
                    "{\"error\":{\"message\":\"server error\"}}");
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildTextRequest(false)))
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof GatewayException);
                    GatewayException ex = (GatewayException) error;
                    assertEquals(ErrorCode.PROVIDER_SERVER_ERROR, ex.getErrorCode());
                })
                .verify(Duration.ofSeconds(5));

        assertEquals(1, requestCount.get());
    }

    // ==================== 11. 流式重试 ====================

    @Test
    void streamChat_5xxBeforeFirstToken_retriesAndSucceeds() {
        AtomicInteger requestCount = new AtomicInteger(0);
        startServer(exchange -> {
            captureRequest(exchange);
            int count = requestCount.incrementAndGet();
            if (count <= 2) {
                writeResponse(exchange, 500, MediaType.APPLICATION_JSON_VALUE,
                        "{\"error\":{\"message\":\"server error\"}}");
            } else {
                String chunk = """
                        {"candidates":[{"content":{"parts":[{"text":"流式重试成功"}],"role":"model"},"finishReason":"STOP"}]}""";
                writeChunkedResponse(exchange, 200, chunk);
            }
        });
        providerClient = newProviderClientWithRetry(5, 3, 100, 1000);

        StepVerifier.create(providerClient.streamChat(buildTextRequest(true)))
                .assertNext(event -> {
                    assertEquals("text_delta", event.getType());
                    assertEquals("流式重试成功", event.getTextDelta());
                })
                .assertNext(event -> {
                    assertEquals("done", event.getType());
                    assertEquals("stop", event.getFinishReason());
                })
                .verifyComplete();

        assertEquals(3, requestCount.get());
    }

    @Test
    void streamChat_invalidChunk_throwsStreamParseError() {
        startServer(exchange -> {
            captureRequest(exchange);
            // 返回一个合法 chunk 后接一个非法 chunk
            String valid = """
                    {"candidates":[{"content":{"parts":[{"text":"ok"}],"role":"model"},"finishReason":null}]}""";
            String invalid = "{not-json}";
            writeChunkedResponse(exchange, 200, valid, invalid);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.streamChat(buildTextRequest(true)))
                .assertNext(event -> {
                    assertEquals("text_delta", event.getType());
                    assertEquals("ok", event.getTextDelta());
                })
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof GatewayException);
                    GatewayException ex = (GatewayException) error;
                    assertEquals(ErrorCode.STREAM_PARSE_ERROR, ex.getErrorCode());
                })
                .verify();
    }

    // ==================== 12. providerType ====================

    @Test
    void getProviderType_returnsGemini() {
        // 不需要启动 server
        GeminiProviderClient client = new GeminiProviderClient(
                new ReactorClientHttpConnector(), objectMapper, new GatewayProperties(), ProviderTestUtil.noopCircuitBreakerManager());
        assertEquals(ProviderType.GEMINI, client.getProviderType());
    }

    // ==================== 辅助方法 ====================

    private GeminiProviderClient newProviderClient(int timeoutSeconds) {
        return newProviderClientWithRetry(timeoutSeconds, 0, 1000, 30000);
    }

    private GeminiProviderClient newProviderClientWithRetry(
            int timeoutSeconds, int maxRetries, long initialIntervalMs, long maxIntervalMs) {
        GatewayProperties gatewayProperties = new GatewayProperties();
        if (maxRetries > 0) {
            GatewayProperties.RetryProperties retryProps = new GatewayProperties.RetryProperties();
            retryProps.setMaxRetries(maxRetries);
            retryProps.setInitialIntervalMs(initialIntervalMs);
            retryProps.setMaxIntervalMs(maxIntervalMs);
            gatewayProperties.setRetry(retryProps);
        }
        GatewayProperties.ProviderProperties providerProperties = new GatewayProperties.ProviderProperties();
        providerProperties.setEnabled(true);
        providerProperties.setBaseUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());
        providerProperties.setApiKey("test-gemini-key");
        providerProperties.setTimeoutSeconds(timeoutSeconds);
        gatewayProperties.setProviders(Map.of("gemini", providerProperties));
        return new GeminiProviderClient(new ReactorClientHttpConnector(), objectMapper, gatewayProperties, ProviderTestUtil.noopCircuitBreakerManager());
    }

    private UnifiedRequest buildTextRequest(boolean stream) {
        UnifiedPart userPart = new UnifiedPart();
        userPart.setType("text");
        userPart.setText("你好");

        UnifiedMessage userMessage = new UnifiedMessage();
        userMessage.setRole("user");
        userMessage.setParts(List.of(userPart));

        UnifiedGenerationConfig generationConfig = new UnifiedGenerationConfig();
        generationConfig.setTemperature(0.7);
        generationConfig.setTopP(0.95);
        generationConfig.setMaxOutputTokens(256);
        generationConfig.setStopSequences(List.of("END"));

        UnifiedRequest request = new UnifiedRequest();
        request.setProvider("gemini");
        request.setModel("gemini-2.0-flash");
        request.setSystemPrompt("你是一个助手");
        request.setMessages(List.of(userMessage));
        request.setGenerationConfig(generationConfig);
        request.setStream(stream);

        UnifiedRequest.ProviderExecutionContext ctx = new UnifiedRequest.ProviderExecutionContext();
        ctx.setProviderName("gemini");
        ctx.setProviderBaseUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());
        request.setExecutionContext(ctx);
        return request;
    }

    /**
     * 启动测试 HTTP Server，为所有 Gemini 端点路径注册处理器。
     * Gemini URI 格式为 /v1beta/models/{model}:generateContent，
     * 使用根路径 "/" 前缀匹配所有请求。
     */
    private void startServer(ThrowingHandler handler) {
        try {
            httpServer = HttpServer.create(new InetSocketAddress(0), 0);
            // Gemini 端点路径包含动态 model 名，用根路径匹配
            httpServer.createContext("/", exchange -> {
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
        requestQuery.set(exchange.getRequestURI().getQuery());
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

    /**
     * 模拟 Gemini 流式 SSE 响应：每个 chunk 以 SSE data: 格式发送。
     */
    private void writeChunkedResponse(HttpExchange exchange, int statusCode, String... chunks) {
        try {
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "text/event-stream");
            // chunked 模式：0 表示分块传输
            exchange.sendResponseHeaders(statusCode, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                for (String chunk : chunks) {
                    os.write(("data: " + chunk + "\n\n").getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to write chunked response", e);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("sleep interrupted", e);
        }
    }

    @FunctionalInterface
    private interface ThrowingHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
