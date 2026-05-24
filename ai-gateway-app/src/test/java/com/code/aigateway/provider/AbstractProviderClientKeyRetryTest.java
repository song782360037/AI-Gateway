package com.code.aigateway.provider;

import com.code.aigateway.config.GatewayProperties;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.resilience.CircuitBreakerManager;
import com.code.aigateway.core.router.KeySelectionStrategy;
import com.code.aigateway.core.router.ProviderKeyEntry;
import com.code.aigateway.core.stats.RequestStatsContext;
import com.code.aigateway.sdk.error.ErrorCode;
import com.code.aigateway.sdk.model.UnifiedRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AbstractProviderClient Key 降级重试逻辑单元测试
 */
class AbstractProviderClientKeyRetryTest {

    /** 测试用 Key 列表 */
    private static final List<ProviderKeyEntry> TEST_KEYS = List.of(
            new ProviderKeyEntry(1L, "sk-key-aaa", "sk-key-a***aaa", 100, 0),
            new ProviderKeyEntry(2L, "sk-key-bbb", "sk-key-b***bbb", 100, 1),
            new ProviderKeyEntry(3L, "sk-key-ccc", "sk-key-c***ccc", 100, 2)
    );

    private TestableProviderClient client;
    private GatewayProperties gatewayProperties;

    @BeforeEach
    void setUp() {
        gatewayProperties = new GatewayProperties();
        gatewayProperties.setRetry(new GatewayProperties.RetryProperties());
        gatewayProperties.getRetry().setMaxRetries(0);

        client = new TestableProviderClient(
                null, new ObjectMapper(), gatewayProperties, null
        );
    }

    // ==================== 辅助方法 ====================

    /** 构建带有多 Key metadata 的 UnifiedRequest */
    private UnifiedRequest buildRequestWithKeys(List<ProviderKeyEntry> keys) {
        return buildRequestWithKeys(keys, KeySelectionStrategy.ROUND_ROBIN);
    }

    private UnifiedRequest buildRequestWithKeys(List<ProviderKeyEntry> keys, KeySelectionStrategy strategy) {
        UnifiedRequest request = new UnifiedRequest();
        request.setModel("test-model");

        // 设置执行上下文（使用第一个 Key）
        UnifiedRequest.ProviderExecutionContext ctx = new UnifiedRequest.ProviderExecutionContext();
        ctx.setProviderName("test-provider");
        ctx.setProviderApiKey(keys.isEmpty() ? null : keys.get(0).apiKey());
        ctx.setProviderBaseUrl("https://api.example.com");
        ctx.setProviderTimeoutSeconds(60);
        request.setExecutionContext(ctx);

        // 设置 metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("providerKeyEntries", keys);
        metadata.put("keySelectionStrategy", strategy);
        metadata.put("statsContext", new RequestStatsContext());
        request.setMetadata(metadata);

        return request;
    }

    /** 构建 429 限流异常 */
    private GatewayException rateLimitError() {
        return new GatewayException(ErrorCode.PROVIDER_RATE_LIMIT, "rate limited");
    }

    /** 构建非 429 异常 */
    private GatewayException authError() {
        return new GatewayException(ErrorCode.PROVIDER_AUTH_ERROR, "auth failed");
    }

    // ==================== 非流式降级重试 ====================

    @Nested
    @DisplayName("withKeyDegradedRetry — 非流式 Key 降级重试")
    class NonStreamKeyRetry {

        @Test
        @DisplayName("无多 Key metadata 时，不触发降级，直接调用")
        void noRetryWithoutMultiKeyMetadata() {
            UnifiedRequest request = new UnifiedRequest();
            request.setModel("test-model");
            UnifiedRequest.ProviderExecutionContext ctx = new UnifiedRequest.ProviderExecutionContext();
            ctx.setProviderName("test-provider");
            ctx.setProviderApiKey("sk-single");
            ctx.setProviderBaseUrl("https://api.example.com");
            ctx.setProviderTimeoutSeconds(60);
            request.setExecutionContext(ctx);
            request.setMetadata(new HashMap<>());

            StepVerifier.create(
                    client.withKeyDegradedRetry(request, config -> Mono.just("ok"))
            ).expectNext("ok").verifyComplete();
        }

        @Test
        @DisplayName("仅 1 个 Key 时，不触发降级")
        void noRetryWithSingleKey() {
            List<ProviderKeyEntry> singleKey = List.of(
                    new ProviderKeyEntry(1L, "sk-only", "sk-onl***nly", 100, 0)
            );
            UnifiedRequest request = buildRequestWithKeys(singleKey);

            // 即使抛 429，单 Key 不降级
            StepVerifier.create(
                    client.withKeyDegradedRetry(request, config -> Mono.error(rateLimitError()))
            ).expectError(GatewayException.class).verify();
        }

        @Test
        @DisplayName("429 时自动切换到其他 Key 并成功")
        void shouldDegradeOnRateLimit() {
            UnifiedRequest request = buildRequestWithKeys(TEST_KEYS);
            AtomicReference<String> usedApiKey = new AtomicReference<>();

            // 第一次调用 429，第二次成功
            StepVerifier.create(
                    client.withKeyDegradedRetry(request, config -> {
                        usedApiKey.set(config.apiKey());
                        if (config.apiKey().equals("sk-key-aaa")) {
                            return Mono.error(rateLimitError());
                        }
                        return Mono.just("success");
                    })
            ).expectNext("success").verifyComplete();

            // 最终使用的不是第一个 Key
            assertNotEquals("sk-key-aaa", usedApiKey.get());
        }

        @Test
        @DisplayName("非 429 错误不触发降级，直接失败")
        void noDegradeOnNonRateLimitError() {
            UnifiedRequest request = buildRequestWithKeys(TEST_KEYS);

            StepVerifier.create(
                    client.withKeyDegradedRetry(request, config -> Mono.error(authError()))
            ).expectErrorMatches(ex ->
                    ex instanceof GatewayException gwEx
                            && gwEx.getErrorCode() == ErrorCode.PROVIDER_AUTH_ERROR
            ).verify();
        }

        @Test
        @DisplayName("所有 Key 都 429 时，返回最后一次错误")
        void allKeysRateLimited() {
            // 2 个 Key 都返回 429
            List<ProviderKeyEntry> twoKeys = List.of(
                    new ProviderKeyEntry(1L, "sk-a", "sk-a***a", 100, 0),
                    new ProviderKeyEntry(2L, "sk-b", "sk-b***b", 100, 1)
            );
            UnifiedRequest request = buildRequestWithKeys(twoKeys);

            StepVerifier.create(
                    client.withKeyDegradedRetry(request, config -> Mono.error(rateLimitError()))
            ).expectErrorMatches(ex ->
                    ex instanceof GatewayException gwEx
                            && gwEx.getErrorCode() == ErrorCode.PROVIDER_RATE_LIMIT
            ).verify();
        }

        @Test
        @DisplayName("降级成功后 metadata 中的 usedApiKeyPrefix 已更新")
        void shouldUpdateMetadataOnDegradation() {
            UnifiedRequest request = buildRequestWithKeys(TEST_KEYS);

            StepVerifier.create(
                    client.withKeyDegradedRetry(request, config -> {
                        if (config.apiKey().equals("sk-key-aaa")) {
                            return Mono.error(rateLimitError());
                        }
                        return Mono.just("ok");
                    })
            ).expectNext("ok").verifyComplete();

            // 验证 metadata 被更新为降级后的 Key prefix
            String updatedPrefix = (String) request.getMetadata().get("usedApiKeyPrefix");
            assertNotNull(updatedPrefix);
            assertNotEquals("sk-key-a***aaa", updatedPrefix);
        }
    }

    // ==================== 流式降级重试 ====================

    @Nested
    @DisplayName("withStreamKeyDegradedRetry — 流式 Key 降级重试")
    class StreamKeyRetry {

        @Test
        @DisplayName("首 token 前遇到 429，触发降级重试")
        void shouldDegradeBeforeFirstToken() {
            UnifiedRequest request = buildRequestWithKeys(TEST_KEYS);
            AtomicBoolean firstTokenReceived = new AtomicBoolean(false);

            StepVerifier.create(
                    client.withStreamKeyDegradedRetry(
                            request,
                            config -> {
                                if (config.apiKey().equals("sk-key-aaa")) {
                                    return Flux.error(rateLimitError());
                                }
                                return Flux.just("event1", "event2");
                            },
                            firstTokenReceived
                    )
            ).expectNext("event1", "event2").verifyComplete();
        }

        @Test
        @DisplayName("首 token 后遇到 429，不触发降级")
        void noDegradeAfterFirstToken() {
            UnifiedRequest request = buildRequestWithKeys(TEST_KEYS);
            AtomicBoolean firstTokenReceived = new AtomicBoolean(false);

            // 模拟：先收到一个 token，然后 429
            StepVerifier.create(
                    client.withStreamKeyDegradedRetry(
                            request,
                            config -> Flux.concat(
                                    Flux.just("first-token").doOnNext(t -> firstTokenReceived.set(true)),
                                    Flux.error(rateLimitError())
                            ),
                            firstTokenReceived
                    )
            ).expectNext("first-token")
                    .expectErrorMatches(ex ->
                            ex instanceof GatewayException gwEx
                                    && gwEx.getErrorCode() == ErrorCode.PROVIDER_RATE_LIMIT
                    ).verify();
        }

        @Test
        @DisplayName("仅 1 个 Key 时流式不降级")
        void noStreamDegradeWithSingleKey() {
            List<ProviderKeyEntry> singleKey = List.of(
                    new ProviderKeyEntry(1L, "sk-only", "sk-onl***nly", 100, 0)
            );
            UnifiedRequest request = buildRequestWithKeys(singleKey);
            AtomicBoolean firstTokenReceived = new AtomicBoolean(false);

            StepVerifier.create(
                    client.withStreamKeyDegradedRetry(
                            request,
                            config -> Flux.error(rateLimitError()),
                            firstTokenReceived
                    )
            ).expectError(GatewayException.class).verify();
        }
    }

    // ==================== 可测试的具体子类 ====================

    /**
     * 最小化测试子类，暴露 withKeyDegradedRetry / withStreamKeyDegradedRetry 供测试调用
     */
    static class TestableProviderClient extends AbstractProviderClient {

        TestableProviderClient(ReactorClientHttpConnector httpConnector,
                              ObjectMapper objectMapper,
                              GatewayProperties gatewayProperties,
                              CircuitBreakerManager circuitBreakerManager) {
            super(httpConnector, objectMapper, gatewayProperties, circuitBreakerManager);
        }

        @Override
        public ProviderType getProviderType() {
            return ProviderType.OPENAI;
        }

        @Override
        public Mono<com.code.aigateway.sdk.model.UnifiedResponse> chat(UnifiedRequest request) {
            return Mono.empty();
        }

        @Override
        public Flux<com.code.aigateway.sdk.model.UnifiedStreamEvent> streamChat(UnifiedRequest request) {
            return Flux.empty();
        }

        /** 暴露 protected 方法给测试 */
        @Override
        public <T> Mono<T> withKeyDegradedRetry(UnifiedRequest request,
                                                 java.util.function.Function<ProviderRuntimeConfig, Mono<T>> callFunction) {
            return super.withKeyDegradedRetry(request, callFunction);
        }

        @Override
        public <T> Flux<T> withStreamKeyDegradedRetry(
                UnifiedRequest request,
                java.util.function.Function<ProviderRuntimeConfig, Flux<T>> callFunction,
                AtomicBoolean firstTokenReceived) {
            return super.withStreamKeyDegradedRetry(request, callFunction, firstTokenReceived);
        }
    }
}
