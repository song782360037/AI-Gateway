package com.code.aigateway.core.service;

import com.code.aigateway.api.request.OpenAiChatCompletionRequest;
import com.code.aigateway.core.capability.CapabilityChecker;
import com.code.aigateway.core.model.UnifiedStreamEvent;
import com.code.aigateway.core.protocol.OpenAiChatProtocolAdapter;
import com.code.aigateway.core.router.ModelRouter;
import com.code.aigateway.core.router.RouteResult;
import com.code.aigateway.provider.ProviderClient;
import com.code.aigateway.provider.ProviderClientFactory;
import com.code.aigateway.provider.ProviderType;
import com.code.aigateway.core.resilience.FailoverStrategy;
import com.code.aigateway.core.stats.RequestStatsCollector;
import com.code.aigateway.core.stats.RequestStatsContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatGatewayServiceStreamSseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OpenAiChatProtocolAdapter protocolAdapter;
    private ModelRouter modelRouter;
    private CapabilityChecker capabilityChecker;
    private ProviderClientFactory providerClientFactory;
    private ProviderClient providerClient;
    private ChatGatewayService chatGatewayService;
    private FailoverStrategy failoverStrategy;

    @BeforeEach
    void setUp() {
        // 使用真实的 OpenAiChatProtocolAdapter（内含真实的 parser、encoder、ObjectMapper）
        protocolAdapter = new OpenAiChatProtocolAdapter(
                new com.code.aigateway.core.parser.OpenAiChatRequestParser(),
                new com.code.aigateway.core.encoder.OpenAiChatResponseEncoder(),
                objectMapper
        );
        modelRouter = Mockito.mock(ModelRouter.class);
        capabilityChecker = Mockito.mock(CapabilityChecker.class);
        providerClientFactory = Mockito.mock(ProviderClientFactory.class);
        providerClient = Mockito.mock(ProviderClient.class);

        // 让 failover 直接执行传入的 function，跳过真实故障转移逻辑
        failoverStrategy = Mockito.mock(FailoverStrategy.class);
        Mockito.when(failoverStrategy.executeStreamWithFailover(
                Mockito.anyList(), Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Function<RouteResult, Flux<UnifiedStreamEvent>> fn = invocation.getArgument(1);
                    List<RouteResult> candidates = invocation.getArgument(0);
                    return fn.apply(candidates.get(0));
                });

        chatGatewayService = new ChatGatewayService(
                modelRouter, capabilityChecker, providerClientFactory,
                Mockito.mock(RequestStatsCollector.class), failoverStrategy);

        Mockito.when(providerClientFactory.getClient(ProviderType.OPENAI)).thenReturn(providerClient);
    }

    @Test
    void streamChat_textDeltaAndDone_encodeOpenAiChunkContract() {
        OpenAiChatCompletionRequest request = buildRequest();
        RouteResult routeResult = RouteResult.builder()
                .providerType(ProviderType.OPENAI)
                .targetModel("gpt-5.4")
                .providerName("openai")
                .providerBaseUrl("http://provider.test")
                .providerTimeoutSeconds(30)
                .build();

        UnifiedStreamEvent textEvent = new UnifiedStreamEvent();
        textEvent.setType("text_delta");
        textEvent.setTextDelta("你好");

        UnifiedStreamEvent doneEvent = new UnifiedStreamEvent();
        doneEvent.setType("done");
        doneEvent.setFinishReason("stop");

        Mockito.when(modelRouter.routeAll(Mockito.any())).thenReturn(List.of(routeResult));
        Mockito.when(providerClient.streamChat(Mockito.any())).thenReturn(Flux.just(textEvent, doneEvent));

        // 传入非 null context，resolveCandidates 会自动设置 routeResult，使 SSE chunk 携带正确的 model
        Flux<ServerSentEvent<String>> flux = chatGatewayService.streamChat(request, protocolAdapter, new RequestStatsContext())
                .map(e -> (ServerSentEvent<String>) e);

        StepVerifier.create(flux)
                .assertNext(first -> assertTextChunk(first, "你好", "gpt-5.4"))
                .assertNext(this::assertDoneChunk)
                .assertNext(last -> assertEquals("[DONE]", last.data()))
                .verifyComplete();
    }

    @Test
    void streamChat_doneWithoutFinishReason_defaultsToStopAndAppendsDoneMarker() {
        OpenAiChatCompletionRequest request = buildRequest();
        RouteResult routeResult = RouteResult.builder()
                .providerType(ProviderType.OPENAI)
                .targetModel("gpt-5.4")
                .providerName("openai")
                .providerBaseUrl("http://provider.test")
                .providerTimeoutSeconds(30)
                .build();

        UnifiedStreamEvent doneEvent = new UnifiedStreamEvent();
        doneEvent.setType("done");

        Mockito.when(modelRouter.routeAll(Mockito.any())).thenReturn(List.of(routeResult));
        Mockito.when(providerClient.streamChat(Mockito.any())).thenReturn(Flux.just(doneEvent));

        Flux<ServerSentEvent<String>> flux = chatGatewayService.streamChat(request, protocolAdapter, new RequestStatsContext())
                .map(e -> (ServerSentEvent<String>) e);

        StepVerifier.create(flux)
                .assertNext(event -> {
                    JsonNode jsonNode = parseJson(event.data());
                    assertEquals("stop", jsonNode.path("choices").get(0).path("finish_reason").asText());
                })
                .assertNext(last -> assertEquals("[DONE]", last.data()))
                .verifyComplete();
    }

    @Test
    void streamChat_textDeltaWithSpecialCharacters_escapesJsonAndRemainsParsable() {
        OpenAiChatCompletionRequest request = buildRequest();
        RouteResult routeResult = RouteResult.builder()
                .providerType(ProviderType.OPENAI)
                .targetModel("gpt-5.4")
                .providerName("openai")
                .providerBaseUrl("http://provider.test")
                .providerTimeoutSeconds(30)
                .build();

        UnifiedStreamEvent textEvent = new UnifiedStreamEvent();
        textEvent.setType("text_delta");
        textEvent.setTextDelta("包含\"引号\"、反斜杠\\以及\n换行");

        UnifiedStreamEvent doneEvent = new UnifiedStreamEvent();
        doneEvent.setType("done");
        doneEvent.setFinishReason("stop");

        Mockito.when(modelRouter.routeAll(Mockito.any())).thenReturn(List.of(routeResult));
        Mockito.when(providerClient.streamChat(Mockito.any())).thenReturn(Flux.just(textEvent, doneEvent));

        Flux<ServerSentEvent<String>> flux = chatGatewayService.streamChat(request, protocolAdapter, new RequestStatsContext())
                .map(e -> (ServerSentEvent<String>) e);

        StepVerifier.create(flux)
                .assertNext(event -> {
                    JsonNode jsonNode = parseJson(event.data());
                    assertEquals("包含\"引号\"、反斜杠\\以及\n换行", jsonNode.path("choices").get(0).path("delta").path("content").asText());
                })
                .thenConsumeWhile(sse -> true)
                .verifyComplete();
    }

    @Test
    void streamChat_providerFailsBeforeFirstChunk_returnsStructuredErrorAndDoneMarker() {
        OpenAiChatCompletionRequest request = buildRequest();
        RouteResult routeResult = RouteResult.builder()
                .providerType(ProviderType.OPENAI)
                .targetModel("gpt-5.4")
                .providerName("openai")
                .providerBaseUrl("http://provider.test")
                .providerTimeoutSeconds(30)
                .build();

        Mockito.when(modelRouter.routeAll(Mockito.any())).thenReturn(List.of(routeResult));
        Mockito.when(providerClient.streamChat(Mockito.any()))
                .thenReturn(Flux.error(new RuntimeException("upstream exploded")));

        Flux<ServerSentEvent<String>> flux = chatGatewayService.streamChat(request, protocolAdapter, new RequestStatsContext())
                .map(e -> (ServerSentEvent<String>) e);

        StepVerifier.create(flux)
                .assertNext(event -> {
                    JsonNode jsonNode = parseJson(event.data());
                    assertEquals("upstream exploded", jsonNode.path("error").path("message").asText());
                    assertEquals("server_error", jsonNode.path("error").path("type").asText());
                })
                .assertNext(last -> assertEquals("[DONE]", last.data()))
                .verifyComplete();
    }

    @Test
    void streamChat_providerFailsAfterTextDelta_returnsStructuredErrorAndDoneMarker() {
        OpenAiChatCompletionRequest request = buildRequest();
        RouteResult routeResult = RouteResult.builder()
                .providerType(ProviderType.OPENAI)
                .targetModel("gpt-5.4")
                .providerName("openai")
                .providerBaseUrl("http://provider.test")
                .providerTimeoutSeconds(30)
                .build();

        UnifiedStreamEvent textEvent = new UnifiedStreamEvent();
        textEvent.setType("text_delta");
        textEvent.setTextDelta("你好");

        Mockito.when(modelRouter.routeAll(Mockito.any())).thenReturn(List.of(routeResult));
        Mockito.when(providerClient.streamChat(Mockito.any()))
                .thenReturn(Flux.just(textEvent).concatWith(Flux.error(new RuntimeException("stream interrupted"))));

        Flux<ServerSentEvent<String>> flux = chatGatewayService.streamChat(request, protocolAdapter, new RequestStatsContext())
                .map(e -> (ServerSentEvent<String>) e);

        StepVerifier.create(flux)
                .assertNext(first -> assertTextChunk(first, "你好", "gpt-5.4"))
                .assertNext(event -> {
                    JsonNode jsonNode = parseJson(event.data());
                    assertEquals("stream interrupted", jsonNode.path("error").path("message").asText());
                    assertEquals("server_error", jsonNode.path("error").path("type").asText());
                })
                .assertNext(last -> assertEquals("[DONE]", last.data()))
                .verifyComplete();
    }

    private void assertTextChunk(ServerSentEvent<String> event, String expectedContent, String expectedModel) {
        JsonNode jsonNode = parseJson(event.data());
        assertEquals("chat.completion.chunk", jsonNode.path("object").asText());
        assertEquals(expectedModel, jsonNode.path("model").asText());
        assertNotNull(jsonNode.path("id").asText());
        assertTrue(jsonNode.path("created").asLong() > 0);
        assertEquals(0, jsonNode.path("choices").get(0).path("index").asInt());
        assertEquals("assistant", jsonNode.path("choices").get(0).path("delta").path("role").asText());
        assertEquals(expectedContent, jsonNode.path("choices").get(0).path("delta").path("content").asText());
        JsonNode finishReasonNode = jsonNode.path("choices").get(0).path("finish_reason");
        assertTrue(finishReasonNode.isNull() || finishReasonNode.isMissingNode());
    }

    private void assertDoneChunk(ServerSentEvent<String> event) {
        JsonNode jsonNode = parseJson(event.data());
        assertEquals("chat.completion.chunk", jsonNode.path("object").asText());
        assertEquals(0, jsonNode.path("choices").get(0).path("index").asInt());
        assertTrue(jsonNode.path("choices").get(0).path("delta").isObject());
        assertTrue(jsonNode.path("choices").get(0).path("delta").path("role").isMissingNode());
        assertTrue(jsonNode.path("choices").get(0).path("delta").path("content").isMissingNode());
        assertEquals("stop", jsonNode.path("choices").get(0).path("finish_reason").asText());
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new AssertionError("failed to parse sse json: " + json, e);
        }
    }

    private OpenAiChatCompletionRequest buildRequest() {
        OpenAiChatCompletionRequest request = new OpenAiChatCompletionRequest();
        request.setModel("gpt-5");
        request.setStream(true);

        OpenAiChatCompletionRequest.OpenAiMessage message = new OpenAiChatCompletionRequest.OpenAiMessage();
        message.setRole("user");
        message.setContent("你好");
        request.setMessages(List.of(message));
        return request;
    }
}
