package com.code.aigateway.provider;

import com.code.aigateway.config.GatewayProperties;
import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedUsage;
import com.code.aigateway.core.resilience.CircuitBreakerManager;
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
    protected final CircuitBreakerManager circuitBreakerManager;

    protected AbstractProviderClient(WebClient.Builder webClientBuilder,
                                     ObjectMapper objectMapper,
                                     GatewayProperties gatewayProperties,
                                     CircuitBreakerManager circuitBreakerManager) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
        this.gatewayProperties = gatewayProperties;
        this.circuitBreakerManager = circuitBreakerManager;
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
     *
     * @param config        运行时配置
     * @param correlationId 请求链路追踪 ID，透传至下游 Provider
     */
    protected WebClient buildWebClient(ProviderRuntimeConfig config, String correlationId) {
        WebClient.Builder builder = webClientBuilder
                .baseUrl(config.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey());
        if (correlationId != null && !correlationId.isBlank()) {
            builder.defaultHeader("X-Correlation-Id", correlationId);
        }
        return builder.build();
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
     * 提取完整错误上下文（HTTP 状态码、错误类型、错误描述）并记录日志。
     * 子类可覆盖 extractErrorType / extractErrorMessage 以适配不同的错误响应格式。
     */
    protected Mono<? extends Throwable> mapErrorResponse(ClientResponse response, ProviderRuntimeConfig config) {
        HttpStatusCode statusCode = response.statusCode();
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> {
                    String errorMessage = extractErrorMessage(body);
                    String errorType = extractErrorType(body);

                    // 记录上游完整错误上下文，便于排查
                    log.warn("[上游错误] 提供商: {}, HTTP状态: {}, 错误类型: {}, 错误描述: {}, 原始响应: {}",
                            config.providerName(), statusCode.value(),
                            errorType.isBlank() ? "N/A" : errorType,
                            errorMessage.isBlank() ? "N/A" : errorMessage,
                            truncateForLog(body, 500));

                    ErrorCode errorCode;
                    if (statusCode.value() == 429) {
                        errorCode = ErrorCode.PROVIDER_RATE_LIMIT;
                    } else if (statusCode.is5xxServerError()) {
                        errorCode = ErrorCode.PROVIDER_SERVER_ERROR;
                    } else {
                        errorCode = ErrorCode.PROVIDER_ERROR;
                    }

                    String message = errorMessage.isBlank()
                            ? errorCode.name().toLowerCase().replace('_', ' ')
                            : errorMessage;

                    return new GatewayException(errorCode, message, null,
                            statusCode.value(), errorType);
                });
    }

    /**
     * 从上游错误响应体中提取错误消息。
     * 默认解析 OpenAI 格式 {"error":{"message":"..."}}，子类可覆盖。
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
        }
        return "";
    }

    /**
     * 从上游错误响应体中提取错误类型。
     * 默认解析 OpenAI 格式 {"error":{"type":"..."}}，子类可覆盖。
     */
    protected String extractErrorType(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode jsonNode = objectMapper.readTree(body);
            JsonNode typeNode = jsonNode.path("error").path("type");
            if (!typeNode.isMissingNode() && !typeNode.isNull()) {
                return typeNode.asText();
            }
        } catch (JsonProcessingException ignored) {
        }
        return "";
    }

    /**
     * 截断长文本用于日志输出，避免日志过长
     */
    private String truncateForLog(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...(truncated)";
    }

    // ==================== 工具方法 ====================

    /**
     * 解析 usage，兼容 OpenAI Chat / Responses 两种字段命名。
     * <p>
     * 兼容字段：
     * - 输入：prompt_tokens / input_tokens
     * - 输出：completion_tokens / output_tokens
     * - 总量：total_tokens（缺失时自动按输入+输出补算）
     * </p>
     */
    protected UnifiedUsage parseUsage(JsonNode usageNode) {
        if (usageNode == null || usageNode.isNull() || usageNode.isMissingNode()) {
            return null;
        }
        UnifiedUsage usage = new UnifiedUsage();

        Integer inputTokens = readIntField(usageNode, "prompt_tokens", "input_tokens");
        Integer outputTokens = readIntField(usageNode, "completion_tokens", "output_tokens");
        Integer totalTokens = readIntField(usageNode, "total_tokens");

        usage.setInputTokens(inputTokens);
        usage.setOutputTokens(outputTokens);
        if (totalTokens != null) {
            usage.setTotalTokens(totalTokens);
        } else if (inputTokens != null && outputTokens != null) {
            usage.setTotalTokens(inputTokens + outputTokens);
        }
        return usage;
    }

    /**
     * 从多个候选字段中读取第一个存在的整数值。
     */
    protected Integer readIntField(JsonNode node, String... fieldNames) {
        if (node == null || node.isNull() || node.isMissingNode() || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode field = node.path(fieldName);
            if (!field.isMissingNode() && !field.isNull()) {
                return field.asInt();
            }
        }
        return null;
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
     * 从统一请求中提取 correlationId
     */
    protected String extractCorrelationId(UnifiedRequest request) {
        return request.getExecutionContext() != null
                ? request.getExecutionContext().getCorrelationId()
                : null;
    }

    /**
     * 使用熔断器包裹 Mono 调用。
     * 熔断维度为 provider+model，避免单个模型失败影响同 Provider 下其他模型。
     *
     * @param providerCode provider 编码
     * @param model        目标模型名
     * @param mono         原始调用
     */
    protected <T> Mono<T> withCircuitBreaker(String providerCode, String model, Mono<T> mono) {
        return mono.transformDeferred(io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator.of(
                circuitBreakerManager.getOrCreate(providerCode, model)));
    }

    /**
     * 使用熔断器包裹 Flux 流式调用。
     */
    protected <T> reactor.core.publisher.Flux<T> withCircuitBreakerFlux(
            String providerCode, String model, reactor.core.publisher.Flux<T> flux) {
        return flux.transformDeferred(io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator.of(
                circuitBreakerManager.getOrCreate(providerCode, model)));
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
