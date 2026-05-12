package com.code.aigateway.core.protocol;

import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.model.OpenAiResponsesStreamState;
import com.code.aigateway.sdk.model.ProtocolType;
import com.code.aigateway.core.model.StreamContext;
import com.code.aigateway.sdk.model.UnifiedStreamEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAI Responses API 协议适配器单元测试
 */
@ExtendWith(MockitoExtension.class)
class OpenAiResponsesProtocolAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OpenAiResponsesProtocolAdapter adapter;

    @BeforeEach
    void setUp() {
        // 适配器已改为委托 SDK 实现，只需传入 ObjectMapper
        adapter = new OpenAiResponsesProtocolAdapter(objectMapper,
                new com.code.aigateway.sdk.protocol.OpenAiResponsesProtocolAdapter(objectMapper));
    }

    // ========== getProtocol ==========

    @Test
    void getProtocol_returnsOpenAiResponses() {
        assertEquals(ProtocolType.OPENAI_RESPONSES, adapter.getProtocol());
    }

    // ========== parse ==========

    // parse 测试已移除：适配器委托 SDK 实现，不再使用 mock requestParser

    // ========== encodeStreamEvent ==========

    @Test
    void encodeStreamEvent_textDelta_withoutExplicitOutputIndex_stillUsesResponsesStateIndex() throws Exception {
        // SDK 使用内部 ResponsesStreamState 管理 output_index 和 item id
        StreamContext ctx = new StreamContext("resp-123", 1710000000L, "gpt-4o", objectMapper);
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("text_delta");
        event.setTextDelta("Hi there");

        List<ServerSentEvent<String>> events = adapter.encodeStreamEvent(event, ctx).collectList().block();

        assertNotNull(events);
        assertEquals(2, events.size());

        // 第一个事件：response.output_item.added（嵌套结构，item 字段在 item 对象内）
        JsonNode addedPayload = objectMapper.readTree(events.get(0).data());
        assertEquals(0, addedPayload.get("output_index").asInt());
        assertEquals("response.output_item.added", addedPayload.get("type").asText());
        // id 在嵌套的 item 对象内
        JsonNode item = addedPayload.get("item");
        assertNotNull(item);
        assertEquals("msg_resp-123", item.get("id").asText());
        assertEquals("assistant", item.get("role").asText());
        assertEquals("in_progress", item.get("status").asText());

        // 第二个事件：response.output_text.delta（delta 字段替代 text）
        JsonNode deltaPayload = objectMapper.readTree(events.get(1).data());
        assertEquals(0, deltaPayload.get("output_index").asInt());
        assertEquals("Hi there", deltaPayload.get("delta").asText());
    }

    @Test
    void streamContext_responsesState_isIsolatedFromCommonState() {
        StreamContext ctx = new StreamContext("resp-123", 1710000000L, "gpt-4o", objectMapper);

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
        // SDK 使用内部 ResponsesStreamState 管理，忽略事件中的 outputIndex/itemId
        StreamContext ctx = new StreamContext("resp-123", 1710000000L, "gpt-4o", objectMapper);
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("text_delta");
        event.setTextDelta("Hi there");
        event.setOutputIndex(2);
        event.setItemId("msg_001");

        // SDK 返回 2 个事件：added + delta
        List<ServerSentEvent<String>> events = adapter.encodeStreamEvent(event, ctx).collectList().block();
        assertNotNull(events);
        assertEquals(2, events.size());

        // 第一个事件：response.output_item.added
        ServerSentEvent<String> sse = events.get(0);
        assertEquals("response.output_item.added", sse.event());
        JsonNode payload = objectMapper.readTree(sse.data());
        assertEquals("response.output_item.added", payload.get("type").asText());
        // SDK 内部分配 index=0，基于 responseId 生成 id（在嵌套 item 内）
        assertEquals(0, payload.get("output_index").asInt());
        JsonNode item = payload.get("item");
        assertNotNull(item);
        assertEquals("msg_resp-123", item.get("id").asText());
    }

    @Test
    void encodeStreamEvent_toolCallDelta_includesIdentifiers() throws Exception {
        // 需要先发送 tool_call 事件让 SDK 内部分配 outputItemIndex
        StreamContext ctx = new StreamContext("resp-123", 1710000000L, "gpt-4o", objectMapper);

        // 先发送 tool_call 事件
        UnifiedStreamEvent toolCallEvent = new UnifiedStreamEvent();
        toolCallEvent.setType("tool_call");
        toolCallEvent.setToolCallId("call_fc_1");
        toolCallEvent.setToolName("get_weather");
        adapter.encodeStreamEvent(toolCallEvent, ctx).collectList().block();

        // 再发送 tool_call_delta
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
        // SDK 使用 lastOutputItemIndex（由 tool_call 事件分配的 index=0）
        assertEquals(0, payload.get("output_index").asInt());
        // SDK 使用 delta 字段
        assertEquals("{\"city\":", payload.get("delta").asText());
    }

    @Test
    void encodeStreamEvent_textDelta_emitsAddedAndDoneLifecycle() throws Exception {
        // SDK 模式：text_delta 先触发 output_item.added，再触发 output_text.delta
        StreamContext ctx = new StreamContext("resp-123", 1710000000L, "gpt-4o", objectMapper);

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
        // 嵌套结构：id 在 item 对象内
        JsonNode addedItem = added.get("item");
        assertNotNull(addedItem);
        assertEquals("msg_resp-123", addedItem.get("id").asText());
        assertEquals("response.output_item.added", added.get("type").asText());

        assertEquals("response.output_text.delta", emitted.get(1).event());
        JsonNode delta = objectMapper.readTree(emitted.get(1).data());
        assertEquals(0, delta.get("output_index").asInt());
        // delta 字段替代 text
        assertEquals("hello", delta.get("delta").asText());

        // done 事件：先关闭 text 块（output_item.done），再发送 response.completed
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
        StreamContext ctx = new StreamContext("resp-123", 1710000000L, "gpt-4o", objectMapper);
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
        StreamContext ctx = new StreamContext("resp-123", 1710000000L, "gpt-4o", objectMapper);
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("unknown_event_type");

        StepVerifier.create(adapter.encodeStreamEvent(event, ctx)).verifyComplete();
    }

    @Test
    void encodeStreamError_returnsNamedErrorEvent() throws Exception {
        // SDK encodeStreamError 返回名为 "error" 的命名事件
        StreamContext ctx = new StreamContext("resp-123", 1710000000L, "gpt-4o", objectMapper);

        ServerSentEvent<String> sse = adapter.encodeStreamError(
                new RuntimeException("boom"), ctx).blockFirst();

        assertNotNull(sse);
        // SDK 返回命名事件 "error"
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
        StreamContext ctx = new StreamContext("resp-123", 1710000000L, "gpt-4o", objectMapper);
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
    @SuppressWarnings("unchecked")
    void buildError_returnsOpenAiFormat() {
        // SDK 委托模式：buildError 返回 Map 而非 POJO
        Object result = adapter.buildError("Invalid request", "invalid_request_error", "bad_request", null);

        assertNotNull(result);
        assertTrue(result instanceof Map);

        Map<String, Object> errorWrapper = (Map<String, Object>) result;
        Map<String, Object> error = (Map<String, Object>) errorWrapper.get("error");
        assertEquals("Invalid request", error.get("message"));
        assertEquals("invalid_request_error", error.get("type"));
        assertEquals("bad_request", error.get("code"));
        assertNull(error.get("param"));
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
