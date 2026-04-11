package com.code.aigateway.core.protocol;

import com.code.aigateway.api.request.OpenAiChatCompletionRequest;
import com.code.aigateway.api.response.OpenAiChatCompletionResponse;
import com.code.aigateway.api.response.OpenAiErrorResponse;
import com.code.aigateway.core.encoder.OpenAiChatResponseEncoder;
import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.model.ResponseProtocol;
import com.code.aigateway.core.model.StreamContext;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedResponse;
import com.code.aigateway.core.model.UnifiedStreamEvent;
import com.code.aigateway.core.model.UnifiedUsage;
import com.code.aigateway.core.parser.OpenAiChatRequestParser;
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
 * OpenAI Chat Completions 协议适配器单元测试
 */
@ExtendWith(MockitoExtension.class)
class OpenAiChatProtocolAdapterTest {

    @Mock
    private OpenAiChatRequestParser requestParser;

    @Mock
    private OpenAiChatResponseEncoder responseEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OpenAiChatProtocolAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OpenAiChatProtocolAdapter(requestParser, responseEncoder, objectMapper);
    }

    // ========== getProtocol ==========

    @Test
    void getProtocol_returnsOpenAiChat() {
        assertEquals(ResponseProtocol.OPENAI_CHAT, adapter.getProtocol());
    }

    // ========== parse ==========

    @Test
    void parse_delegatesToRequestParser() {
        // 准备 mock 输入和期望输出
        OpenAiChatCompletionRequest rawRequest = mock(OpenAiChatCompletionRequest.class);
        UnifiedRequest expected = new UnifiedRequest();
        when(requestParser.parse(rawRequest)).thenReturn(expected);

        UnifiedRequest result = adapter.parse(rawRequest);

        // 验证委托调用正确
        assertEquals(expected, result);
        verify(requestParser).parse(rawRequest);
    }

    // ========== encodeResponse ==========

    @Test
    void encodeResponse_delegatesToEncoder() {
        UnifiedResponse response = new UnifiedResponse();
        OpenAiChatCompletionResponse encoded = mock(OpenAiChatCompletionResponse.class);
        when(responseEncoder.encode(response)).thenReturn(encoded);

        Object result = adapter.encodeResponse(response);

        assertEquals(encoded, result);
        verify(responseEncoder).encode(response);
    }

    // ========== encodeStreamEvent ==========

    @Test
    void encodeStreamEvent_textDelta_firstChunkContainsRole() throws Exception {
        // 首个 text_delta chunk 应携带 role="assistant"
        StreamContext ctx = new StreamContext("chatcmpl-123", 1710000000L, "gpt-4o");
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("text_delta");
        event.setTextDelta("Hello");

        ServerSentEvent<String> sse = adapter.encodeStreamEvent(event, ctx).blockFirst();
        assertNotNull(sse);
        assertNotNull(sse.data());

        // 解析 SSE data JSON，验证结构
        JsonNode root = objectMapper.readTree(sse.data());
        assertEquals("chatcmpl-123", root.get("id").asText());
        assertEquals("chat.completion.chunk", root.get("object").asText());
        assertEquals("gpt-4o", root.get("model").asText());

        JsonNode delta = root.get("choices").get(0).get("delta");
        assertEquals("assistant", delta.get("role").asText());
        assertEquals("Hello", delta.get("content").asText());

        // finish_reason 在非 done chunk 中应为 null
        JsonNode finishReason = root.get("choices").get(0).get("finish_reason");
        assertNull(finishReason);
    }

    @Test
    void encodeStreamEvent_textDelta_subsequentChunksNoRole() throws Exception {
        // 先发送第一个 chunk 消费掉 firstContentSent 标记
        StreamContext ctx = new StreamContext("chatcmpl-123", 1710000000L, "gpt-4o");
        UnifiedStreamEvent firstEvent = new UnifiedStreamEvent();
        firstEvent.setType("text_delta");
        firstEvent.setTextDelta("Hello");
        adapter.encodeStreamEvent(firstEvent, ctx).blockFirst();

        // 第二个 chunk 不应包含 role
        UnifiedStreamEvent secondEvent = new UnifiedStreamEvent();
        secondEvent.setType("text_delta");
        secondEvent.setTextDelta(" world");

        ServerSentEvent<String> sse = adapter.encodeStreamEvent(secondEvent, ctx).blockFirst();
        JsonNode root = objectMapper.readTree(sse.data());
        JsonNode delta = root.get("choices").get(0).get("delta");

        // role 字段不应出现（@JsonInclude NON_NULL）
        assertNull(delta.get("role"));
        assertEquals(" world", delta.get("content").asText());
    }

    @Test
    void encodeStreamEvent_doneEvent_containsFinishReason() throws Exception {
        // done 事件应包含 finish_reason
        StreamContext ctx = new StreamContext("chatcmpl-123", 1710000000L, "gpt-4o");
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("done");
        event.setFinishReason("stop");

        ServerSentEvent<String> sse = adapter.encodeStreamEvent(event, ctx).blockFirst();
        assertNotNull(sse);

        JsonNode root = objectMapper.readTree(sse.data());
        assertEquals("stop", root.get("choices").get(0).get("finish_reason").asText());
    }

    @Test
    void encodeStreamError_returnsOpenAiErrorChunk() throws Exception {
        StreamContext ctx = new StreamContext("chatcmpl-123", 1710000000L, "gpt-4o");

        ServerSentEvent<String> sse = adapter.encodeStreamError(
                new RuntimeException("boom"), ctx).blockFirst();

        assertNotNull(sse);
        JsonNode root = objectMapper.readTree(sse.data());
        assertEquals("boom", root.get("error") .get("message").asText());
        assertEquals("server_error", root.get("error").get("type").asText());
        assertEquals(ErrorCode.INTERNAL_ERROR.name(), root.get("error").get("code").asText());
    }


    @Test
    void terminalStreamEvents_returnsDone() {
        // OpenAI Chat 以 [DONE] 终止流
        StreamContext ctx = new StreamContext("chatcmpl-123", 1710000000L, "gpt-4o");
        Flux<ServerSentEvent<String>> flux = adapter.terminalStreamEvents(ctx);

        StepVerifier.create(flux)
                .expectNextMatches(sse -> "[DONE]".equals(sse.data()))
                .verifyComplete();
    }

    // ========== isSse ==========

    @Test
    void isSse_returnsTrue() {
        assertTrue(adapter.isSse());
    }

    // ========== buildError ==========

    @Test
    void buildError_returnsOpenAiFormat() {
        Object result = adapter.buildError("Model not found", "invalid_request_error", "model_not_found", "model");

        // 验证返回类型和结构
        assertNotNull(result);
        assertTrue(result instanceof OpenAiErrorResponse);

        OpenAiErrorResponse error = (OpenAiErrorResponse) result;
        assertNotNull(error.getError());

        OpenAiErrorResponse.Error detail = error.getError();
        assertEquals("Model not found", detail.getMessage());
        assertEquals("invalid_request_error", detail.getType());
        assertEquals("model_not_found", detail.getCode());
        assertEquals("model", detail.getParam());
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
    void mapErrorType_providerError() {
        assertEquals("server_error", adapter.mapErrorType(ErrorCode.PROVIDER_ERROR));
    }

    @Test
    void mapErrorType_providerTimeout() {
        assertEquals("server_error", adapter.mapErrorType(ErrorCode.PROVIDER_TIMEOUT));
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
