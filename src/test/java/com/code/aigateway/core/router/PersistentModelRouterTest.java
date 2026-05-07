package com.code.aigateway.core.router;

import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.runtime.RoutingSnapshotHolder;
import com.code.aigateway.provider.ProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 持久化路由器单元测试
 *
 * <p>覆盖四层降级策略：精确匹配 → 模式匹配 → Auto智能路由 → YAML兜底 → 透传</p>
 */
@ExtendWith(MockitoExtension.class)
class PersistentModelRouterTest {

    @Mock
    private RoutingSnapshotHolder routingSnapshotHolder;

    @Mock
    private ConfigBasedModelRouter fallbackRouter;

    @Mock
    private AutoRouteSelector autoRouteSelector;

    private PersistentModelRouter router;

    @BeforeEach
    void setUp() {
        router = new PersistentModelRouter(routingSnapshotHolder, fallbackRouter, autoRouteSelector);
    }

    // ==================== 辅助方法 ====================

    /** 构建统一请求 */
    private UnifiedRequest buildRequest(String model, String protocol) {
        UnifiedRequest req = new UnifiedRequest();
        req.setModel(model);
        req.setRequestProtocol(protocol);
        return req;
    }

    /** 构建路由候选 */
    private RouteCandidate buildCandidate(String providerType, String providerCode,
                                          String targetModel, int priority,
                                          List<String> supportedProtocols) {
        return RouteCandidate.builder()
                .providerType(providerType)
                .providerCode(providerCode)
                .targetModel(targetModel)
                .providerBaseUrl("https://api." + providerCode + ".com")
                .providerApiKey("sk-test-key")
                .providerTimeoutSeconds(60)
                .providerPriority(priority)
                .supportedProtocols(supportedProtocols)
                .build();
    }

    /** 构建包含精确匹配的快照 */
    private RoutingConfigSnapshot buildSnapshotWithExactMatch(
            String aliasName, List<RouteCandidate> candidates) {
        return buildSnapshot(
                Map.of(aliasName, candidates),
                List.of(),
                Map.of(),
                Map.of()
        );
    }

    /** 构建完整快照 */
    private RoutingConfigSnapshot buildSnapshot(
            Map<String, List<RouteCandidate>> aliasRouteMap,
            List<RoutingConfigSnapshot.PatternRoute> patternRoutes,
            Map<String, RoutingConfigSnapshot.ProviderEntry> providerMap,
            Map<String, RoutingConfigSnapshot.AutoRouteEntry> autoRouteMap) {
        return new RoutingConfigSnapshot(
                aliasRouteMap, patternRoutes, providerMap, Map.of(), autoRouteMap, List.of(), 1L, "test"
        );
    }

    /** 构建空快照（无任何路由规则） */
    private RoutingConfigSnapshot buildEmptySnapshot() {
        return buildSnapshot(Map.of(), List.of(), Map.of(), Map.of());
    }

    /** 构建带 Provider 的空快照（用于透传测试） */
    private RoutingConfigSnapshot buildEmptySnapshotWithProviders(
            RoutingConfigSnapshot.ProviderEntry... providers) {
        Map<String, RoutingConfigSnapshot.ProviderEntry> providerMap = new java.util.LinkedHashMap<>();
        for (RoutingConfigSnapshot.ProviderEntry p : providers) {
            providerMap.put(p.providerCode(), p);
        }
        return buildSnapshot(Map.of(), List.of(), providerMap, Map.of());
    }

    // ==================== route() 方法测试 ====================

    @Nested
    @DisplayName("route() - 单候选路由")
    class RouteSingle {

        @Test
        @DisplayName("模型名为空时抛出 INVALID_REQUEST")
        void shouldThrowWhenModelIsNull() {
            UnifiedRequest req = buildRequest(null, "openai-chat");
            GatewayException ex = assertThrows(GatewayException.class, () -> router.route(req));
            assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
        }

        @Test
        @DisplayName("模型名为空白字符串时抛出 INVALID_REQUEST")
        void shouldThrowWhenModelIsBlank() {
            UnifiedRequest req = buildRequest("  ", "openai-chat");
            GatewayException ex = assertThrows(GatewayException.class, () -> router.route(req));
            assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
        }

        @Test
        @DisplayName("快照不存在且非 auto 模型时，回退到 YAML 路由")
        void shouldFallbackToYamlWhenSnapshotIsNull() {
            when(routingSnapshotHolder.get()).thenReturn(null);
            when(autoRouteSelector.isAutoModel("gpt-4o")).thenReturn(false);
            RouteResult yamlResult = RouteResult.builder()
                    .providerType(ProviderType.OPENAI)
                    .providerName("yaml-openai")
                    .targetModel("gpt-4o")
                    .providerBaseUrl("https://yaml.test.com")
                    .providerApiKey("yaml-key")
                    .providerTimeoutSeconds(60)
                    .build();
            when(fallbackRouter.route(any())).thenReturn(yamlResult);

            RouteResult result = router.route(buildRequest("gpt-4o", "openai-chat"));

            assertEquals(ProviderType.OPENAI, result.getProviderType());
            assertEquals("yaml-openai", result.getProviderName());
            verify(fallbackRouter).route(any());
        }

        @Test
        @DisplayName("快照不存在且是 auto 模型时，抛出 MODEL_NOT_FOUND")
        void shouldThrowWhenSnapshotNullAndAutoModel() {
            when(routingSnapshotHolder.get()).thenReturn(null);
            when(autoRouteSelector.isAutoModel("auto")).thenReturn(true);

            GatewayException ex = assertThrows(GatewayException.class,
                    () -> router.route(buildRequest("auto", "openai-chat")));
            assertEquals(ErrorCode.MODEL_NOT_FOUND, ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("route() - 精确匹配")
    class ExactMatch {

        @Test
        @DisplayName("精确匹配成功时返回首选候选")
        void shouldReturnExactMatchCandidate() {
            RouteCandidate candidate = buildCandidate("openai", "openai-main", "gpt-4o", 10, List.of("OPENAI_CHAT"));
            RoutingConfigSnapshot snapshot = buildSnapshotWithExactMatch("gpt-4o", List.of(candidate));
            when(routingSnapshotHolder.get()).thenReturn(snapshot);

            RouteResult result = router.route(buildRequest("gpt-4o", "openai-chat"));

            assertEquals(ProviderType.OPENAI, result.getProviderType());
            assertEquals("openai-main", result.getProviderName());
            assertEquals("gpt-4o", result.getTargetModel());
        }

        @Test
        @DisplayName("精确匹配但无候选支持请求协议时，抛出 PROVIDER_NOT_FOUND")
        void shouldThrowWhenExactMatchButProtocolNotSupported() {
            // 候选只支持 ANTHROPIC 协议
            RouteCandidate candidate = buildCandidate("anthropic", "claude-main", "gpt-4o", 10, List.of("ANTHROPIC"));
            RoutingConfigSnapshot snapshot = buildSnapshotWithExactMatch("gpt-4o", List.of(candidate));
            when(routingSnapshotHolder.get()).thenReturn(snapshot);

            // 请求协议为 openai-chat
            GatewayException ex = assertThrows(GatewayException.class,
                    () -> router.route(buildRequest("gpt-4o", "openai-chat")));
            assertEquals(ErrorCode.PROVIDER_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("精确匹配时按优先级选择支持请求协议的首个候选")
        void shouldSelectFirstProtocolMatchByPriority() {
            // 高优先级但不支持请求协议
            RouteCandidate highPriority = buildCandidate("anthropic", "claude-main", "gpt-4o", 20, List.of("ANTHROPIC"));
            // 低优先级但支持请求协议
            RouteCandidate lowPriority = buildCandidate("openai", "openai-main", "gpt-4o", 10, List.of("OPENAI_CHAT"));
            RoutingConfigSnapshot snapshot = buildSnapshotWithExactMatch("gpt-4o", List.of(highPriority, lowPriority));
            when(routingSnapshotHolder.get()).thenReturn(snapshot);

            RouteResult result = router.route(buildRequest("gpt-4o", "openai-chat"));

            assertEquals("openai-main", result.getProviderName());
        }

        @Test
        @DisplayName("候选 supportedProtocols 为空时表示支持所有协议")
        void shouldMatchWhenSupportedProtocolsEmpty() {
            RouteCandidate candidate = buildCandidate("openai", "openai-main", "gpt-4o", 10, List.of());
            RoutingConfigSnapshot snapshot = buildSnapshotWithExactMatch("gpt-4o", List.of(candidate));
            when(routingSnapshotHolder.get()).thenReturn(snapshot);

            RouteResult result = router.route(buildRequest("gpt-4o", "anthropic"));

            assertEquals("openai-main", result.getProviderName());
        }

        @Test
        @DisplayName("候选 supportedProtocols 为 null 时表示支持所有协议")
        void shouldMatchWhenSupportedProtocolsNull() {
            RouteCandidate candidate = buildCandidate("openai", "openai-main", "gpt-4o", 10, null);
            RoutingConfigSnapshot snapshot = buildSnapshotWithExactMatch("gpt-4o", List.of(candidate));
            when(routingSnapshotHolder.get()).thenReturn(snapshot);

            RouteResult result = router.route(buildRequest("gpt-4o", "anthropic"));

            assertEquals("openai-main", result.getProviderName());
        }
    }

    @Nested
    @DisplayName("route() - 模式匹配（GLOB / REGEX）")
    class PatternMatch {

        @Test
        @DisplayName("GLOB 模式匹配成功")
        void shouldMatchGlobPattern() {
            RouteCandidate candidate = buildCandidate("openai", "openai-main", "gpt-4-turbo", 10, List.of("OPENAI_CHAT"));
            RoutingConfigSnapshot.PatternRoute patternRoute = new RoutingConfigSnapshot.PatternRoute(
                    MatchType.GLOB, GlobPatternUtil.globToRegex("gpt-4*"), "gpt-4*", List.of(candidate));
            RoutingConfigSnapshot snapshot = buildSnapshot(
                    Map.of(), List.of(patternRoute), Map.of(), Map.of());
            when(routingSnapshotHolder.get()).thenReturn(snapshot);

            RouteResult result = router.route(buildRequest("gpt-4o-mini", "openai-chat"));

            assertEquals("gpt-4-turbo", result.getTargetModel());
        }

        @Test
        @DisplayName("REGEX 模式匹配成功")
        void shouldMatchRegexPattern() {
            RouteCandidate candidate = buildCandidate("openai", "openai-main", "gpt-base", 10, List.of("OPENAI_CHAT"));
            RoutingConfigSnapshot.PatternRoute patternRoute = new RoutingConfigSnapshot.PatternRoute(
                    MatchType.REGEX, "^gpt-\\d+$", "gpt-\\d+", List.of(candidate));
            RoutingConfigSnapshot snapshot = buildSnapshot(
                    Map.of(), List.of(patternRoute), Map.of(), Map.of());
            when(routingSnapshotHolder.get()).thenReturn(snapshot);

            RouteResult result = router.route(buildRequest("gpt-4", "openai-chat"));

            assertEquals("gpt-base", result.getTargetModel());
        }

        @Test
        @DisplayName("GLOB 优先于 REGEX 匹配")
        void shouldPreferGlobOverRegex() {
            RouteCandidate globCandidate = buildCandidate("openai", "openai-glob", "glob-model", 5, List.of("OPENAI_CHAT"));
            RouteCandidate regexCandidate = buildCandidate("openai", "openai-regex", "regex-model", 10, List.of("OPENAI_CHAT"));

            // GLOB 规则排在后面，但仍应优先匹配
            RoutingConfigSnapshot.PatternRoute regexRoute = new RoutingConfigSnapshot.PatternRoute(
                    MatchType.REGEX, "^gpt-4.*$", "gpt-4.*", List.of(regexCandidate));
            RoutingConfigSnapshot.PatternRoute globRoute = new RoutingConfigSnapshot.PatternRoute(
                    MatchType.GLOB, GlobPatternUtil.globToRegex("gpt-4*"), "gpt-4*", List.of(globCandidate));

            RoutingConfigSnapshot snapshot = buildSnapshot(
                    Map.of(), List.of(regexRoute, globRoute), Map.of(), Map.of());
            when(routingSnapshotHolder.get()).thenReturn(snapshot);

            RouteResult result = router.route(buildRequest("gpt-4o", "openai-chat"));

            assertEquals("glob-model", result.getTargetModel());
        }

        @Test
        @DisplayName("模式匹配不命中时继续走后续路由")
        void shouldContinueWhenPatternNotMatched() {
            RouteCandidate candidate = buildCandidate("openai", "openai-main", "other", 10, List.of("OPENAI_CHAT"));
            RoutingConfigSnapshot.PatternRoute patternRoute = new RoutingConfigSnapshot.PatternRoute(
                    MatchType.GLOB, GlobPatternUtil.globToRegex("claude-*"), "claude-*", List.of(candidate));
            RoutingConfigSnapshot snapshot = buildSnapshot(
                    Map.of(), List.of(patternRoute), Map.of(), Map.of());
            when(routingSnapshotHolder.get()).thenReturn(snapshot);
            when(autoRouteSelector.isAutoModel("gpt-4o")).thenReturn(false);

            // YAML 也未命中 → 抛出 MODEL_NOT_FOUND，触发透传
            when(fallbackRouter.route(any()))
                    .thenThrow(new GatewayException(ErrorCode.MODEL_NOT_FOUND, "not found"));

            // 快照无 Provider，透传候选为空
            GatewayException ex = assertThrows(GatewayException.class,
                    () -> router.route(buildRequest("gpt-4o", "openai-chat")));
            assertEquals(ErrorCode.PROVIDER_NOT_FOUND, ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("route() - Auto 智能路由")
    class AutoRoute {

        @Test
        @DisplayName("精确匹配和模式匹配都未命中时，走 Auto 智能路由")
        void shouldUseAutoRouteWhenExactAndPatternMissed() {
            RoutingConfigSnapshot snapshot = buildEmptySnapshot();
            when(routingSnapshotHolder.get()).thenReturn(snapshot);
            when(autoRouteSelector.isAutoModel("auto")).thenReturn(true);

            RouteResult autoResult = RouteResult.builder()
                    .providerType(ProviderType.OPENAI)
                    .providerName("auto-openai")
                    .targetModel("gpt-4o")
                    .providerBaseUrl("https://auto.test.com")
                    .providerApiKey("auto-key")
                    .providerTimeoutSeconds(60)
                    .build();
            when(autoRouteSelector.select(snapshot, buildRequest("auto", "openai-chat"))).thenReturn(autoResult);

            RouteResult result = router.route(buildRequest("auto", "openai-chat"));

            assertEquals("auto-openai", result.getProviderName());
            verify(autoRouteSelector).select(snapshot, buildRequest("auto", "openai-chat"));
        }

        @Test
        @DisplayName("auto:code 风格的模型名也走智能路由")
        void shouldUseAutoRouteForPrefixedModel() {
            RoutingConfigSnapshot snapshot = buildEmptySnapshot();
            when(routingSnapshotHolder.get()).thenReturn(snapshot);
            when(autoRouteSelector.isAutoModel("auto:code")).thenReturn(true);

            RouteResult autoResult = RouteResult.builder()
                    .providerType(ProviderType.ANTHROPIC)
                    .providerName("auto-claude")
                    .targetModel("claude-sonnet")
                    .providerBaseUrl("https://auto.test.com")
                    .providerApiKey("auto-key")
                    .providerTimeoutSeconds(60)
                    .build();
            when(autoRouteSelector.select(snapshot, buildRequest("auto:code", "anthropic"))).thenReturn(autoResult);

            RouteResult result = router.route(buildRequest("auto:code", "anthropic"));

            assertEquals("auto-claude", result.getProviderName());
        }
    }

    @Nested
    @DisplayName("route() - YAML 兜底与透传")
    class FallbackAndPassthrough {

        @Test
        @DisplayName("快照未命中时回退到 YAML 路由成功")
        void shouldFallbackToYamlRouter() {
            RoutingConfigSnapshot snapshot = buildEmptySnapshot();
            when(routingSnapshotHolder.get()).thenReturn(snapshot);
            when(autoRouteSelector.isAutoModel("unknown-model")).thenReturn(false);

            RouteResult yamlResult = RouteResult.builder()
                    .providerType(ProviderType.OPENAI)
                    .providerName("yaml-openai")
                    .targetModel("unknown-model")
                    .providerBaseUrl("https://yaml.test.com")
                    .providerApiKey("yaml-key")
                    .providerTimeoutSeconds(60)
                    .build();
            when(fallbackRouter.route(any())).thenReturn(yamlResult);

            RouteResult result = router.route(buildRequest("unknown-model", "openai-chat"));

            assertEquals("yaml-openai", result.getProviderName());
        }

        @Test
        @DisplayName("YAML 路由抛出 MODEL_NOT_FOUND 时，走透传分支")
        void shouldPassthroughWhenYamlThrowsModelNotFound() {
            RoutingConfigSnapshot.ProviderEntry provider = new RoutingConfigSnapshot.ProviderEntry(
                    "openai", "openai-main", true, "https://api.openai.com", "sk-key", 60, 10, List.of("OPENAI_CHAT"), Map.of());
            RoutingConfigSnapshot snapshot = buildEmptySnapshotWithProviders(provider);
            when(routingSnapshotHolder.get()).thenReturn(snapshot);
            when(autoRouteSelector.isAutoModel("unknown-model")).thenReturn(false);
            when(fallbackRouter.route(any()))
                    .thenThrow(new GatewayException(ErrorCode.MODEL_NOT_FOUND, "not found"));

            RouteResult result = router.route(buildRequest("unknown-model", "openai-chat"));

            // 透传：targetModel 保持原始模型名
            assertEquals("unknown-model", result.getTargetModel());
            assertEquals("openai-main", result.getProviderName());
            assertEquals(ProviderType.OPENAI, result.getProviderType());
        }

        @Test
        @DisplayName("透传时按协议过滤 Provider")
        void shouldFilterProviderByProtocolInPassthrough() {
            // Provider 只支持 ANTHROPIC
            RoutingConfigSnapshot.ProviderEntry provider = new RoutingConfigSnapshot.ProviderEntry(
                    "anthropic", "claude-main", true, "https://api.anthropic.com", "sk-key", 60, 10, List.of("ANTHROPIC"), Map.of());
            RoutingConfigSnapshot snapshot = buildEmptySnapshotWithProviders(provider);
            when(routingSnapshotHolder.get()).thenReturn(snapshot);
            when(autoRouteSelector.isAutoModel("unknown-model")).thenReturn(false);
            when(fallbackRouter.route(any()))
                    .thenThrow(new GatewayException(ErrorCode.MODEL_NOT_FOUND, "not found"));

            // 请求协议为 openai-chat，无 Provider 支持
            GatewayException ex = assertThrows(GatewayException.class,
                    () -> router.route(buildRequest("unknown-model", "openai-chat")));
            assertEquals(ErrorCode.PROVIDER_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("YAML 路由抛出非 MODEL_NOT_FOUND 异常时，直接向上抛出")
        void shouldRethrowNonModelNotFoundFromYaml() {
            RoutingConfigSnapshot snapshot = buildEmptySnapshot();
            when(routingSnapshotHolder.get()).thenReturn(snapshot);
            when(autoRouteSelector.isAutoModel("gpt-4o")).thenReturn(false);
            when(fallbackRouter.route(any()))
                    .thenThrow(new GatewayException(ErrorCode.PROVIDER_DISABLED, "disabled"));

            GatewayException ex = assertThrows(GatewayException.class,
                    () -> router.route(buildRequest("gpt-4o", "openai-chat")));
            assertEquals(ErrorCode.PROVIDER_DISABLED, ex.getErrorCode());
        }

        @Test
        @DisplayName("透传时 Provider supportedProtocols 为空表示支持所有协议")
        void shouldTreatEmptyProtocolsAsAllSupportedInPassthrough() {
            RoutingConfigSnapshot.ProviderEntry provider = new RoutingConfigSnapshot.ProviderEntry(
                    "openai", "openai-main", true, "https://api.openai.com", "sk-key", 60, 10, List.of(), Map.of());
            RoutingConfigSnapshot snapshot = buildEmptySnapshotWithProviders(provider);
            when(routingSnapshotHolder.get()).thenReturn(snapshot);
            when(autoRouteSelector.isAutoModel("unknown-model")).thenReturn(false);
            when(fallbackRouter.route(any()))
                    .thenThrow(new GatewayException(ErrorCode.MODEL_NOT_FOUND, "not found"));

            // 请求协议为 anthropic，但 Provider 支持所有协议
            RouteResult result = router.route(buildRequest("unknown-model", "anthropic"));
            assertEquals("openai-main", result.getProviderName());
        }

        @Test
        @DisplayName("透传时请求协议为 null 表示不限制协议")
        void shouldNotFilterWhenRequestProtocolNullInPassthrough() {
            RoutingConfigSnapshot.ProviderEntry provider = new RoutingConfigSnapshot.ProviderEntry(
                    "openai", "openai-main", true, "https://api.openai.com", "sk-key", 60, 10, List.of("OPENAI_CHAT"), Map.of());
            RoutingConfigSnapshot snapshot = buildEmptySnapshotWithProviders(provider);
            when(routingSnapshotHolder.get()).thenReturn(snapshot);
            when(autoRouteSelector.isAutoModel("unknown-model")).thenReturn(false);
            when(fallbackRouter.route(any()))
                    .thenThrow(new GatewayException(ErrorCode.MODEL_NOT_FOUND, "not found"));

            RouteResult result = router.route(buildRequest("unknown-model", null));
            assertEquals("openai-main", result.getProviderName());
        }
    }

    @Nested
    @DisplayName("route() - 非法 providerType 处理")
    class InvalidProviderType {

        @Test
        @DisplayName("候选的 providerType 非法时抛出 PROVIDER_NOT_FOUND")
        void shouldThrowWhenProviderTypeInvalid() {
            RouteCandidate candidate = buildCandidate("invalid-type", "unknown-provider", "model", 10, List.of("OPENAI_CHAT"));
            RoutingConfigSnapshot snapshot = buildSnapshotWithExactMatch("test-model", List.of(candidate));
            when(routingSnapshotHolder.get()).thenReturn(snapshot);

            GatewayException ex = assertThrows(GatewayException.class,
                    () -> router.route(buildRequest("test-model", "openai-chat")));
            assertEquals(ErrorCode.PROVIDER_NOT_FOUND, ex.getErrorCode());
        }
    }

    // ==================== routeAll() 方法测试 ====================

    @Nested
    @DisplayName("routeAll() - 多候选故障转移路由")
    class RouteAll {

        @Test
        @DisplayName("模型名为空时抛出 INVALID_REQUEST")
        void shouldThrowWhenModelIsNull() {
            UnifiedRequest req = buildRequest(null, "openai-chat");
            GatewayException ex = assertThrows(GatewayException.class, () -> router.routeAll(req));
            assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
        }

        @Test
        @DisplayName("精确匹配返回全部候选（按协议过滤）")
        void shouldReturnAllExactMatchCandidates() {
            RouteCandidate candidate1 = buildCandidate("openai", "openai-main", "gpt-4o", 20, List.of("OPENAI_CHAT"));
            RouteCandidate candidate2 = buildCandidate("anthropic", "claude-main", "gpt-4o", 10, List.of("OPENAI_CHAT"));
            RoutingConfigSnapshot snapshot = buildSnapshotWithExactMatch("gpt-4o", List.of(candidate1, candidate2));
            when(routingSnapshotHolder.get()).thenReturn(snapshot);

            List<RouteResult> results = router.routeAll(buildRequest("gpt-4o", "openai-chat"));

            assertEquals(2, results.size());
            assertEquals("openai-main", results.get(0).getProviderName());
            assertEquals("claude-main", results.get(1).getProviderName());
        }

        @Test
        @DisplayName("routeAll 按协议过滤不支持的候选")
        void shouldFilterUnsupportedProtocolInRouteAll() {
            RouteCandidate candidate1 = buildCandidate("openai", "openai-main", "gpt-4o", 20, List.of("OPENAI_CHAT"));
            RouteCandidate candidate2 = buildCandidate("anthropic", "claude-main", "gpt-4o", 10, List.of("ANTHROPIC"));
            RoutingConfigSnapshot snapshot = buildSnapshotWithExactMatch("gpt-4o", List.of(candidate1, candidate2));
            when(routingSnapshotHolder.get()).thenReturn(snapshot);

            // 请求协议为 openai-chat，candidate2 应被过滤
            List<RouteResult> results = router.routeAll(buildRequest("gpt-4o", "openai-chat"));

            assertEquals(1, results.size());
            assertEquals("openai-main", results.get(0).getProviderName());
        }

        @Test
        @DisplayName("routeAll 模式匹配返回全部候选")
        void shouldReturnAllPatternMatchCandidates() {
            RouteCandidate candidate1 = buildCandidate("openai", "openai-main", "gpt-base", 20, List.of("OPENAI_CHAT"));
            RouteCandidate candidate2 = buildCandidate("anthropic", "claude-main", "gpt-base", 10, List.of("OPENAI_CHAT"));
            RoutingConfigSnapshot.PatternRoute patternRoute = new RoutingConfigSnapshot.PatternRoute(
                    MatchType.GLOB, GlobPatternUtil.globToRegex("gpt-*"), "gpt-*", List.of(candidate1, candidate2));
            RoutingConfigSnapshot snapshot = buildSnapshot(
                    Map.of(), List.of(patternRoute), Map.of(), Map.of());
            when(routingSnapshotHolder.get()).thenReturn(snapshot);

            List<RouteResult> results = router.routeAll(buildRequest("gpt-4o", "openai-chat"));

            assertEquals(2, results.size());
        }

        @Test
        @DisplayName("routeAll 精确和模式未命中时走 Auto 智能路由")
        void shouldUseAutoRouteInRouteAll() {
            RoutingConfigSnapshot snapshot = buildEmptySnapshot();
            when(routingSnapshotHolder.get()).thenReturn(snapshot);
            when(autoRouteSelector.isAutoModel("auto")).thenReturn(true);

            RouteResult autoResult = RouteResult.builder()
                    .providerType(ProviderType.OPENAI)
                    .providerName("auto-openai")
                    .targetModel("gpt-4o")
                    .providerBaseUrl("https://auto.test.com")
                    .providerApiKey("auto-key")
                    .providerTimeoutSeconds(60)
                    .build();
            when(autoRouteSelector.selectAll(snapshot, buildRequest("auto", "openai-chat")))
                    .thenReturn(List.of(autoResult));

            List<RouteResult> results = router.routeAll(buildRequest("auto", "openai-chat"));

            assertEquals(1, results.size());
            assertEquals("auto-openai", results.get(0).getProviderName());
        }

        @Test
        @DisplayName("routeAll 快照为空时回退到 YAML")
        void shouldFallbackToYamlInRouteAll() {
            when(routingSnapshotHolder.get()).thenReturn(null);
            when(autoRouteSelector.isAutoModel("gpt-4o")).thenReturn(false);

            RouteResult yamlResult = RouteResult.builder()
                    .providerType(ProviderType.OPENAI)
                    .providerName("yaml-openai")
                    .targetModel("gpt-4o")
                    .providerBaseUrl("https://yaml.test.com")
                    .providerApiKey("yaml-key")
                    .providerTimeoutSeconds(60)
                    .build();
            when(fallbackRouter.routeAll(any())).thenReturn(List.of(yamlResult));

            List<RouteResult> results = router.routeAll(buildRequest("gpt-4o", "openai-chat"));

            assertEquals(1, results.size());
            assertEquals("yaml-openai", results.get(0).getProviderName());
        }

        @Test
        @DisplayName("routeAll YAML 未命中时走透传，返回所有支持协议的 Provider")
        void shouldReturnAllPassthroughCandidatesInRouteAll() {
            RoutingConfigSnapshot.ProviderEntry provider1 = new RoutingConfigSnapshot.ProviderEntry(
                    "openai", "openai-main", true, "https://api.openai.com", "sk-key1", 60, 20, List.of("OPENAI_CHAT"), Map.of());
            RoutingConfigSnapshot.ProviderEntry provider2 = new RoutingConfigSnapshot.ProviderEntry(
                    "anthropic", "claude-main", true, "https://api.anthropic.com", "sk-key2", 60, 10, List.of("OPENAI_CHAT"), Map.of());
            RoutingConfigSnapshot snapshot = buildEmptySnapshotWithProviders(provider1, provider2);
            when(routingSnapshotHolder.get()).thenReturn(snapshot);
            when(autoRouteSelector.isAutoModel("unknown")).thenReturn(false);
            when(fallbackRouter.routeAll(any()))
                    .thenThrow(new GatewayException(ErrorCode.MODEL_NOT_FOUND, "not found"));

            List<RouteResult> results = router.routeAll(buildRequest("unknown", "openai-chat"));

            assertEquals(2, results.size());
            // 按优先级降序排列
            assertEquals("openai-main", results.get(0).getProviderName());
            assertEquals("claude-main", results.get(1).getProviderName());
            // 透传时 targetModel 保持原始模型名
            assertEquals("unknown", results.get(0).getTargetModel());
            assertEquals("unknown", results.get(1).getTargetModel());
        }

        @Test
        @DisplayName("routeAll 所有候选都不支持请求协议时抛出 PROVIDER_NOT_FOUND")
        void shouldThrowWhenAllCandidatesUnsupportedProtocol() {
            RouteCandidate candidate = buildCandidate("anthropic", "claude-main", "model", 10, List.of("ANTHROPIC"));
            RoutingConfigSnapshot snapshot = buildSnapshotWithExactMatch("test-model", List.of(candidate));
            when(routingSnapshotHolder.get()).thenReturn(snapshot);

            GatewayException ex = assertThrows(GatewayException.class,
                    () -> router.routeAll(buildRequest("test-model", "openai-chat")));
            assertEquals(ErrorCode.PROVIDER_NOT_FOUND, ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("route() - 协议归一化匹配")
    class ProtocolNormalization {

        @Test
        @DisplayName("请求协议 openai-chat 与候选 OPENAI_CHAT 可正确匹配（归一化比较）")
        void shouldMatchWithNormalizedProtocol() {
            RouteCandidate candidate = buildCandidate("openai", "openai-main", "gpt-4o", 10, List.of("OPENAI_CHAT"));
            RoutingConfigSnapshot snapshot = buildSnapshotWithExactMatch("gpt-4o", List.of(candidate));
            when(routingSnapshotHolder.get()).thenReturn(snapshot);

            // 请求使用小写连字符格式
            RouteResult result = router.route(buildRequest("gpt-4o", "openai-chat"));

            assertNotNull(result);
            assertEquals("openai-main", result.getProviderName());
        }
    }

    @Nested
    @DisplayName("route() - 降级优先级验证")
    class DegradationPriority {

        @Test
        @DisplayName("精确匹配优先于模式匹配")
        void exactMatchTakesPrecedenceOverPattern() {
            // 精确匹配候选
            RouteCandidate exactCandidate = buildCandidate("openai", "exact-openai", "exact-model", 5, List.of("OPENAI_CHAT"));
            // 模式匹配候选
            RouteCandidate patternCandidate = buildCandidate("openai", "pattern-openai", "pattern-model", 10, List.of("OPENAI_CHAT"));
            RoutingConfigSnapshot.PatternRoute patternRoute = new RoutingConfigSnapshot.PatternRoute(
                    MatchType.GLOB, GlobPatternUtil.globToRegex("gpt-*"), "gpt-*", List.of(patternCandidate));

            // gpt-4o 同时存在于精确匹配和模式匹配中
            RoutingConfigSnapshot snapshot = buildSnapshot(
                    Map.of("gpt-4o", List.of(exactCandidate)),
                    List.of(patternRoute),
                    Map.of(), Map.of());
            when(routingSnapshotHolder.get()).thenReturn(snapshot);

            RouteResult result = router.route(buildRequest("gpt-4o", "openai-chat"));

            // 精确匹配优先
            assertEquals("exact-model", result.getTargetModel());
        }

        @Test
        @DisplayName("模式匹配优先于 Auto 智能路由")
        void patternMatchTakesPrecedenceOverAuto() {
            RouteCandidate patternCandidate = buildCandidate("openai", "pattern-openai", "pattern-model", 5, List.of("OPENAI_CHAT"));
            RoutingConfigSnapshot.PatternRoute patternRoute = new RoutingConfigSnapshot.PatternRoute(
                    MatchType.GLOB, GlobPatternUtil.globToRegex("auto*"), "auto*", List.of(patternCandidate));

            RoutingConfigSnapshot snapshot = buildSnapshot(
                    Map.of(), List.of(patternRoute), Map.of(), Map.of());
            when(routingSnapshotHolder.get()).thenReturn(snapshot);

            // "auto-model" 既是 auto 模型名又能匹配 GLOB 模式
            // 但由于 isAutoModel 在精确/模式匹配后才检查，模式匹配优先，autoRouteSelector 不会被调用
            RouteResult result = router.route(buildRequest("auto-model", "openai-chat"));

            assertEquals("pattern-model", result.getTargetModel());
            // Auto 路由不应被调用
            verify(autoRouteSelector, never()).select(any(), any());
        }

        @Test
        @DisplayName("Auto 智能路由优先于 YAML 兜底")
        void autoRouteTakesPrecedenceOverYaml() {
            RoutingConfigSnapshot snapshot = buildEmptySnapshot();
            when(routingSnapshotHolder.get()).thenReturn(snapshot);
            when(autoRouteSelector.isAutoModel("auto")).thenReturn(true);

            RouteResult autoResult = RouteResult.builder()
                    .providerType(ProviderType.OPENAI)
                    .providerName("auto-openai")
                    .targetModel("gpt-4o")
                    .providerBaseUrl("https://auto.test.com")
                    .providerApiKey("auto-key")
                    .providerTimeoutSeconds(60)
                    .build();
            when(autoRouteSelector.select(any(), any())).thenReturn(autoResult);

            router.route(buildRequest("auto", "openai-chat"));

            // YAML 路由不应被调用
            verify(fallbackRouter, never()).route(any());
        }
    }
}
