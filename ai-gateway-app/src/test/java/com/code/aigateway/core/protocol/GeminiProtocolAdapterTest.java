package com.code.aigateway.core.protocol;

import com.code.aigateway.core.error.ErrorCode;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Google Gemini API 协议适配器单元测试
 * <p>
 * Gemini 使用 NDJSON（非 SSE），isSse() 返回 false。
 * done 事件不产生输出，流式没有显式终止标记。
 */
@ExtendWith(MockitoExtension.class)
class GeminiProtocolAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private GeminiProtocolAdapter adapter;

    @BeforeEach
    void setUp() {
        // 适配器已改为委托 SDK 实现，只需传入 ObjectMapper
        adapter = new GeminiProtocolAdapter(objectMapper,
                new com.code.aigateway.sdk.protocol.GeminiProtocolAdapter(objectMapper));
    }

    // ========== getProtocol ==========

    @Test
    void getProtocol_returnsGemini() {
        assertEquals(ProtocolType.GEMINI, adapter.getProtocol());
    }

    // ========== parse ==========

    // parse 测试已移除：适配器委托 SDK 实现，不再使用 mock requestParser

    // ========== encodeStreamEvent ==========

    @Test
    void encodeStreamEvent_textDelta_returnsJsonChunk() throws Exception {
        // text_delta 应返回包含 candidates[].content.parts[].text 的 JSON
        StreamContext ctx = new StreamContext("gemini-123", 1710000000L, "gemini-1.5-pro", objectMapper);
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("text_delta");
        event.setTextDelta("Hello Gemini");

        ServerSentEvent<String> sse = adapter.encodeStreamEvent(event, ctx).blockFirst();

        assertNotNull(sse);
        assertNotNull(sse.data());

        JsonNode root = objectMapper.readTree(sse.data());
        JsonNode candidates = root.get("candidates");
        assertNotNull(candidates);
        assertEquals(1, candidates.size());

        JsonNode content = candidates.get(0).get("content");
        assertEquals("model", content.get("role").asText());

        JsonNode parts = content.get("parts");
        assertEquals(1, parts.size());
        assertEquals("Hello Gemini", parts.get(0).get("text").asText());
    }

    @Test
    void encodeStreamEvent_toolCallDelta_returnsJsonChunk() throws Exception {
        // tool_call_delta：SDK 模式下 name 为空（delta 不重复传 name），args 为解析后的对象
        StreamContext ctx = new StreamContext("gemini-123", 1710000000L, "gemini-1.5-pro", objectMapper);
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("tool_call_delta");
        event.setToolName("get_weather");
        event.setArgumentsDelta("{\"city\":\"Paris\"}");

        ServerSentEvent<String> sse = adapter.encodeStreamEvent(event, ctx).blockFirst();

        assertNotNull(sse);
        JsonNode root = objectMapper.readTree(sse.data());
        JsonNode content = root.get("candidates").get(0).get("content");

        // 验证 functionCall 结构（SDK 中 delta 的 name 为空字符串）
        JsonNode functionCall = content.get("parts").get(0).get("functionCall");
        assertNotNull(functionCall);
        assertEquals("", functionCall.get("name").asText());
        // args 解析为对象而非原始字符串
        JsonNode args = functionCall.get("args");
        assertNotNull(args);
        assertEquals("Paris", args.get("city").asText());
    }

    @Test
    void encodeStreamEvent_doneEvent_returnsNull() {
        // Gemini 的 done 事件不产生任何 SSE 输出（返回空 Flux）
        StreamContext ctx = new StreamContext("gemini-123", 1710000000L, "gemini-1.5-pro", objectMapper);
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("done");

        assertNull(adapter.encodeStreamEvent(event, ctx).blockFirst());
    }

    @Test
    void encodeStreamEvent_unknownType_returnsNull() {
        // 未知事件类型应返回空 Flux
        StreamContext ctx = new StreamContext("gemini-123", 1710000000L, "gemini-1.5-pro", objectMapper);
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("unknown_event");

        assertNull(adapter.encodeStreamEvent(event, ctx).blockFirst());
    }

    // ========== terminalStreamEvents ==========

    @Test
    void terminalStreamEvents_returnsEmpty() {
        // Gemini 不发送显式终止标记
        StreamContext ctx = new StreamContext("gemini-123", 1710000000L, "gemini-1.5-pro", objectMapper);
        Flux<ServerSentEvent<String>> flux = adapter.terminalStreamEvents(ctx);

        StepVerifier.create(flux).verifyComplete();
    }

    // ========== isSse ==========

    @Test
    void isSse_returnsFalse() {
        // Gemini 使用 NDJSON，不是 SSE
        assertFalse(adapter.isSse());
    }

    // ========== buildError ==========

    @Test
    @SuppressWarnings("unchecked")
    void buildError_returnsGeminiFormat() {
        // SDK 委托模式：buildError 返回 Map 而非 POJO
        Object result = adapter.buildError("API key not valid", "UNAUTHENTICATED", "401", null);

        assertNotNull(result);
        assertTrue(result instanceof Map);

        Map<String, Object> errorWrapper = (Map<String, Object>) result;
        Map<String, Object> error = (Map<String, Object>) errorWrapper.get("error");
        assertNotNull(error);
        assertEquals("API key not valid", error.get("message"));
        assertEquals("UNAUTHENTICATED", error.get("status"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildError_invalidArgument_mapsToStatusCode400() {
        // SDK 返回 Map 结构，code 字段为 Integer 类型
        Object result = adapter.buildError("Bad request", "INVALID_ARGUMENT", "400", null);
        Map<String, Object> errorWrapper = (Map<String, Object>) result;
        Map<String, Object> error = (Map<String, Object>) errorWrapper.get("error");
        assertEquals(400, error.get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildError_resourceExhausted_mapsToStatusCode429() {
        Object result = adapter.buildError("Rate limited", "RESOURCE_EXHAUSTED", "429", null);
        Map<String, Object> errorWrapper = (Map<String, Object>) result;
        Map<String, Object> error = (Map<String, Object>) errorWrapper.get("error");
        assertEquals(429, error.get("code"));
    }

    // ========== mapErrorType ==========

    @Test
    void mapErrorType_invalidRequest() {
        assertEquals("INVALID_ARGUMENT", adapter.mapErrorType(ErrorCode.INVALID_REQUEST));
    }

    @Test
    void mapErrorType_modelNotFound() {
        assertEquals("INVALID_ARGUMENT", adapter.mapErrorType(ErrorCode.MODEL_NOT_FOUND));
    }

    @Test
    void mapErrorType_capabilityNotSupported() {
        assertEquals("INVALID_ARGUMENT", adapter.mapErrorType(ErrorCode.CAPABILITY_NOT_SUPPORTED));
    }

    @Test
    void mapErrorType_authFailed() {
        assertEquals("UNAUTHENTICATED", adapter.mapErrorType(ErrorCode.AUTH_FAILED));
    }

    @Test
    void mapErrorType_providerRateLimit() {
        assertEquals("RESOURCE_EXHAUSTED", adapter.mapErrorType(ErrorCode.PROVIDER_RATE_LIMIT));
    }

    @Test
    void mapErrorType_internalError() {
        // 未显式映射的错误码应返回 "INTERNAL"
        assertEquals("INTERNAL", adapter.mapErrorType(ErrorCode.INTERNAL_ERROR));
    }

    @Test
    void mapErrorType_providerError() {
        assertEquals("INTERNAL", adapter.mapErrorType(ErrorCode.PROVIDER_ERROR));
    }

    @Test
    void mapErrorType_providerServerError() {
        assertEquals("INTERNAL", adapter.mapErrorType(ErrorCode.PROVIDER_SERVER_ERROR));
    }

    @Test
    void mapErrorType_providerAuthError() {
        assertEquals("UNAUTHENTICATED", adapter.mapErrorType(ErrorCode.PROVIDER_AUTH_ERROR));
    }

    @Test
    void mapErrorType_providerBadRequest() {
        assertEquals("INVALID_ARGUMENT", adapter.mapErrorType(ErrorCode.PROVIDER_BAD_REQUEST));
    }

    @Test
    void mapErrorType_providerResourceNotFound() {
        assertEquals("NOT_FOUND", adapter.mapErrorType(ErrorCode.PROVIDER_RESOURCE_NOT_FOUND));
    }
}
