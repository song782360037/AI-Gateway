package com.code.aigateway.provider;

import com.code.aigateway.config.GatewayProperties;
import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedUsage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provider 抽象基类
 * <p>
 * 提取所有 provider 共用的运行时配置解析、重试策略、错误映射等逻辑，
 * 子类只需关注请求构建和响应解析。
 * </p>
 *
 * @author sst
 */
@Slf4j
public abstract class AbstractProviderClient implements ProviderClient {

    protected final WebClient.Builder webClientBuilder;
    protected final ObjectMapper objectMapper;
    protected final GatewayProperties gatewayProperties;

    protected AbstractProviderClient(WebClient.Builder webClientBuilder,
                                     ObjectMapper objectMapper,
                                     GatewayProperties gatewayProperties) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
        this.gatewayProperties = gatewayProperties;
    }

    // ==================== 运行时配置 ====================

    /**
     * 解析 provider 运行时配置。
     * 优先从 executionContext 获取参数（由路由层写入），回退到 YAML 静态配置。
     */
    protected ProviderRuntimeConfig resolveRuntimeConfig(UnifiedRequest request) {
        UnifiedRequest.ProviderExecutionContext ctx = request.getExecutionContext();
        String providerName = ctx != null && ctx.getProviderName() != null
                ? ctx.getProviderName()
                : request.getProvider();
        if (providerName == null || providerName.isBlank()) {
            throw new GatewayException(ErrorCode.PROVIDER_NOT_FOUND, "provider name is missing");
        }

        GatewayProperties.ProviderProperties props = gatewayProperties.getProviders() == null
                ? null
                : gatewayProperties.getProviders().get(providerName);

        String apiKey = ctx != null && ctx.getProviderApiKey() != null
                ? ctx.getProviderApiKey()
                : (props != null ? props.getApiKey() : null);
        if (apiKey == null || apiKey.isBlank()) {
            throw new GatewayException(ErrorCode.PROVIDER_ERROR, "provider api key is missing: " + providerName);
        }

        String baseUrl = ctx != null && ctx.getProviderBaseUrl() != null
                ? ctx.getProviderBaseUrl()
                : (props != null ? props.getBaseUrl() : null);
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new GatewayException(ErrorCode.PROVIDER_ERROR, "provider base url is missing: " + providerName);
        }

        Integer timeoutSeconds = ctx != null && ctx.getProviderTimeoutSeconds() != null
                ? ctx.getProviderTimeoutSeconds()
                : (props != null ? props.getTimeoutSeconds() : null);
        if (timeoutSeconds == null || timeoutSeconds <= 0) {
            timeoutSeconds = 60;
        }

        GatewayProperties.RetryProperties retryProps = gatewayProperties.getRetry();
        int maxRetries = retryProps != null ? retryProps.getMaxRetries() : 0;
        long initialIntervalMs = retryProps != null ? retryProps.getInitialIntervalMs() : 1000;
        long maxIntervalMs = retryProps != null ? retryProps.getMaxIntervalMs() : 30000;

        return new ProviderRuntimeConfig(
                providerName, trimTrailingSlash(baseUrl), apiKey, timeoutSeconds,
                maxRetries, initialIntervalMs, maxIntervalMs
        );
    }

    // ==================== WebClient 构建 ====================

    /**
     * 构建 WebClient，默认使用 Bearer Token 认证。
     * 子类可覆盖以自定义认证方式（如 Anthropic 的 x-api-key header）。
     */
    protected WebClient buildWebClient(ProviderRuntimeConfig config) {
        return webClientBuilder
                .baseUrl(config.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey())
                .build();
    }

    // ==================== 重试策略 ====================

    /**
     * 构建指数退避重试策略（非流式请求）
     */
    protected Retry buildRetrySpec(ProviderRuntimeConfig config) {
        return Retry.backoff(config.maxRetries(), Duration.ofMillis(config.initialIntervalMs()))
                .maxBackoff(Duration.ofMillis(config.maxIntervalMs()))
                .filter(this::isRetryableError)
                .doBeforeRetry(signal -> log.warn(
                        "[Provider重试] 提供商: {}, 第{}次重试(共{}次), 失败原因: {}",
                        config.providerName(),
                        signal.totalRetries() + 1,
                        config.maxRetries(),
                        signal.failure().getMessage()
                ));
    }

    /**
     * 构建流式请求重试策略（仅首 token 前可重试）
     * <p>
     * 首个 token 到达后不再重试，避免向客户端重复输出。
     * </p>
     */
    protected Retry buildStreamRetrySpec(ProviderRuntimeConfig config, AtomicBoolean firstTokenReceived) {
        return Retry.backoff(config.maxRetries(), Duration.ofMillis(config.initialIntervalMs()))
                .maxBackoff(Duration.ofMillis(config.maxIntervalMs()))
                .filter(error -> !firstTokenReceived.get() && isRetryableError(error))
                .doBeforeRetry(signal -> log.warn(
                        "[Provider流式重试] 提供商: {}, 第{}次重试(共{}次), 首 token 前失败, 原因: {}",
                        config.providerName(),
                        signal.totalRetries() + 1,
                        config.maxRetries(),
                        signal.failure().getMessage()
                ));
    }

    // ==================== 错误处理 ====================

    /**
     * 判断异常是否可重试（5xx、超时、连接异常）
     */
    protected boolean isRetryableError(Throwable throwable) {
        if (throwable instanceof ConnectException) {
            return true;
        }
        if (throwable instanceof TimeoutException) {
            return true;
        }
        if (throwable instanceof WebClientRequestException) {
            Throwable cause = throwable.getCause();
            while (cause != null) {
                if (isRetryableError(cause)) {
                    return true;
                }
                cause = cause.getCause();
            }
            return false;
        }
        if (throwable instanceof GatewayException gatewayEx) {
            return gatewayEx.getErrorCode() == ErrorCode.PROVIDER_TIMEOUT
                    || gatewayEx.getErrorCode() == ErrorCode.PROVIDER_SERVER_ERROR;
        }
        return false;
    }

    /**
     * 将底层传输异常映射为统一异常。
     * 重试耗尽时 reactor 会包装原始异常，需要逐层解包。
     */
    protected Throwable mapTransportError(Throwable throwable) {
        Throwable cause = throwable;
        int depth = 0;
        while (cause.getCause() != null && depth < 10) {
            if (cause.getCause() instanceof GatewayException) {
                return cause.getCause();
            }
            if (cause.getCause() instanceof java.util.concurrent.TimeoutException) {
                return new GatewayException(ErrorCode.PROVIDER_TIMEOUT, "provider request timeout");
            }
            cause = cause.getCause();
            depth++;
        }
        if (throwable instanceof GatewayException) {
            return throwable;
        }
        if (throwable instanceof java.util.concurrent.TimeoutException) {
            return new GatewayException(ErrorCode.PROVIDER_TIMEOUT, "provider request timeout");
        }
        return new GatewayException(ErrorCode.PROVIDER_ERROR, "provider request failed");
    }

    /**
     * 将上游错误 HTTP 响应映射为统一异常。
     * 子类可覆盖以处理不同的错误响应格式。
     */
    protected Mono<? extends Throwable> mapErrorResponse(ClientResponse response, ProviderRuntimeConfig config) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> {
                    String message = extractErrorMessage(body);
                    if (response.statusCode().value() == 429) {
                        return new GatewayException(ErrorCode.PROVIDER_RATE_LIMIT,
                                message.isBlank() ? "provider rate limited" : message);
                    }
                    if (response.statusCode().is5xxServerError()) {
                        return new GatewayException(ErrorCode.PROVIDER_SERVER_ERROR,
                                message.isBlank() ? "provider server error" : message);
                    }
                    return new GatewayException(ErrorCode.PROVIDER_ERROR,
                            message.isBlank() ? "provider request failed" : message);
                });
    }

    /**
     * 提取上游错误消息，默认解析 OpenAI 格式 {"error":{"message":"..."}}。
     * 子类可覆盖以适配不同的错误响应格式。
     */
    protected String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode jsonNode = objectMapper.readTree(body);
            JsonNode messageNode = jsonNode.path("error").path("message");
            if (!messageNode.isMissingNode() && !messageNode.isNull()) {
                return messageNode.asText();
            }
        } catch (JsonProcessingException ignored) {
            // 非 JSON 响应体，不泄露上游内部细节
        }
        return "";
    }

    // ==================== 工具方法 ====================

    /**
     * 解析 usage（OpenAI 格式：prompt_tokens / completion_tokens / total_tokens）
     */
    protected UnifiedUsage parseUsage(JsonNode usageNode) {
        if (usageNode == null || usageNode.isNull() || usageNode.isMissingNode()) {
            return null;
        }
        UnifiedUsage usage = new UnifiedUsage();
        usage.setInputTokens(usageNode.path("prompt_tokens").isMissingNode() ? null : usageNode.path("prompt_tokens").asInt());
        usage.setOutputTokens(usageNode.path("completion_tokens").isMissingNode() ? null : usageNode.path("completion_tokens").asInt());
        usage.setTotalTokens(usageNode.path("total_tokens").isMissingNode() ? null : usageNode.path("total_tokens").asInt());
        return usage;
    }

    /**
     * 去除 baseUrl 尾部斜杠
     */
    protected String trimTrailingSlash(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    /**
     * 读取 JSON 节点文本值，null/missing 返回 null
     */
    protected String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return node.asText();
    }

    // ==================== 内部配置记录 ====================

    /**
     * Provider 运行时配置（不可变）
     */
    protected record ProviderRuntimeConfig(
            String providerName,
            String baseUrl,
            String apiKey,
            Integer timeoutSeconds,
            int maxRetries,
            long initialIntervalMs,
            long maxIntervalMs
    ) {
    }
}
