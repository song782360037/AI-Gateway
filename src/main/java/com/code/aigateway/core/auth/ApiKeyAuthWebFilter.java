package com.code.aigateway.core.auth;

import com.code.aigateway.admin.mapper.ApiKeyConfigMapper;
import com.code.aigateway.admin.model.dataobject.ApiKeyConfigDO;
import com.code.aigateway.api.response.OpenAiErrorResponse;
import com.code.aigateway.config.GatewayProperties;
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
 * API Key 认证 WebFilter
 *
 * <p>拦截 /v1/** 路径，校验请求携带的 API Key。
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

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 仅拦截 /v1/** 路径
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/v1/")) {
            return chain.filter(exchange);
        }

        // auth.enabled = false 时直接放行
        if (!isAuthEnabled()) {
            return chain.filter(exchange);
        }

        // 提取 API Key
        String rawKey = extractApiKey(exchange);
        if (rawKey == null) {
            return writeAuthError(exchange, "Missing API key");
        }

        // SHA-256 哈希后查库校验（阻塞操作切线程）
        String keyHash = sha256Hex(rawKey);
        return Mono.fromCallable(() -> apiKeyConfigMapper.selectByHash(keyHash))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(config -> {
                    if (config == null) {
                        return writeAuthError(exchange, "Invalid API key");
                    }
                    String validationError = validateConfig(config);
                    if (validationError != null) {
                        return writeAuthError(exchange, validationError);
                    }

                    // 鉴权通过，响应完成时递增使用计数
                    exchange.getResponse().beforeCommit(() ->
                            Mono.fromRunnable(() -> apiKeyConfigMapper.incrementUsedCount(config.getId()))
                                    .subscribeOn(Schedulers.boundedElastic())
                                    .then()
                    );
                    return chain.filter(exchange);
                });
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

    /** 返回 OpenAI 格式的 401 错误 */
    private Mono<Void> writeAuthError(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        OpenAiErrorResponse body = new OpenAiErrorResponse(
                new OpenAiErrorResponse.Error(message, "authentication_error", "AUTH_FAILED", null)
        );
        byte[] bytes = toJsonBytes(body);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
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
