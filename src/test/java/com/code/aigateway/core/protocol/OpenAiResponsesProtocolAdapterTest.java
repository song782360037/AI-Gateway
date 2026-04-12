package com.code.aigateway.core.protocol;

import com.code.aigateway.api.request.OpenAiResponsesRequest;
import com.code.aigateway.api.response.OpenAiErrorResponse;
import com.code.aigateway.api.response.OpenAiResponsesResponse;
import com.code.aigateway.core.encoder.OpenAiResponsesResponseEncoder;
import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.model.OpenAiResponsesStreamState;
import com.code.aigateway.core.model.ResponseProtocol;
import com.code.aigateway.core.model.StreamContext;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedResponse;
import com.code.aigateway.core.model.UnifiedStreamEvent;
import com.code.aigateway.core.model.UnifiedPart;
import com.code.aigateway.core.parser.OpenAiResponsesRequestParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OpenAI Responses API 协议适配器单元测试
 */
@ExtendWith(MockitoExtension.class)
class OpenAiResponsesProtocolAdapterTest {

    @Mock
    private OpenAiResponsesRequestParser requestParser;

    @Mock
    private OpenAiResponsesResponseEncoder responseEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OpenAiResponsesProtocolAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OpenAiResponsesProtocolAdapter(requestParser, responseEncoder, objectMapper);
    }

    // ========== getProtocol ==========

    @Test
    void getProtocol_returnsOpenAiResponses() {
        assertEquals(ResponseProtocol.OPENAI_RESPONSES, adapter.getProtocol());
    }

    // ========== parse ==========

    @Test
    void parse_delegatesToRequestParser() {
        OpenAiResponsesRequest rawRequest = mock(OpenAiResponsesRequest.class);
        UnifiedRequest expected = new UnifiedRequest();
        when(requestParser.parse(rawRequest)).thenReturn(expected);

        UnifiedRequest result = adapter.parse(rawRequest);

        assertEquals(expected, result);
        verify(requestParser).parse(rawRequest);
    }

    // ========== encodeStreamEvent ==========

    @Test
    void encodeStreamEvent_textDelta_withoutExplicitOutputIndex_stillUsesResponsesStateIndex() throws Exception {
        StreamContext ctx = new StreamContext("resp-123", 1710000000L, "gpt-4o");
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("text_delta");
        event.setTextDelta("Hi there");

        List<ServerSentEvent<String>> events = adapter.encodeStreamEvent(event, ctx).collectList().block();

        assertNotNull(events);
        assertEquals(2, events.size());
        JsonNode addedPayload = objectMapper.readTree(events.get(0).data());
        JsonNode deltaPayload = objectMapper.readTree(events.get(1).data());
        assertEquals(0, addedPayload.get("output_index").asInt());
        assertEquals(0, deltaPayload.get("output_index").asInt());
        assertEquals("msg_resp-123_0", addedPayload.get("item").get("id").asText());
        assertEquals("msg_resp-123_0", deltaPayload.get("item_id").asText());
    }

    @Test
    void streamContext_responsesState_isIsolatedFromCommonState() {
        StreamContext ctx = new StreamContext("resp-123", 1710000000L, "gpt-4o");

        int contentBlockIndex = ctx.allocateAndOpenContentBlock("text");
        OpenAiResponsesStreamState responsesState = ctx.responses();
        int outputItemIndex = responsesState.nextOutputItemIndex();
        responsesState.tryOpenTextBlock();
        responsesState.setTextOutputItemIndex(outputItemIndex);
        responsesState.setTextItemId("msg_001");

        assertEquals(0, contentBlockIndex);
        assertEquals(0, ctx.getOpenBlockIndex());
        assertEquals("text", ctx.getOpenBlockType());
        assertTrue(ctx.hasOpenContentBlock());
        assertEquals(0, outputItemIndex);
        assertEquals(0, responsesState.getTextOutputItemIndex());
        assertEquals("msg_001", responsesState.getTextItemId());

        responsesState.closeTextBlock();

        assertEquals(0, ctx.getOpenBlockIndex());
        assertEquals("text", ctx.getOpenBlockType());
        assertTrue(ctx.hasOpenContentBlock());
        assertEquals(-1, responsesState.getTextOutputItemIndex());
        assertNull(responsesState.getTextItemId());
    }

    @Test
    void encodeStreamEvent_textDelta_includesOutputIndexAndItemId() throws Exception {
        StreamContext ctx = new StreamContext("resp-123", 1710000000L, "gpt-4o");
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("text_delta");
        event.setTextDelta("Hi there");
        event.setOutputIndex(2);
        event.setItemId("msg_001");

        ServerSentEvent<String> sse = adapter.encodeStreamEvent(event, ctx).blockFirst();

        assertNotNull(sse);
        assertEquals("response.output_item.added", sse.event());
        JsonNode payload = objectMapper.readTree(sse.data());
        assertEquals("response.output_item.added", payload.get("type").asText());
        assertEquals(2, payload.get("output_index").asInt());
        assertEquals("msg_001", payload.get("item").get("id").asText());
        assertEquals("message", payload.get("item").get("type").asText());
    }

    @Test
    void encodeStreamEvent_toolCallDelta_includesIdentifiers() throws Exception {
        StreamContext ctx = new StreamContext("resp-123", 1710000000L, "gpt-4o");
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("tool_call_delta");
        event.setArgumentsDelta("{\"city\":");
        event.setOutputIndex(1);
        event.setItemId("item_fc_1");
        event.setToolCallId("call_fc_1");

        ServerSentEvent<String> sse = adapter.encodeStreamEvent(event, ctx).blockFirst();

        assertNotNull(sse);
        assertEquals("response.function_call_arguments.delta", sse.event());

        JsonNode payload = objectMapper.readTree(sse.data());
        assertEquals("response.function_call_arguments.delta", payload.get("type").asText());
        assertEquals(1, payload.get("output_index").asInt());
        assertEquals("item_fc_1", payload.get("item_id").asText());
        assertEquals("call_fc_1", payload.get("call_id").asText());
        assertEquals("{\"city\":", payload.get("delta").asText());
    }

    @Test
    void encodeStreamEvent_textDelta_emitsAddedAndDoneLifecycle() throws Exception {
        StreamContext ctx = new StreamContext("resp-123", 1710000000L, "gpt-4o");

        UnifiedStreamEvent textEvent = new UnifiedStreamEvent();
        textEvent.setType("text_delta");
        textEvent.setTextDelta("hello");
        textEvent.setOutputIndex(0);
        textEvent.setItemId("msg_001");

        var emitted = adapter.encodeStreamEvent(textEvent, ctx).collectList().block();
        assertNotNull(emitted);
        assertEquals(2, emitted.size());
        assertEquals("response.output_item.added", emitted.get(0).event());
        JsonNode added = objectMapper.readTree(emitted.get(0).data());
        assertEquals(0, added.get("output_index").asInt());
        assertEquals("message", added.get("item").get("type").asText());
        assertEquals("msg_001", added.get("item").get("id").asText());

        assertEquals("response.output_text.delta", emitted.get(1).event());
        JsonNode delta = objectMapper.readTree(emitted.get(1).data());
        assertEquals(0, delta.get("output_index").asInt());
        assertEquals("msg_001", delta.get("item_id").asText());
        assertEquals("hello", delta.get("delta").asText());

        UnifiedStreamEvent doneEvent = new UnifiedStreamEvent();
        doneEvent.setType("done");
        doneEvent.setFinishReason("stop");
        var doneChunks = adapter.encodeStreamEvent(doneEvent, ctx).collectList().block();
        assertNotNull(doneChunks);
        assertEquals(2, doneChunks.size());
        assertEquals("response.output_item.done", doneChunks.get(0).event());
        assertEquals("response.completed", doneChunks.get(1).event());
    }


    @Test
    void encodeStreamEvent_doneEvent_returnsCompleted() throws Exception {
        // done 事件应生成名为 "response.completed" 的 SSE 事件
        StreamContext ctx = new StreamContext("resp-123", 1710000000L, "gpt-4o");
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("done");
        event.setFinishReason("stop");

        ServerSentEvent<String> sse = adapter.encodeStreamEvent(event, ctx).blockLast();

        assertNotNull(sse);
        assertEquals("response.completed", sse.event());

        JsonNode payload = objectMapper.readTree(sse.data());
        assertEquals("response.completed", payload.get("type").asText());

        JsonNode responseNode = payload.get("response");
        assertEquals("resp-123", responseNode.get("id").asText());
        assertEquals("response", responseNode.get("object").asText());
        assertEquals("completed", responseNode.get("status").asText());
        assertEquals("gpt-4o", responseNode.get("model").asText());
    }

    @Test
    void encodeStreamEvent_unknownType_returnsNull() {
        // 未知事件类型应返回空 Flux，由调用方跳过
        StreamContext ctx = new StreamContext("resp-123", 1710000000L, "gpt-4o");
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("unknown_event_type");

        StepVerifier.create(adapter.encodeStreamEvent(event, ctx)).verifyComplete();
    }

    @Test
    void parse_invalidReasoningEffort_throwsInvalidRequest() {
        OpenAiResponsesRequest request = new OpenAiResponsesRequest();
        request.setModel("gpt-4o");
        request.setInput(List.of());
        request.setReasoning(Map.of("effort", "extreme"));

        OpenAiResponsesRequestParser realParser = new OpenAiResponsesRequestParser();

        GatewayException exception = assertThrows(GatewayException.class, () -> realParser.parse(request));
        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
        assertEquals("reasoning.effort", exception.getParam());
    }

    @Test
    void parse_arrayContentWithTextItems_returnsUnifiedMessageParts() {
        OpenAiResponsesRequest.InputItem item = new OpenAiResponsesRequest.InputItem();
        item.setType("message");
        item.setRole("user");
        item.setContent(List.of(
                Map.of("type", "input_text", "text", "你好"),
                Map.of("type", "text", "text", "世界")
        ));

        OpenAiResponsesRequest request = new OpenAiResponsesRequest();
        request.setModel("gpt-4o");
        request.setInput(List.of(item));

        OpenAiResponsesRequestParser realParser = new OpenAiResponsesRequestParser();
        UnifiedRequest unifiedRequest = realParser.parse(request);

        assertEquals(1, unifiedRequest.getMessages().size());
        assertEquals(2, unifiedRequest.getMessages().getFirst().getParts().size());
        assertEquals("你好", unifiedRequest.getMessages().getFirst().getParts().get(0).getText());
        assertEquals("世界", unifiedRequest.getMessages().getFirst().getParts().get(1).getText());
    }

    @Test
    void parse_inputImageContent_mapsToUnifiedImage() {
        OpenAiResponsesRequest.InputItem item = new OpenAiResponsesRequest.InputItem();
        item.setType("message");
        item.setRole("user");
        item.setContent(List.of(
                Map.of("type", "input_text", "text", "描述图片"),
                Map.of("type", "input_image", "image_url", "https://example.com/test.png", "detail", "high")
        ));

        OpenAiResponsesRequest request = new OpenAiResponsesRequest();
        request.setModel("gpt-4o");
        request.setInput(List.of(item));

        OpenAiResponsesRequestParser realParser = new OpenAiResponsesRequestParser();
        UnifiedRequest unifiedRequest = realParser.parse(request);

        assertEquals(1, unifiedRequest.getMessages().size());
        List<UnifiedPart> parts = unifiedRequest.getMessages().getFirst().getParts();
        assertEquals(2, parts.size());
        assertEquals("text", parts.get(0).getType());
        assertEquals("描述图片", parts.get(0).getText());
        assertEquals("image", parts.get(1).getType());
        assertEquals("https://example.com/test.png", parts.get(1).getUrl());
        assertEquals("high", parts.get(1).getAttributes().get("detail"));
    }

    @Test
    void parse_inputImageBase64Content_mapsToUnifiedImage() {
        OpenAiResponsesRequest.InputItem item = new OpenAiResponsesRequest.InputItem();
        item.setType("message");
        item.setRole("user");
        item.setContent(List.of(
                Map.of("type", "input_image", "image_url", "data:image/png;base64,QUJDRA==")
        ));

        OpenAiResponsesRequest request = new OpenAiResponsesRequest();
        request.setModel("gpt-4o");
        request.setInput(List.of(item));

        OpenAiResponsesRequestParser realParser = new OpenAiResponsesRequestParser();
        UnifiedRequest unifiedRequest = realParser.parse(request);

        List<UnifiedPart> parts = unifiedRequest.getMessages().getFirst().getParts();
        assertEquals(1, parts.size());
        assertEquals("image", parts.get(0).getType());
        assertEquals("image/png", parts.get(0).getMimeType());
        assertEquals("QUJDRA==", parts.get(0).getBase64Data());
    }

    @Test
    void parse_arrayContentWithUnsupportedType_throwsInvalidRequest() {
        OpenAiResponsesRequest.InputItem item = new OpenAiResponsesRequest.InputItem();
        item.setType("message");
        item.setRole("user");
        item.setContent(List.of(Map.of("type", "input_file", "file_data", "dGVzdA==")));

        OpenAiResponsesRequest request = new OpenAiResponsesRequest();
        request.setModel("gpt-4o");
        request.setInput(List.of(item));

        OpenAiResponsesRequestParser realParser = new OpenAiResponsesRequestParser();

        GatewayException exception = assertThrows(GatewayException.class, () -> realParser.parse(request));
        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
        assertEquals("input[0][0].type", exception.getParam());
    }

    @Test
    void encodeStreamError_returnsNamedErrorEvent() throws Exception {
        StreamContext ctx = new StreamContext("resp-123", 1710000000L, "gpt-4o");

        ServerSentEvent<String> sse = adapter.encodeStreamError(
                new RuntimeException("boom"), ctx).blockFirst();

        assertNotNull(sse);
        assertEquals("error", sse.event());
        JsonNode root = objectMapper.readTree(sse.data());
        assertEquals("boom", root.get("error").get("message").asText());
        assertEquals("server_error", root.get("error").get("type").asText());
        assertEquals(ErrorCode.INTERNAL_ERROR.name(), root.get("error").get("code").asText());
    }

    // ========== terminalStreamEvents ==========

    @Test
    void terminalStreamEvents_returnsEmpty() {
        // Responses API 不像 Chat Completions 有 [DONE] 标记
        StreamContext ctx = new StreamContext("resp-123", 1710000000L, "gpt-4o");
        Flux<ServerSentEvent<String>> flux = adapter.terminalStreamEvents(ctx);

        StepVerifier.create(flux).verifyComplete();
    }

    // ========== isSse ==========

    @Test
    void isSse_returnsTrue() {
        assertTrue(adapter.isSse());
    }

    // ========== buildError ==========

    @Test
    void buildError_returnsOpenAiFormat() {
        // Responses API 复用 OpenAI 标准错误格式
        Object result = adapter.buildError("Invalid request", "invalid_request_error", "bad_request", null);

        assertNotNull(result);
        assertTrue(result instanceof OpenAiErrorResponse);

        OpenAiErrorResponse error = (OpenAiErrorResponse) result;
        assertEquals("Invalid request", error.getError().getMessage());
        assertEquals("invalid_request_error", error.getError().getType());
        assertEquals("bad_request", error.getError().getCode());
        assertNull(error.getError().getParam());
    }

    // ========== mapErrorType ==========

    @Test
    void mapErrorType_invalidRequest() {
        assertEquals("invalid_request_error", adapter.mapErrorType(ErrorCode.INVALID_REQUEST));
    }

    @Test
    void mapErrorType_modelNotFound() {
        assertEquals("invalid_request_error", adapter.mapErrorType(ErrorCode.MODEL_NOT_FOUND));
    }

    @Test
    void mapErrorType_capabilityNotSupported() {
        assertEquals("invalid_request_error", adapter.mapErrorType(ErrorCode.CAPABILITY_NOT_SUPPORTED));
    }

    @Test
    void mapErrorType_authFailed() {
        assertEquals("authentication_error", adapter.mapErrorType(ErrorCode.AUTH_FAILED));
    }

    @Test
    void mapErrorType_providerRateLimit() {
        assertEquals("rate_limit_error", adapter.mapErrorType(ErrorCode.PROVIDER_RATE_LIMIT));
    }

    @Test
    void mapErrorType_internalError() {
        // 未显式映射的错误码应返回 "server_error"
        assertEquals("server_error", adapter.mapErrorType(ErrorCode.INTERNAL_ERROR));
    }

    @Test
    void mapErrorType_providerServerError() {
        assertEquals("server_error", adapter.mapErrorType(ErrorCode.PROVIDER_SERVER_ERROR));
    }

    @Test
    void mapErrorType_providerAuthError() {
        assertEquals("authentication_error", adapter.mapErrorType(ErrorCode.PROVIDER_AUTH_ERROR));
    }

    @Test
    void mapErrorType_providerBadRequest() {
        assertEquals("invalid_request_error", adapter.mapErrorType(ErrorCode.PROVIDER_BAD_REQUEST));
    }

    @Test
    void mapErrorType_providerResourceNotFound() {
        assertEquals("invalid_request_error", adapter.mapErrorType(ErrorCode.PROVIDER_RESOURCE_NOT_FOUND));
    }
}
