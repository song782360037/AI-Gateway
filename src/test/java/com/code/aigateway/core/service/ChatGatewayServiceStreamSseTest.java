package com.code.aigateway.core.service;

import com.code.aigateway.api.request.OpenAiChatCompletionRequest;
import com.code.aigateway.core.capability.CapabilityChecker;
import com.code.aigateway.core.encoder.OpenAiChatResponseEncoder;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedStreamEvent;
import com.code.aigateway.core.parser.OpenAiChatRequestParser;
import com.code.aigateway.core.router.ModelRouter;
import com.code.aigateway.core.router.RouteResult;
import com.code.aigateway.provider.ProviderClient;
import com.code.aigateway.provider.ProviderClientFactory;
import com.code.aigateway.provider.ProviderType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatGatewayServiceStreamSseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OpenAiChatRequestParser requestParser;
    private ModelRouter modelRouter;
    private CapabilityChecker capabilityChecker;
    private ProviderClientFactory providerClientFactory;
    private OpenAiChatResponseEncoder responseEncoder;
    private ProviderClient providerClient;
    private ChatGatewayService chatGatewayService;

    @BeforeEach
    void setUp() {
        requestParser = Mockito.mock(OpenAiChatRequestParser.class);
        modelRouter = Mockito.mock(ModelRouter.class);
        capabilityChecker = Mockito.mock(CapabilityChecker.class);
        providerClientFactory = Mockito.mock(ProviderClientFactory.class);
        responseEncoder = Mockito.mock(OpenAiChatResponseEncoder.class);
        providerClient = Mockito.mock(ProviderClient.class);
        chatGatewayService = new ChatGatewayService(requestParser, modelRouter, capabilityChecker, providerClientFactory, responseEncoder, objectMapper);

        Mockito.when(providerClientFactory.getClient(ProviderType.OPENAI)).thenReturn(providerClient);
    }

    @Test
    void streamChat_textDeltaAndDone_encodeOpenAiChunkContract() {
        OpenAiChatCompletionRequest request = buildRequest();
        UnifiedRequest unifiedRequest = buildUnifiedRequest();
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

        Mockito.when(requestParser.parse(request)).thenReturn(unifiedRequest);
        Mockito.when(modelRouter.route(unifiedRequest)).thenReturn(routeResult);
        Mockito.when(providerClient.streamChat(unifiedRequest)).thenReturn(Flux.just(textEvent, doneEvent));

        StepVerifier.create(chatGatewayService.streamChat(request))
                .assertNext(first -> assertTextChunk(first, "你好", "gpt-5.4"))
                .assertNext(this::assertDoneChunk)
                .assertNext(last -> assertEquals("[DONE]", last.data()))
                .verifyComplete();
    }

    @Test
    void streamChat_doneWithoutFinishReason_defaultsToStopAndAppendsDoneMarker() {
        OpenAiChatCompletionRequest request = buildRequest();
        UnifiedRequest unifiedRequest = buildUnifiedRequest();
        RouteResult routeResult = RouteResult.builder()
                .providerType(ProviderType.OPENAI)
                .targetModel("gpt-5.4")
                .providerName("openai")
                .providerBaseUrl("http://provider.test")
                .providerTimeoutSeconds(30)
                .build();

        UnifiedStreamEvent doneEvent = new UnifiedStreamEvent();
        doneEvent.setType("done");

        Mockito.when(requestParser.parse(request)).thenReturn(unifiedRequest);
        Mockito.when(modelRouter.route(unifiedRequest)).thenReturn(routeResult);
        Mockito.when(providerClient.streamChat(unifiedRequest)).thenReturn(Flux.just(doneEvent));

        StepVerifier.create(chatGatewayService.streamChat(request))
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
        UnifiedRequest unifiedRequest = buildUnifiedRequest();
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

        Mockito.when(requestParser.parse(request)).thenReturn(unifiedRequest);
        Mockito.when(modelRouter.route(unifiedRequest)).thenReturn(routeResult);
        Mockito.when(providerClient.streamChat(unifiedRequest)).thenReturn(Flux.just(textEvent, doneEvent));

        StepVerifier.create(chatGatewayService.streamChat(request))
                .assertNext(event -> {
                    JsonNode jsonNode = parseJson(event.data());
                    assertEquals("包含\"引号\"、反斜杠\\以及\n换行", jsonNode.path("choices").get(0).path("delta").path("content").asText());
                })
                .thenConsumeWhile(sse -> true)
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

    private UnifiedRequest buildUnifiedRequest() {
        UnifiedRequest request = new UnifiedRequest();
        request.setModel("gpt-5.4");
        request.setProvider("openai");
        request.setStream(true);
        return request;
    }
}
