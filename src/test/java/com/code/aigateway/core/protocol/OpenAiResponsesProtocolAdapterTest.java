package com.code.aigateway.core.protocol;

import com.code.aigateway.api.request.OpenAiResponsesRequest;
import com.code.aigateway.api.response.OpenAiErrorResponse;
import com.code.aigateway.api.response.OpenAiResponsesResponse;
import com.code.aigateway.core.encoder.OpenAiResponsesResponseEncoder;
import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.model.ResponseProtocol;
import com.code.aigateway.core.model.StreamContext;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedResponse;
import com.code.aigateway.core.model.UnifiedStreamEvent;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void encodeStreamEvent_textDelta_returnsNamedEvent() throws Exception {
        // text_delta 应生成名为 "response.output_text.delta" 的 SSE 事件
        StreamContext ctx = new StreamContext("resp-123", 1710000000L, "gpt-4o");
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("text_delta");
        event.setTextDelta("Hi there");

        ServerSentEvent<String> sse = adapter.encodeStreamEvent(event, ctx).blockFirst();

        assertNotNull(sse);
        assertEquals("response.output_text.delta", sse.event());

        // 解析 payload 验证结构
        JsonNode payload = objectMapper.readTree(sse.data());
        assertEquals("response.output_text.delta", payload.get("type").asText());
        assertEquals("Hi there", payload.get("delta").asText());
    }

    @Test
    void encodeStreamEvent_toolCallDelta_returnsNamedEvent() throws Exception {
        // tool_call_delta 应生成名为 "response.function_call_arguments.delta" 的 SSE 事件
        StreamContext ctx = new StreamContext("resp-123", 1710000000L, "gpt-4o");
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("tool_call_delta");
        event.setArgumentsDelta("{\"city\":");

        ServerSentEvent<String> sse = adapter.encodeStreamEvent(event, ctx).blockFirst();

        assertNotNull(sse);
        assertEquals("response.function_call_arguments.delta", sse.event());

        JsonNode payload = objectMapper.readTree(sse.data());
        assertEquals("response.function_call_arguments.delta", payload.get("type").asText());
        assertEquals("{\"city\":", payload.get("delta").asText());
    }

    @Test
    void encodeStreamEvent_doneEvent_returnsCompleted() throws Exception {
        // done 事件应生成名为 "response.completed" 的 SSE 事件
        StreamContext ctx = new StreamContext("resp-123", 1710000000L, "gpt-4o");
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("done");
        event.setFinishReason("stop");

        ServerSentEvent<String> sse = adapter.encodeStreamEvent(event, ctx).blockFirst();

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

        assertNull(adapter.encodeStreamEvent(event, ctx).blockFirst());
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
}
