package com.code.aigateway.core.protocol;

import com.code.aigateway.core.model.StreamContext;
import com.code.aigateway.sdk.model.ProtocolType;
import com.code.aigateway.sdk.model.UnifiedStreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gemini 桥接层测试
 * <p>验证 SDK 适配器 → App 适配器的委托和 SSE 包装行为，不重复测试 SDK 内部逻辑。</p>
 */
class GeminiProtocolAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private GeminiProtocolAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new GeminiProtocolAdapter(objectMapper,
                new com.code.aigateway.sdk.protocol.GeminiProtocolAdapter(objectMapper));
    }

    @Test
    void getProtocolType_returnsGemini() {
        assertEquals(ProtocolType.GEMINI, adapter.getProtocolType());
    }

    @Test
    void isSse_returnsFalse() {
        assertFalse(adapter.isSse());
    }

    @Test
    void encodeStreamEvent_returnsJsonChunk() {
        StreamContext ctx = new StreamContext("gemini-123", 1710000000L, "gemini-1.5-pro", objectMapper);
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("text_delta");
        event.setTextDelta("Hello Gemini");

        ServerSentEvent<String> sse = adapter.encodeStreamEvent(event, ctx).blockFirst();
        assertNotNull(sse);
        assertNotNull(sse.data());
        assertTrue(sse.data().contains("Hello Gemini"));
    }

    @Test
    void terminalStreamEvents_returnsEmpty() {
        StreamContext ctx = new StreamContext("gemini-123", 1710000000L, "gemini-1.5-pro", objectMapper);
        Flux<ServerSentEvent<String>> flux = adapter.terminalStreamEvents(ctx);
        StepVerifier.create(flux).verifyComplete();
    }

    @Test
    void encodeStreamError_producesSse() {
        StreamContext ctx = new StreamContext("gemini-123", 1710000000L, "gemini-1.5-pro", objectMapper);
        ServerSentEvent<String> sse = adapter.encodeStreamError(
                new RuntimeException("test error"), ctx).blockFirst();

        assertNotNull(sse);
        assertNotNull(sse.data());
        assertTrue(sse.data().contains("test error"));
    }
}
