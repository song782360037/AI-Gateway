package com.code.aigateway.core.router;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RouteCandidate.supportsProtocol 单元测试
 */
class RouteCandidateTest {

    @Test
    void supportsProtocol_nullRequest_returnsTrue() {
        RouteCandidate candidate = RouteCandidate.builder()
                .providerType("OPENAI").providerCode("test").targetModel("gpt-4o")
                .supportedProtocols(List.of("OPENAI_CHAT")).build();
        assertTrue(candidate.supportsProtocol(null));
    }

    @Test
    void supportsProtocol_emptySupportedProtocols_returnsTrue() {
        RouteCandidate candidate = RouteCandidate.builder()
                .providerType("OPENAI").providerCode("test").targetModel("gpt-4o")
                .supportedProtocols(null).build();
        assertTrue(candidate.supportsProtocol("OPENAI_CHAT"));
    }

    @Test
    void supportsProtocol_matchingProtocol_returnsTrue() {
        RouteCandidate candidate = RouteCandidate.builder()
                .providerType("OPENAI").providerCode("test").targetModel("gpt-4o")
                .supportedProtocols(List.of("OPENAI_CHAT", "ANTHROPIC")).build();
        assertTrue(candidate.supportsProtocol("OPENAI_CHAT"));
        assertTrue(candidate.supportsProtocol("ANTHROPIC"));
    }

    @Test
    void supportsProtocol_caseInsensitiveNormalization() {
        RouteCandidate candidate = RouteCandidate.builder()
                .providerType("OPENAI").providerCode("test").targetModel("gpt-4o")
                .supportedProtocols(List.of("openai-chat")).build();
        assertTrue(candidate.supportsProtocol("OPENAI_CHAT"));
    }

    @Test
    void supportsProtocol_nonMatchingProtocol_returnsFalse() {
        RouteCandidate candidate = RouteCandidate.builder()
                .providerType("OPENAI").providerCode("test").targetModel("gpt-4o")
                .supportedProtocols(List.of("OPENAI_CHAT")).build();
        assertFalse(candidate.supportsProtocol("ANTHROPIC"));
    }
}
