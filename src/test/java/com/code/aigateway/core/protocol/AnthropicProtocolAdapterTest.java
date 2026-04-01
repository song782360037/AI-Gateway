package com.code.aigateway.core.protocol;

import com.code.aigateway.api.request.AnthropicMessagesRequest;
import com.code.aigateway.api.response.AnthropicErrorResponse;
import com.code.aigateway.core.encoder.AnthropicResponseEncoder;
import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.model.ResponseProtocol;
import com.code.aigateway.core.model.StreamContext;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedResponse;
import com.code.aigateway.core.model.UnifiedStreamEvent;
import com.code.aigateway.core.model.UnifiedUsage;
import com.code.aigateway.core.parser.AnthropicRequestParser;
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
 * Anthropic Messages API 协议适配器单元测试
 */
@ExtendWith(MockitoExtension.class)
class AnthropicProtocolAdapterTest {

    @Mock
    private AnthropicRequestParser requestParser;

    @Mock
    private AnthropicResponseEncoder responseEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AnthropicProtocolAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AnthropicProtocolAdapter(requestParser, responseEncoder, objectMapper);
    }

    // ========== getProtocol ==========

    @Test
    void getProtocol_returnsAnthropic() {
        assertEquals(ResponseProtocol.ANTHROPIC, adapter.getProtocol());
    }

    // ========== parse ==========

    @Test
    void parse_delegatesToRequestParser() {
        AnthropicMessagesRequest rawRequest = mock(AnthropicMessagesRequest.class);
        UnifiedRequest expected = new UnifiedRequest();
        when(requestParser.parse(rawRequest)).thenReturn(expected);

        UnifiedRequest result = adapter.parse(rawRequest);

        assertEquals(expected, result);
        verify(requestParser).parse(rawRequest);
    }

    // ========== encodeStreamEvent ==========

    @Test
    void encodeStreamEvent_textDelta_returnsContentBlockDelta() throws Exception {
        // text_delta 应生成名为 "content_block_delta" 的 SSE 事件，delta.type = "text_delta"
        StreamContext ctx = new StreamContext("msg-123", 1710000000L, "claude-3-opus");
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("text_delta");
        event.setTextDelta("Bonjour");

        ServerSentEvent<String> sse = adapter.encodeStreamEvent(event, ctx);

        assertNotNull(sse);
        assertEquals("content_block_delta", sse.event());

        JsonNode payload = objectMapper.readTree(sse.data());
        assertEquals("content_block_delta", payload.get("type").asText());
        assertEquals(0, payload.get("index").asInt());

        JsonNode delta = payload.get("delta");
        assertEquals("text_delta", delta.get("type").asText());
        assertEquals("Bonjour", delta.get("text").asText());
    }

    @Test
    void encodeStreamEvent_toolCallDelta_returnsContentBlockDelta() throws Exception {
        // tool_call_delta 应生成 content_block_delta，delta.type = "input_json_delta"
        StreamContext ctx = new StreamContext("msg-123", 1710000000L, "claude-3-opus");
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("tool_call_delta");
        event.setOutputIndex(1);
        event.setArgumentsDelta("{\"loc\":");

        ServerSentEvent<String> sse = adapter.encodeStreamEvent(event, ctx);

        assertNotNull(sse);
        assertEquals("content_block_delta", sse.event());

        JsonNode payload = objectMapper.readTree(sse.data());
        assertEquals(1, payload.get("index").asInt());

        JsonNode delta = payload.get("delta");
        assertEquals("input_json_delta", delta.get("type").asText());
        assertEquals("{\"loc\":", delta.get("partial_json").asText());
    }

    @Test
    void encodeStreamEvent_doneEvent_returnsMessageDelta() throws Exception {
        // done 事件应生成名为 "message_delta" 的 SSE 事件，包含 stop_reason
        StreamContext ctx = new StreamContext("msg-123", 1710000000L, "claude-3-opus");
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("done");
        event.setFinishReason("stop");

        ServerSentEvent<String> sse = adapter.encodeStreamEvent(event, ctx);

        assertNotNull(sse);
        assertEquals("message_delta", sse.event());

        JsonNode payload = objectMapper.readTree(sse.data());
        assertEquals("message_delta", payload.get("type").asText());

        JsonNode delta = payload.get("delta");
        // "stop" 映射为 Anthropic 的 "end_turn"
        assertEquals("end_turn", delta.get("stop_reason").asText());
    }

    @Test
    void encodeStreamEvent_doneEvent_withUsage() throws Exception {
        // done 事件携带 usage 时应包含 output_tokens
        StreamContext ctx = new StreamContext("msg-123", 1710000000L, "claude-3-opus");
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("done");
        event.setFinishReason("stop");
        UnifiedUsage usage = new UnifiedUsage();
        usage.setOutputTokens(42);
        event.setUsage(usage);

        ServerSentEvent<String> sse = adapter.encodeStreamEvent(event, ctx);

        assertNotNull(sse);
        JsonNode payload = objectMapper.readTree(sse.data());
        JsonNode delta = payload.get("delta");

        // 验证 usage 中包含 output_tokens
        JsonNode usageNode = delta.get("usage");
        assertNotNull(usageNode);
        assertEquals(42, usageNode.get("output_tokens").asInt());
    }

    // ========== terminalStreamEvents ==========

    @Test
    void terminalStreamEvents_returnsMessageStop() {
        // Anthropic 以 "message_stop" 事件终止流
        StreamContext ctx = new StreamContext("msg-123", 1710000000L, "claude-3-opus");
        Flux<ServerSentEvent<String>> flux = adapter.terminalStreamEvents(ctx);

        StepVerifier.create(flux)
                .expectNextMatches(sse -> {
                    if (!"message_stop".equals(sse.event())) return false;
                    try {
                        JsonNode payload = objectMapper.readTree(sse.data());
                        return "message_stop".equals(payload.get("type").asText());
                    } catch (Exception e) {
                        return false;
                    }
                })
                .verifyComplete();
    }

    // ========== isSse ==========

    @Test
    void isSse_returnsTrue() {
        assertTrue(adapter.isSse());
    }

    // ========== buildError ==========

    @Test
    void buildError_returnsAnthropicFormat() {
        Object result = adapter.buildError("Invalid API key", "authentication_error", "401", null);

        assertNotNull(result);
        assertTrue(result instanceof AnthropicErrorResponse);

        AnthropicErrorResponse error = (AnthropicErrorResponse) result;
        assertEquals("error", error.getType());
        assertNotNull(error.getError());
        assertEquals("authentication_error", error.getError().getType());
        assertEquals("Invalid API key", error.getError().getMessage());
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
        // 未显式映射的错误码应返回 "api_error"（Anthropic 默认）
        assertEquals("api_error", adapter.mapErrorType(ErrorCode.INTERNAL_ERROR));
    }

    @Test
    void mapErrorType_providerError() {
        assertEquals("api_error", adapter.mapErrorType(ErrorCode.PROVIDER_ERROR));
    }

    @Test
    void mapErrorType_providerTimeout() {
        assertEquals("api_error", adapter.mapErrorType(ErrorCode.PROVIDER_TIMEOUT));
    }
}
