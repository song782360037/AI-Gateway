package com.code.aigateway.core.router;

import com.code.aigateway.sdk.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.sdk.model.UnifiedGenerationConfig;
import com.code.aigateway.sdk.model.UnifiedMessage;
import com.code.aigateway.sdk.model.UnifiedPart;
import com.code.aigateway.sdk.model.UnifiedRequest;
import com.code.aigateway.sdk.model.UnifiedResponseFormat;
import com.code.aigateway.sdk.model.UnifiedTool;
import com.code.aigateway.sdk.model.UnifiedToolChoice;
import com.code.aigateway.core.router.auto.AutoRouteRequestClassifier;
import com.code.aigateway.core.router.auto.AutoRouteScorer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AutoRouteSelector 单元测试
 */
class AutoRouteSelectorTest {

    private ProviderKeySelector providerKeySelector;
    private AutoRouteSelector selector;

    @BeforeEach
    void setUp() {
        providerKeySelector = mock(ProviderKeySelector.class);
        // 默认 mock 行为：返回一个固定的 ProviderKeyEntry
        when(providerKeySelector.select(any(), any(), any()))
                .thenReturn(new ProviderKeyEntry(1L, "sk-test-key", "sk-test****key", 100, 0));
        selector = new AutoRouteSelector(
                new AutoRouteRequestClassifier(),
                new AutoRouteScorer(),
                providerKeySelector);
    }

    // ==================== isAutoModel ====================


    @Test
    void isAutoModel_exactMatch() {
        assertTrue(selector.isAutoModel("auto"));
    }

    @Test
    void isAutoModel_caseInsensitive() {
        assertTrue(selector.isAutoModel("Auto"));
        assertTrue(selector.isAutoModel("AUTO"));
    }

    @Test
    void isAutoModel_withPrefix() {
        assertTrue(selector.isAutoModel("auto:coding"));
        assertTrue(selector.isAutoModel("Auto:Coding"));
    }

    @Test
    void isAutoModel_normalModel_returnsFalse() {
        assertFalse(selector.isAutoModel("gpt-4o"));
        assertFalse(selector.isAutoModel("auto-route"));
    }

    @Test
    void isAutoModel_null_returnsFalse() {
        assertFalse(selector.isAutoModel(null));
    }

    // ==================== selectAll ====================

    @Test
    void selectAll_autoDefault_resolvesCorrectly() {
        RouteCandidate candidate = RouteCandidate.builder()
                .providerType("OPENAI").providerCode("openai-main").targetModel("gpt-4o")
                .providerPriority(10).build();
        RoutingConfigSnapshot snapshot = buildSnapshot("default", candidate);

        UnifiedRequest request = buildRequest("auto", null);
        List<RouteResult> results = selector.selectAll(snapshot, request);

        assertEquals(1, results.size());
        assertEquals("openai-main", results.get(0).getProviderName());
        assertEquals("gpt-4o", results.get(0).getTargetModel());
    }

    @Test
    void selectAll_autoWithKey_resolvesCorrectly() {
        RouteCandidate candidate = RouteCandidate.builder()
                .providerType("ANTHROPIC").providerCode("anthropic-main").targetModel("claude-sonnet-4-20250514")
                .providerPriority(5).build();
        RoutingConfigSnapshot snapshot = buildSnapshot("coding", candidate);

        UnifiedRequest request = buildRequest("auto:coding", null);
        List<RouteResult> results = selector.selectAll(snapshot, request);

        assertEquals(1, results.size());
        assertEquals("anthropic-main", results.get(0).getProviderName());
    }

    @Test
    void selectAll_noRouteEntry_throwsModelNotFound() {
        RoutingConfigSnapshot snapshot = buildEmptySnapshot();

        UnifiedRequest request = buildRequest("auto:unknown", null);
        GatewayException ex = assertThrows(GatewayException.class,
                () -> selector.selectAll(snapshot, request));
        assertEquals(ErrorCode.MODEL_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void selectAll_noProtocolMatch_throwsProviderNotFound() {
        RouteCandidate candidate = RouteCandidate.builder()
                .providerType("OPENAI").providerCode("openai-main").targetModel("gpt-4o")
                .providerPriority(10)
                .supportedProtocols(List.of("OPENAI_CHAT")).build();
        RoutingConfigSnapshot snapshot = buildSnapshot("default", candidate);

        UnifiedRequest request = buildRequest("auto", "ANTHROPIC");
        GatewayException ex = assertThrows(GatewayException.class,
                () -> selector.selectAll(snapshot, request));
        assertEquals(ErrorCode.PROVIDER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void selectAll_emptyRouteKey_throwsInvalidRequest() {
        UnifiedRequest request = buildRequest("auto:", null);
        assertThrows(GatewayException.class,
                () -> selector.selectAll(buildEmptySnapshot(), request));
    }

    @Test
    void selectAll_protocolFilter_worksCorrectly() {
        RouteCandidate openai = RouteCandidate.builder()
                .providerType("OPENAI").providerCode("openai").targetModel("gpt-4o")
                .providerPriority(10)
                .supportedProtocols(List.of("OPENAI_CHAT")).build();
        RouteCandidate anthropic = RouteCandidate.builder()
                .providerType("ANTHROPIC").providerCode("anthropic").targetModel("claude-sonnet-4-20250514")
                .providerPriority(5)
                .supportedProtocols(List.of("ANTHROPIC")).build();
        RoutingConfigSnapshot snapshot = buildSnapshot("default", openai, anthropic);

        UnifiedRequest request = buildRequest("auto", "ANTHROPIC");
        List<RouteResult> results = selector.selectAll(snapshot, request);
        assertEquals(1, results.size());
        assertEquals("anthropic", results.get(0).getProviderName());
    }

    @Test
    void selectAll_visionRequest_filtersUnsupportedCandidates() {
        RouteCandidate textOnly = baseCandidate("text-only")
                .supportsVision(false)
                .qualityScore(90)
                .build();
        RouteCandidate vision = baseCandidate("vision")
                .supportsVision(true)
                .qualityScore(50)
                .build();
        RoutingConfigSnapshot snapshot = buildSnapshot("default", textOnly, vision);

        List<RouteResult> results = selector.selectAll(snapshot, buildVisionRequest());

        assertEquals(1, results.size());
        assertEquals("vision", results.get(0).getProviderName());
    }

    @Test
    void selectAll_toolRequiredRequest_filtersUnsupportedCandidates() {
        RouteCandidate autoToolOnly = baseCandidate("auto-tool")
                .supportsTools(true)
                .supportsToolChoiceRequired(false)
                .build();
        RouteCandidate requiredTool = baseCandidate("required-tool")
                .supportsTools(true)
                .supportsToolChoiceRequired(true)
                .build();
        RoutingConfigSnapshot snapshot = buildSnapshot("default", autoToolOnly, requiredTool);

        List<RouteResult> results = selector.selectAll(snapshot, buildRequiredToolRequest());

        assertEquals(1, results.size());
        assertEquals("required-tool", results.get(0).getProviderName());
    }

    @Test
    void selectAll_jsonSchemaRequest_prefersJsonCandidate() {
        RouteCandidate text = baseCandidate("text")
                .supportsJson(false)
                .qualityScore(100)
                .build();
        RouteCandidate json = baseCandidate("json")
                .supportsJson(true)
                .qualityScore(50)
                .build();
        RoutingConfigSnapshot snapshot = buildSnapshot("default", text, json);

        List<RouteResult> results = selector.selectAll(snapshot, buildJsonRequest());

        assertEquals(1, results.size());
        assertEquals("json", results.get(0).getProviderName());
    }

    @Test
    void selectAll_longContextRequest_filtersSmallContextCandidate() {
        RouteCandidate small = baseCandidate("small")
                .maxInputTokens(100)
                .build();
        RouteCandidate large = baseCandidate("large")
                .maxInputTokens(2000)
                .build();
        RoutingConfigSnapshot snapshot = buildSnapshot("default", small, large);

        UnifiedRequest request = buildTextRequest("auto", "请总结：" + "内容".repeat(300));
        List<RouteResult> results = selector.selectAll(snapshot, request);

        assertEquals(1, results.size());
        assertEquals("large", results.get(0).getProviderName());
    }

    @Test
    void selectAll_fastIntent_prefersLatencyScore() {
        RouteCandidate quality = baseCandidate("quality")
                .qualityScore(100)
                .latencyScore(10)
                .build();
        RouteCandidate fast = baseCandidate("fast")
                .qualityScore(50)
                .latencyScore(100)
                .build();
        RoutingConfigSnapshot snapshot = buildSnapshot("default", quality, fast);

        UnifiedRequest request = buildRequest("auto", null);
        request.setMetadata(Map.of("intent", "fast"));
        List<RouteResult> results = selector.selectAll(snapshot, request);

        assertEquals("fast", results.get(0).getProviderName());
    }

    @Test
    void selectAll_cheapIntent_prefersCostScore() {
        RouteCandidate quality = baseCandidate("quality")
                .qualityScore(100)
                .costScore(10)
                .build();
        RouteCandidate cheap = baseCandidate("cheap")
                .qualityScore(50)
                .costScore(100)
                .build();
        RoutingConfigSnapshot snapshot = buildSnapshot("default", quality, cheap);

        UnifiedRequest request = buildRequest("auto", null);
        request.setMetadata(Map.of("intent", "cheap"));
        List<RouteResult> results = selector.selectAll(snapshot, request);

        assertEquals("cheap", results.get(0).getProviderName());
    }

    @Test
    void selectAll_priorityDoesNotOverrideTaskFit() {
        RouteCandidate highPriorityText = baseCandidate("high-priority-text")
                .supportsVision(false)
                .providerPriority(9999)
                .build();
        RouteCandidate lowPriorityVision = baseCandidate("low-priority-vision")
                .supportsVision(true)
                .providerPriority(0)
                .build();
        RoutingConfigSnapshot snapshot = buildSnapshot("default", highPriorityText, lowPriorityVision);

        List<RouteResult> results = selector.selectAll(snapshot, buildVisionRequest());

        assertEquals(1, results.size());
        assertEquals("low-priority-vision", results.get(0).getProviderName());
    }

    @Test
    void selectAll_keepsMultipleRankedCandidatesForFailover() {
        RouteCandidate first = baseCandidate("first").qualityScore(80).build();
        RouteCandidate second = baseCandidate("second").qualityScore(70).build();
        RoutingConfigSnapshot snapshot = buildSnapshot("default", second, first);

        List<RouteResult> results = selector.selectAll(snapshot, buildRequest("auto", null));

        assertEquals(2, results.size());
        assertEquals("first", results.get(0).getProviderName());
        assertEquals("second", results.get(1).getProviderName());
    }

    // ==================== 辅助方法 ====================

    private RouteCandidate.RouteCandidateBuilder baseCandidate(String providerCode) {
        return RouteCandidate.builder()
                .providerType("OPENAI")
                .providerCode(providerCode)
                .targetModel("gpt-4o-mini")
                .providerPriority(0)
                .supportsStream(true)
                .supportsJson(true)
                .qualityScore(50)
                .latencyScore(50)
                .costScore(50)
                .toolScore(50)
                .visionScore(50)
                .reasoningScore(50)
                .reliabilityScore(50)
                .weight(100);
    }

    private RoutingConfigSnapshot buildSnapshot(String routeKey, RouteCandidate... candidates) {
        var entry = new RoutingConfigSnapshot.AutoRouteEntry(routeKey, "AUTO", List.of(candidates));
        // 从候选中构建 providerMap，确保路由时能获取到 ProviderEntry 和 API Key
        Map<String, RoutingConfigSnapshot.ProviderEntry> providerMap = new java.util.LinkedHashMap<>();
        for (RouteCandidate c : candidates) {
            if (!providerMap.containsKey(c.getProviderCode())) {
                providerMap.put(c.getProviderCode(), new RoutingConfigSnapshot.ProviderEntry(
                        c.getProviderType(), c.getProviderCode(), true,
                        "https://api." + c.getProviderCode() + ".com",
                        List.of(new ProviderKeyEntry(1L, "sk-test-key", "sk-test****key", 100, 0)),
                        KeySelectionStrategy.ROUND_ROBIN,
                        60, c.getProviderPriority(),
                        null, Map.of(), "full"
                ));
            }
        }
        return new RoutingConfigSnapshot(
                java.util.Collections.emptyMap(),
                List.of(),
                providerMap,
                java.util.Collections.singletonMap(routeKey, entry),
                1L, "test");
    }

    private RoutingConfigSnapshot buildEmptySnapshot() {
        return new RoutingConfigSnapshot(
                java.util.Collections.emptyMap(),
                List.of(),
                java.util.Collections.emptyMap(),
                java.util.Collections.emptyMap(),
                1L, "test");
    }

    private UnifiedRequest buildRequest(String model, String protocol) {
        UnifiedRequest req = new UnifiedRequest();
        req.setModel(model);
        req.setRequestProtocol(protocol);
        return req;
    }

    private UnifiedRequest buildVisionRequest() {
        UnifiedPart part = new UnifiedPart();
        part.setType(UnifiedPart.TYPE_IMAGE);
        part.setUrl("https://example.com/image.png");
        UnifiedMessage message = new UnifiedMessage();
        message.setRole("user");
        message.setParts(List.of(part));
        UnifiedRequest request = buildRequest("auto", null);
        request.setMessages(List.of(message));
        return request;
    }

    private UnifiedRequest buildRequiredToolRequest() {
        UnifiedTool tool = new UnifiedTool();
        tool.setName("search");
        UnifiedToolChoice toolChoice = new UnifiedToolChoice();
        toolChoice.setType("required");
        UnifiedRequest request = buildRequest("auto", null);
        request.setTools(List.of(tool));
        request.setToolChoice(toolChoice);
        return request;
    }

    private UnifiedRequest buildJsonRequest() {
        UnifiedResponseFormat responseFormat = new UnifiedResponseFormat();
        responseFormat.setType("json_schema");
        UnifiedRequest request = buildRequest("auto", null);
        request.setResponseFormat(responseFormat);
        return request;
    }

    private UnifiedRequest buildTextRequest(String model, String text) {
        UnifiedPart part = new UnifiedPart();
        part.setType(UnifiedPart.TYPE_TEXT);
        part.setText(text);
        UnifiedMessage message = new UnifiedMessage();
        message.setRole("user");
        message.setParts(List.of(part));
        UnifiedRequest request = buildRequest(model, null);
        request.setMessages(List.of(message));
        return request;
    }
}
