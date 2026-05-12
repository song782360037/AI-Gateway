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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Anthropic Messages 桥接层测试
 * <p>验证 SDK 适配器 → App 适配器的委托和 SSE 包装行为，不重复测试 SDK 内部逻辑。</p>
 */
class AnthropicProtocolAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AnthropicProtocolAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AnthropicProtocolAdapter(objectMapper,
                new com.code.aigateway.sdk.protocol.AnthropicProtocolAdapter(objectMapper));
    }

    @Test
    void getProtocolType_returnsAnthropic() {
        assertEquals(ProtocolType.ANTHROPIC, adapter.getProtocolType());
    }

    @Test
    void isSse_returnsTrue() {
        assertTrue(adapter.isSse());
    }

    @Test
    void encodeStreamEvent_returnsNamedSseEvents() {
        StreamContext ctx = new StreamContext("msg-123", 1710000000L, "claude-3-opus", objectMapper);
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("text_delta");
        event.setTextDelta("Bonjour");

        List<ServerSentEvent<String>> events = adapter.encodeStreamEvent(event, ctx).collectList().block();
        assertNotNull(events);
        assertFalse(events.isEmpty());

        boolean hasNamedEvent = events.stream().anyMatch(e -> e.event() != null);
        assertTrue(hasNamedEvent);
    }

    @Test
    void terminalStreamEvents_returnsMessageStop() {
        StreamContext ctx = new StreamContext("msg-123", 1710000000L, "claude-3-opus", objectMapper);
        Flux<ServerSentEvent<String>> flux = adapter.terminalStreamEvents(ctx);

        StepVerifier.create(flux)
                .expectNextMatches(sse -> "message_stop".equals(sse.event()))
                .verifyComplete();
    }

    @Test
    void encodeStreamError_producesSse() {
        StreamContext ctx = new StreamContext("msg-123", 1710000000L, "claude-3-opus", objectMapper);
        ServerSentEvent<String> sse = adapter.encodeStreamError(
                new RuntimeException("test error"), ctx).blockFirst();

        assertNotNull(sse);
        assertNotNull(sse.data());
        assertTrue(sse.data().contains("test error"));
    }
}
