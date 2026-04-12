package com.code.aigateway.core.auth;

import com.code.aigateway.admin.mapper.ApiKeyConfigMapper;
import com.code.aigateway.admin.model.dataobject.ApiKeyConfigDO;
import com.code.aigateway.api.response.AnthropicErrorResponse;
import com.code.aigateway.api.response.GeminiErrorResponse;
import com.code.aigateway.api.response.OpenAiErrorResponse;
import com.code.aigateway.config.GatewayProperties;
import com.code.aigateway.core.model.ResponseProtocol;
import com.code.aigateway.core.protocol.ProtocolResolver;
import com.code.aigateway.core.ratelimit.RateLimitResult;
import com.code.aigateway.core.ratelimit.RateLimitService;
import com.code.aigateway.core.stats.RequestStatsCollector;
import com.code.aigateway.core.stats.RequestStatsContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * API Key 认证 WebFilter（含限流）
 *
 * <p>拦截 /v1/** 和 /v1beta/** 路径，校验请求携带的 API Key，
 * 鉴权通过后检查限流策略。
 * <ul>
 *   <li>优先从 Authorization: Bearer ak-xxx 提取</li>
 *   <li>回退从 X-Api-Key: ak-xxx 提取</li>
 *   <li>gateway.auth.enabled = false 时放行所有请求</li>
 * </ul>
 * 原始 key 经 SHA-256 哈希后查库，明文永不落库。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class ApiKeyAuthWebFilter implements WebFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String X_API_KEY = "X-Api-Key";

    private final GatewayProperties gatewayProperties;
    private final ApiKeyConfigMapper apiKeyConfigMapper;
    private final ObjectMapper objectMapper;
    private final RateLimitService rateLimitService;
    private final RequestStatsCollector requestStatsCollector;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 拦截 /v1/** 和 /v1beta/** 路径
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/v1/") && !path.startsWith("/v1beta/")) {
            return chain.filter(exchange);
        }

        RequestStatsContext context = exchange.getAttribute(RequestStatsContext.ATTRIBUTE_KEY);
        if (context != null) {
            context.setTerminalStage("AUTH");
        }

        // auth.enabled = false 时直接放行
        if (!isAuthEnabled()) {
            if (context != null) {
                context.setAuthStatus("DISABLED");
            }
            return chain.filter(exchange);
        }

        // 提取 API Key
        String rawKey = extractApiKey(exchange);
        if (rawKey == null) {
            if (context != null) {
                context.setAuthStatus("FAILED");
            }
            return writeAuthError(exchange, path, "Missing API key");
        }

        // SHA-256 哈希后查库校验（阻塞操作切线程）
        String keyHash = sha256Hex(rawKey);
        return Mono.fromCallable(() -> java.util.Optional.ofNullable(apiKeyConfigMapper.selectByHash(keyHash)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optConfig -> {
                    ApiKeyConfigDO config = optConfig.orElse(null);
                    if (config == null) {
                        if (context != null) {
                            context.setAuthStatus("FAILED");
                        }
                        return writeAuthError(exchange, path, "Invalid API key");
                    }
                    if (context != null) {
                        context.setApiKeyConfigId(config.getId());
                        context.setApiKeyPrefix(config.getKeyPrefix());
                    }
                    String validationError = validateConfig(config);
                    if (validationError != null) {
                        if (context != null) {
                            context.setAuthStatus("FAILED");
                        }
                        return writeAuthError(exchange, path, validationError);
                    }
                    if (context != null) {
                        context.setAuthStatus("PASSED");
                    }

                    // 鉴权通过，检查限流
                    return checkRateLimitAndContinue(exchange, chain, path, config, context);
                });
    }

    /**
     * 限流检查 + 通过后继续过滤器链
     */
    private Mono<Void> checkRateLimitAndContinue(ServerWebExchange exchange, WebFilterChain chain,
                                                  String path, ApiKeyConfigDO config, RequestStatsContext context) {
        GatewayProperties.RateLimitProperties rlProps = gatewayProperties.getRateLimit();

        // 限流未启用或 Redis 不可用时直接放行
        if (rlProps == null || !rlProps.isEnabled()) {
            if (context != null) {
                context.setRateLimitTriggered(Boolean.FALSE);
            }
            return incrementAndContinue(exchange, chain, config);
        }

        // 读取 API Key 级别的限流配置，未配置则使用全局默认值
        int rpmLimit = config.getRpmLimit() != null ? config.getRpmLimit() : rlProps.getDefaultRpm();
        int hourlyLimit = config.getHourlyLimit() != null ? config.getHourlyLimit() : rlProps.getDefaultHourlyRpm();

        return rateLimitService.checkRateLimit(config.getKeyHash(), rpmLimit, hourlyLimit)
                .flatMap(result -> {
                    // 设置限流响应头（无论是否超限）
                    ServerHttpResponse response = exchange.getResponse();
                    response.getHeaders().set("X-RateLimit-Limit", String.valueOf(result.limit()));
                    response.getHeaders().set("X-RateLimit-Remaining", String.valueOf(Math.max(0, result.remaining())));
                    response.getHeaders().set("X-RateLimit-Reset", String.valueOf(result.resetAtEpochSeconds()));

                    if (!result.allowed()) {
                        log.warn("[API Key限流] keyPrefix={}, 已超限 limit={}, remaining={}",
                                config.getKeyPrefix(), result.limit(), result.remaining());
                        if (context != null) {
                            context.setRateLimitTriggered(Boolean.TRUE);
                            context.setRateLimitReason("limit=" + result.limit() + ", remaining=" + result.remaining());
                            context.setTerminalStage("RATE_LIMIT");
                        }
                        return writeRateLimitError(exchange, path, result);
                    }

                    if (context != null) {
                        context.setRateLimitTriggered(Boolean.FALSE);
                    }
                    return incrementAndContinue(exchange, chain, config);
                });
    }

    /** 递增使用计数并继续过滤器链 */
    private Mono<Void> incrementAndContinue(ServerWebExchange exchange, WebFilterChain chain, ApiKeyConfigDO config) {
        exchange.getResponse().beforeCommit(() ->
                Mono.fromRunnable(() -> apiKeyConfigMapper.incrementUsedCount(config.getId()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .then()
        );
        return chain.filter(exchange);
    }

    /** 检查 auth 开关 */
    private boolean isAuthEnabled() {
        return gatewayProperties.getAuth() != null && gatewayProperties.getAuth().isEnabled();
    }

    /** 提取 API Key，优先 Authorization: Bearer，回退 X-Api-Key */
    private String extractApiKey(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            if (token.startsWith("ak-")) {
                return token;
            }
        }

        String apiKey = exchange.getRequest().getHeaders().getFirst(X_API_KEY);
        if (apiKey != null && apiKey.startsWith("ak-")) {
            return apiKey.trim();
        }
        return null;
    }

    /** 校验 key 配置状态，返回 null 表示通过 */
    private String validateConfig(ApiKeyConfigDO config) {
        if (!"ACTIVE".equals(config.getStatus())) {
            return "API key is disabled";
        }
        if (config.getExpireTime() != null && config.getExpireTime().isBefore(LocalDateTime.now())) {
            return "API key has expired";
        }
        if (config.getTotalLimit() != null && config.getUsedCount() >= config.getTotalLimit()) {
            return "API key usage limit exceeded";
        }
        return null;
    }

    /** 根据路径推断协议类型，返回对应格式的 401 错误 */
    private Mono<Void> writeAuthError(ServerWebExchange exchange, String path, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        ResponseProtocol protocol = ProtocolResolver.fromPath(path);
        RequestStatsContext context = exchange.getAttribute(RequestStatsContext.ATTRIBUTE_KEY);
        if (context != null) {
            context.setResponseProtocol(protocol);
        }
        byte[] bytes = toJsonBytes(buildProtocolError(protocol, message));
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)))
                .doOnSuccess(unused -> requestStatsCollector.collectRejected(context, "AUTH_FAILED", message));
    }

    /** 返回 429 Too Many Requests，携带限流状态信息 */
    private Mono<Void> writeRateLimitError(ServerWebExchange exchange, String path, RateLimitResult result) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(result.resetAtEpochSeconds()));

        ResponseProtocol protocol = ProtocolResolver.fromPath(path);
        RequestStatsContext context = exchange.getAttribute(RequestStatsContext.ATTRIBUTE_KEY);
        if (context != null) {
            context.setResponseProtocol(protocol);
        }
        Object errorBody = buildRateLimitError(protocol, result);
        byte[] bytes = toJsonBytes(errorBody);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)))
                .doOnSuccess(unused -> requestStatsCollector.collectRejected(context, "RATE_LIMITED",
                        "Rate limit exceeded. Limit: " + result.limit() + ", remaining: " + result.remaining()));
    }

    /** 按协议构建限流错误响应 */
    private Object buildRateLimitError(ResponseProtocol protocol, RateLimitResult result) {
        String message = "Rate limit exceeded. Limit: " + result.limit() + ", remaining: " + result.remaining();
        return switch (protocol) {
            case ANTHROPIC -> new AnthropicErrorResponse(
                    "error", new AnthropicErrorResponse.ErrorDetail("rate_limit_error", message)
            );
            case GEMINI -> new GeminiErrorResponse(
                    new GeminiErrorResponse.ErrorDetail(429, message, "RESOURCE_EXHAUSTED")
            );
            default -> new OpenAiErrorResponse(
                    new OpenAiErrorResponse.Error(message, "rate_limit_error", "RATE_LIMITED", null)
            );
        };
    }

    /** 按协议构建认证错误响应体 */
    private Object buildProtocolError(ResponseProtocol protocol, String message) {
        return switch (protocol) {
            case ANTHROPIC -> new AnthropicErrorResponse(
                    "error", new AnthropicErrorResponse.ErrorDetail("authentication_error", message)
            );
            case GEMINI -> new GeminiErrorResponse(
                    new GeminiErrorResponse.ErrorDetail(401, message, "UNAUTHENTICATED")
            );
            default -> new OpenAiErrorResponse(
                    new OpenAiErrorResponse.Error(message, "authentication_error", "AUTH_FAILED", null)
            );
        };
    }

    /** SHA-256 哈希转 hex */
    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (Exception e) {
            log.error("[API Key认证] SHA-256 哈希计算失败", e);
            throw new RuntimeException("Hash computation failed", e);
        }
    }

    private byte[] toJsonBytes(Object obj) {
        try {
            return objectMapper.writeValueAsBytes(obj);
        } catch (JsonProcessingException e) {
            return "{\"error\":{\"message\":\"Unauthorized\",\"type\":\"authentication_error\",\"code\":\"AUTH_FAILED\"}}"
                    .getBytes(StandardCharsets.UTF_8);
        }
    }
}
