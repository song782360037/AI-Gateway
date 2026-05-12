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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAI Chat 桥接层测试
 * <p>验证 SDK 适配器 → App 适配器的委托和 SSE 包装行为，不重复测试 SDK 内部逻辑。</p>
 */
class OpenAiChatProtocolAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OpenAiChatProtocolAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OpenAiChatProtocolAdapter(objectMapper,
                new com.code.aigateway.sdk.protocol.OpenAiChatProtocolAdapter(objectMapper));
    }

    @Test
    void getProtocolType_returnsOpenAiChat() {
        assertEquals(ProtocolType.OPENAI_CHAT, adapter.getProtocolType());
    }

    @Test
    void isSse_returnsTrue() {
        assertTrue(adapter.isSse());
    }

    @Test
    void encodeStreamEvent_returnsSseEvents() {
        StreamContext ctx = new StreamContext("chatcmpl-123", 1710000000L, "gpt-4o", objectMapper);
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("text_delta");
        event.setTextDelta("Hello");

        ServerSentEvent<String> sse = adapter.encodeStreamEvent(event, ctx).blockFirst();
        assertNotNull(sse);
        assertNotNull(sse.data());
        assertTrue(sse.data().contains("Hello"));
    }

    @Test
    void terminalStreamEvents_returnsDone() {
        StreamContext ctx = new StreamContext("chatcmpl-123", 1710000000L, "gpt-4o", objectMapper);
        Flux<ServerSentEvent<String>> flux = adapter.terminalStreamEvents(ctx);

        StepVerifier.create(flux)
                .expectNextMatches(sse -> "[DONE]".equals(sse.data()))
                .verifyComplete();
    }

    @Test
    void encodeStreamError_producesSse() {
        StreamContext ctx = new StreamContext("chatcmpl-123", 1710000000L, "gpt-4o", objectMapper);
        ServerSentEvent<String> sse = adapter.encodeStreamError(
                new RuntimeException("test error"), ctx).blockFirst();

        assertNotNull(sse);
        assertNotNull(sse.data());
        assertTrue(sse.data().contains("test error"));
    }
}
