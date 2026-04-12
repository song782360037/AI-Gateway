package com.code.aigateway.core.auth;

import com.code.aigateway.admin.mapper.ApiKeyConfigMapper;
import com.code.aigateway.admin.model.dataobject.ApiKeyConfigDO;
import com.code.aigateway.config.GatewayProperties;
import com.code.aigateway.core.ratelimit.RateLimitService;
import com.code.aigateway.core.stats.RequestStatsCollector;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ApiKeyAuthWebFilter 单元测试
 * <p>
 * 覆盖场景：路径放行、认证开关、缺失/无效/过期/超限 Key、
 * Bearer 和 X-Api-Key 两种提取方式、Gemini 路径错误格式。
 * <p>
 * 注意：由于 filter 内部通过 Schedulers.boundedElastic() 执行阻塞 Mapper 调用，
 * 测试中需要 HookOn(Operators.ON_EACH_HOOK) 或使用 StepVerifier 的
 * StepVerifier.DefaultTimeoutSettings 来确保调度器可执行。
 * 为简化测试，我们在 @BeforeEach 中通过 Schedulers 工厂将 boundedElastic 替换为 immediate。
 * </p>
 */
class ApiKeyAuthWebFilterTest {

    private GatewayProperties gatewayProperties;
    private ApiKeyConfigMapper apiKeyConfigMapper;
    private ObjectMapper objectMapper;
    private ApiKeyAuthWebFilter filter;
    private WebFilterChain filterChain;

    // 测试用 API Key 明文和预期前缀
    private static final String RAW_KEY = "ak-testkey123456789";

    @BeforeEach
    void setUp() {
        gatewayProperties = new GatewayProperties();
        apiKeyConfigMapper = Mockito.mock(ApiKeyConfigMapper.class);
        objectMapper = new ObjectMapper();
        filterChain = Mockito.mock(WebFilterChain.class);

        // 默认启用认证
        GatewayProperties.AuthProperties auth = new GatewayProperties.AuthProperties();
        auth.setEnabled(true);
        gatewayProperties.setAuth(auth);

        // 默认 filterChain 放行
        when(filterChain.filter(any())).thenReturn(Mono.empty());

        filter = new ApiKeyAuthWebFilter(gatewayProperties, apiKeyConfigMapper, objectMapper,
                Mockito.mock(RateLimitService.class), Mockito.mock(RequestStatsCollector.class));
    }

    @AfterEach
    void tearDown() {
        // 清理资源
    }

    // ==================== 路径放行 ====================

    @Test
    void filter_nonGatewayPath_passesThrough() {
        // 非 /v1/ 和 /v1beta/ 路径应直接放行
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/admin/login").build()
        );

        Mono<Void> result = filter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();
        verify(filterChain).filter(exchange);
    }

    // ==================== 认证开关 ====================

    @Test
    void filter_authDisabled_passesThrough() {
        // auth.enabled=false 时应直接放行
        gatewayProperties.getAuth().setEnabled(false);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/v1/chat/completions").build()
        );

        Mono<Void> result = filter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();
        verify(filterChain).filter(exchange);
    }

    // ==================== 缺失 API Key ====================

    @Test
    void filter_missingApiKey_returns401() {
        // 不携带任何认证头，应返回 401
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/v1/chat/completions").build()
        );

        Mono<Void> result = filter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();
        assertResponseStatusCode(exchange, HttpStatus.UNAUTHORIZED);
        verify(filterChain, never()).filter(any());
    }

    // ==================== 无效 API Key ====================

    @Test
    void filter_invalidApiKey_returns401() {
        // Key 哈希在数据库中查不到，应返回 401
        when(apiKeyConfigMapper.selectByHash(anyString())).thenReturn(null);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/v1/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + RAW_KEY)
                        .build()
        );

        // block() 确保 Mono 完全执行完毕后再断言
        filter.filter(exchange, filterChain).block();

        assertResponseStatusCode(exchange, HttpStatus.UNAUTHORIZED);
        verify(filterChain, never()).filter(any());
    }

    // ==================== 已禁用 Key ====================

    @Test
    void filter_disabledKey_returns401() {
        // Key 存在但状态为 DISABLED，应返回 401
        when(apiKeyConfigMapper.selectByHash(anyString())).thenReturn(buildConfig("DISABLED", null, 0L, 100L));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/v1/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + RAW_KEY)
                        .build()
        );

        filter.filter(exchange, filterChain).block();

        assertResponseStatusCode(exchange, HttpStatus.UNAUTHORIZED);
        verify(filterChain, never()).filter(any());
    }

    // ==================== 已过期 Key ====================

    @Test
    void filter_expiredKey_returns401() {
        // Key 存在但已过期，应返回 401
        ApiKeyConfigDO config = buildConfig("ACTIVE", LocalDateTime.now().minusDays(1), 0L, 100L);
        when(apiKeyConfigMapper.selectByHash(anyString())).thenReturn(config);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/v1/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + RAW_KEY)
                        .build()
        );

        filter.filter(exchange, filterChain).block();

        assertResponseStatusCode(exchange, HttpStatus.UNAUTHORIZED);
        verify(filterChain, never()).filter(any());
    }

    // ==================== 使用次数超限 ====================

    @Test
    void filter_usageLimitExceeded_returns401() {
        // Key 的 usedCount >= totalLimit，应返回 401
        ApiKeyConfigDO config = buildConfig("ACTIVE", null, 100L, 100L);
        when(apiKeyConfigMapper.selectByHash(anyString())).thenReturn(config);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/v1/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + RAW_KEY)
                        .build()
        );

        filter.filter(exchange, filterChain).block();

        assertResponseStatusCode(exchange, HttpStatus.UNAUTHORIZED);
        verify(filterChain, never()).filter(any());
    }

    // ==================== 有效 Key 通过 ====================

    @Test
    void filter_validKey_passesThrough() {
        // Key 状态正常、未过期、未超限，应放行
        ApiKeyConfigDO config = buildConfig("ACTIVE", null, 10L, 100L);
        when(apiKeyConfigMapper.selectByHash(anyString())).thenReturn(config);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/v1/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + RAW_KEY)
                        .build()
        );

        filter.filter(exchange, filterChain).block();

        verify(filterChain).filter(exchange);
    }

    // ==================== Bearer Token 格式 ====================

    @Test
    void filter_bearerTokenFormat() {
        // 从 Authorization: Bearer ak-xxx 提取 Key
        ApiKeyConfigDO config = buildConfig("ACTIVE", null, 0L, 100L);
        when(apiKeyConfigMapper.selectByHash(anyString())).thenReturn(config);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/v1/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + RAW_KEY)
                        .build()
        );

        filter.filter(exchange, filterChain).block();

        verify(filterChain).filter(exchange);
    }

    // ==================== X-Api-Key 头 ====================

    @Test
    void filter_xApiKeyHeader() {
        // 从 X-Api-Key: ak-xxx 提取 Key
        ApiKeyConfigDO config = buildConfig("ACTIVE", null, 0L, 100L);
        when(apiKeyConfigMapper.selectByHash(anyString())).thenReturn(config);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/v1/chat/completions")
                        .header("X-Api-Key", RAW_KEY)
                        .build()
        );

        filter.filter(exchange, filterChain).block();

        verify(filterChain).filter(exchange);
    }

    // ==================== Gemini 路径返回 Gemini 格式错误 ====================

    @Test
    void filter_geminiPath_returnsGeminiError() {
        // /v1beta/ 路径缺失 Key 时，应返回 Gemini 格式错误
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/v1beta/models/gemini-pro:generateContent").build()
        );

        Mono<Void> result = filter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();
        assertResponseStatusCode(exchange, HttpStatus.UNAUTHORIZED);

        // 验证响应体包含 Gemini 格式的错误字段
        String body = exchange.getResponse().getBodyAsString().block();
        assertNotNull(body);
        // Gemini 格式：外层 error 包含 code、message、status 字段
        assertTrue(body.contains("UNAUTHENTICATED"), "Gemini 错误应包含 UNAUTHENTICATED 状态");
        verify(filterChain, never()).filter(any());
    }

    // ==================== 辅助方法 ====================

    /** 断言响应状态码 */
    private void assertResponseStatusCode(MockServerWebExchange exchange, HttpStatus expected) {
        HttpStatusCode actual = exchange.getResponse().getStatusCode();
        assertNotNull(actual, "响应状态码不应为 null");
        assertEquals(expected.value(), actual.value());
    }

    /** 构造测试用 ApiKeyConfigDO */
    private ApiKeyConfigDO buildConfig(String status, LocalDateTime expireTime,
                                       Long usedCount, Long totalLimit) {
        ApiKeyConfigDO config = new ApiKeyConfigDO();
        config.setId(1L);
        config.setKeyHash("dummy-hash");
        config.setKeyPrefix("ak-test");
        config.setName("测试 Key");
        config.setStatus(status);
        config.setUsedCount(usedCount);
        config.setTotalLimit(totalLimit);
        config.setExpireTime(expireTime);
        return config;
    }
}
