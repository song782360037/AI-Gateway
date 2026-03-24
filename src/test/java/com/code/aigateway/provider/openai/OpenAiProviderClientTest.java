package com.code.aigateway.provider.openai;

import com.code.aigateway.config.GatewayProperties;
import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.model.UnifiedGenerationConfig;
import com.code.aigateway.core.model.UnifiedMessage;
import com.code.aigateway.core.model.UnifiedPart;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedResponse;
import com.code.aigateway.core.model.UnifiedStreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiProviderClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer httpServer;
    private OpenAiProviderClient providerClient;
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

    @Test
    void chat_success_returnsUnifiedResponse() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "id": "chatcmpl-upstream-1",
                      "object": "chat.completion",
                      "created": 1710000000,
                      "model": "gpt-5.4",
                      "choices": [
                        {
                          "index": 0,
                          "message": {
                            "role": "assistant",
                            "content": "来自远端 OpenAI 兼容服务的响应"
                          },
                          "finish_reason": "stop"
                        }
                      ],
                      "usage": {
                        "prompt_tokens": 11,
                        "completion_tokens": 7,
                        "total_tokens": 18
                      }
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildRequest(false)))
                .assertNext(response -> {
                    assertEquals("chatcmpl-upstream-1", response.getId());
                    assertEquals("gpt-5.4", response.getModel());
                    assertEquals("openai", response.getProvider());
                    assertEquals("stop", response.getFinishReason());
                    assertNotNull(response.getUsage());
                    assertEquals(11, response.getUsage().getInputTokens());
                    assertEquals(7, response.getUsage().getOutputTokens());
                    assertEquals(18, response.getUsage().getTotalTokens());
                    assertEquals("assistant", response.getOutputs().get(0).getRole());
                    assertEquals("来自远端 OpenAI 兼容服务的响应", response.getOutputs().get(0).getParts().get(0).getText());
                })
                .verifyComplete();

        assertEquals("/v1/chat/completions", requestPath.get());
        assertEquals("Bearer test-openai-key", authorizationHeader.get());
        assertTrue(requestBody.get().contains("\"model\":\"gpt-5.4\""));
        assertTrue(requestBody.get().contains("\"stream\":false"));
        assertTrue(requestBody.get().contains("\"messages\""));
    }

    @Test
    void streamChat_success_returnsOrderedTextDeltaEventsAndDone() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.TEXT_EVENT_STREAM_VALUE, """
                    data: {"id":"chatcmpl-stream-1","object":"chat.completion.chunk","created":1710000000,"model":"gpt-5.4","choices":[{"index":0,"delta":{"content":"你好"},"finish_reason":null}]}

                    data: {"id":"chatcmpl-stream-1","object":"chat.completion.chunk","created":1710000000,"model":"gpt-5.4","choices":[{"index":0,"delta":{"content":"，世界"},"finish_reason":null}]}

                    data: {"id":"chatcmpl-stream-1","object":"chat.completion.chunk","created":1710000000,"model":"gpt-5.4","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

                    data: [DONE]

                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.streamChat(buildRequest(true)))
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
                })
                .verifyComplete();

        assertEquals("/v1/chat/completions", requestPath.get());
        assertEquals("Bearer test-openai-key", authorizationHeader.get());
        assertTrue(requestBody.get().contains("\"stream\":true"));
    }

    @Test
    void chat_rateLimited_throwsProviderRateLimit() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 429, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "error": {
                        "message": "rate limit exceeded",
                        "type": "rate_limit_error"
                      }
                    }
                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildRequest(false)))
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof GatewayException);
                    GatewayException gatewayException = (GatewayException) error;
                    assertEquals(ErrorCode.PROVIDER_RATE_LIMIT, gatewayException.getErrorCode());
                    assertTrue(gatewayException.getMessage().contains("rate limit exceeded"));
                })
                .verify();
    }

    @Test
    void chat_timeout_throwsProviderTimeout() {
        startServer(exchange -> {
            captureRequest(exchange);
            sleep(1500);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                    {
                      "id": "chatcmpl-timeout",
                      "model": "gpt-5.4",
                      "choices": [
                        {
                          "index": 0,
                          "message": {
                            "role": "assistant",
                            "content": "late response"
                          },
                          "finish_reason": "stop"
                        }
                      ]
                    }
                    """);
        });
        providerClient = newProviderClient(1);

        StepVerifier.create(providerClient.chat(buildRequest(false)))
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof GatewayException);
                    GatewayException gatewayException = (GatewayException) error;
                    assertEquals(ErrorCode.PROVIDER_TIMEOUT, gatewayException.getErrorCode());
                })
                .verify(Duration.ofSeconds(3));
    }

    @Test
    void streamChat_invalidChunk_throwsStreamParseError() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.TEXT_EVENT_STREAM_VALUE, """
                    data: {"id":"chatcmpl-stream-1","object":"chat.completion.chunk","created":1710000000,"model":"gpt-5.4","choices":[{"index":0,"delta":{"content":"合法片段"},"finish_reason":null}]}

                    data: {not-json}

                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.streamChat(buildRequest(true)))
                .assertNext(event -> {
                    assertEquals("text_delta", event.getType());
                    assertEquals("合法片段", event.getTextDelta());
                })
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof GatewayException);
                    GatewayException gatewayException = (GatewayException) error;
                    assertEquals(ErrorCode.STREAM_PARSE_ERROR, gatewayException.getErrorCode());
                })
                .verify();
    }

    @Test
    void chat_invalidResponseWithoutChoices_throwsProviderError() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.APPLICATION_JSON_VALUE, "{}\n");
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildRequest(false)))
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof GatewayException);
                    GatewayException gatewayException = (GatewayException) error;
                    assertEquals(ErrorCode.PROVIDER_ERROR, gatewayException.getErrorCode());
                    assertTrue(gatewayException.getMessage().contains("invalid upstream response"));
                })
                .verify();
    }

    @Test
    void streamChat_errorPayloadInsideSse_throwsProviderError() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 200, MediaType.TEXT_EVENT_STREAM_VALUE, """
                    data: {"error":{"message":"upstream denied request"}}

                    """);
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.streamChat(buildRequest(true)))
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof GatewayException);
                    GatewayException gatewayException = (GatewayException) error;
                    assertEquals(ErrorCode.PROVIDER_ERROR, gatewayException.getErrorCode());
                    assertEquals("provider stream failed", gatewayException.getMessage());
                })
                .verify();
    }

    @Test
    void chat_upstream500_masksInternalDetails() {
        startServer(exchange -> {
            captureRequest(exchange);
            writeResponse(exchange, 500, MediaType.TEXT_HTML_VALUE,
                    "<html><body>connect to http://internal-host:8080 failed</body></html>");
        });
        providerClient = newProviderClient(5);

        StepVerifier.create(providerClient.chat(buildRequest(false)))
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof GatewayException);
                    GatewayException gatewayException = (GatewayException) error;
                    assertEquals(ErrorCode.PROVIDER_ERROR, gatewayException.getErrorCode());
                    assertEquals("provider request failed", gatewayException.getMessage());
                })
                .verify();
    }

    private OpenAiProviderClient newProviderClient(int timeoutSeconds) {
        GatewayProperties gatewayProperties = new GatewayProperties();
        GatewayProperties.ProviderProperties providerProperties = new GatewayProperties.ProviderProperties();
        providerProperties.setEnabled(true);
        providerProperties.setBaseUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());
        providerProperties.setApiKey("test-openai-key");
        providerProperties.setTimeoutSeconds(timeoutSeconds);
        gatewayProperties.setProviders(Map.of("openai", providerProperties));
        return new OpenAiProviderClient(WebClient.builder(), objectMapper, gatewayProperties);
    }

    private UnifiedRequest buildRequest(boolean stream) {
        UnifiedPart userPart = new UnifiedPart();
        userPart.setType("text");
        userPart.setText("你好，帮我做个摘要");

        UnifiedMessage userMessage = new UnifiedMessage();
        userMessage.setRole("user");
        userMessage.setParts(List.of(userPart));

        UnifiedGenerationConfig generationConfig = new UnifiedGenerationConfig();
        generationConfig.setTemperature(0.2);
        generationConfig.setTopP(0.9);
        generationConfig.setMaxOutputTokens(256);
        generationConfig.setStopSequences(List.of("END"));

        UnifiedRequest request = new UnifiedRequest();
        request.setProvider("openai");
        request.setModel("gpt-5.4");
        request.setSystemPrompt("你是一个严谨的助手");
        request.setMessages(List.of(userMessage));
        request.setGenerationConfig(generationConfig);
        request.setStream(stream);

        UnifiedRequest.ProviderExecutionContext executionContext = new UnifiedRequest.ProviderExecutionContext();
        executionContext.setProviderName("openai");
        executionContext.setProviderBaseUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());
        request.setExecutionContext(executionContext);
        return request;
    }

    private void startServer(ThrowingHandler handler) {
        try {
            httpServer = HttpServer.create(new InetSocketAddress(0), 0);
            httpServer.createContext("/v1/chat/completions", exchange -> {
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
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bodyBytes);
                outputStream.flush();
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to write response", e);
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
