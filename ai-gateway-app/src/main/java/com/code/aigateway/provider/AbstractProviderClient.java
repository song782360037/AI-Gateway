package com.code.aigateway.provider;

import com.code.aigateway.common.util.CustomHeaderUtils;
import com.code.aigateway.config.GatewayProperties;
import com.code.aigateway.sdk.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.GatewayMetadataKeys;
import com.code.aigateway.core.resilience.CircuitBreakerManager;
import com.code.aigateway.core.router.ProviderKeyEntry;
import com.code.aigateway.core.router.KeySelectionStrategy;
import com.code.aigateway.core.stats.RequestStatsContext;
import com.code.aigateway.sdk.model.UnifiedMessage;
import com.code.aigateway.sdk.model.UnifiedPart;
import com.code.aigateway.sdk.model.UnifiedRequest;
import com.code.aigateway.sdk.model.UnifiedUsage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.ConnectException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** metadata key：thinking 参数兼容模式（full / simplified），由路由层写入 */
    public static final String META_THINKING_COMPAT_MODE = "thinkingCompatMode";

    protected final ReactorClientHttpConnector httpConnector;
    protected final ObjectMapper objectMapper;
    protected final GatewayProperties gatewayProperties;
    protected final CircuitBreakerManager circuitBreakerManager;

    protected AbstractProviderClient(ReactorClientHttpConnector httpConnector,
                                     ObjectMapper objectMapper,
                                     GatewayProperties gatewayProperties,
                                     CircuitBreakerManager circuitBreakerManager) {
        this.httpConnector = httpConnector;
        this.objectMapper = objectMapper;
        this.gatewayProperties = gatewayProperties;
        this.circuitBreakerManager = circuitBreakerManager;
    }

    // ==================== 运行时配置 ====================

    /**
     * 解析 provider 运行时配置。
     * 优先从 executionContext 获取参数（由路由层写入），回退到 YAML 静态配置。
     * <p>
     * 注意：providerName 必须来自 executionContext 中的 providerName（即 providerCode），
     * 而非 request.getProvider()（即 providerType）。两者语义不同：
     * providerCode 是提供商实例的唯一标识（如 "openai-main"），
     * providerType 是提供商类型（如 "openai"），同类型可能有多个实例。
     * 若误用 providerType 作为熔断器键，同类型多实例将共享熔断状态，导致误判。
     * </p>
     */
    protected ProviderRuntimeConfig resolveRuntimeConfig(UnifiedRequest request) {
        UnifiedRequest.ProviderExecutionContext ctx = request.getExecutionContext();
        String providerName = ctx != null && ctx.getProviderName() != null
                ? ctx.getProviderName()
                : request.getProvider();
        if (providerName == null || providerName.isBlank()) {
            throw new GatewayException(ErrorCode.PROVIDER_NOT_FOUND, "provider name is missing");
        }

        // 防护性检查：当回退到 request.getProvider()（即 providerType）时，
        // 若存在运行时快照且该类型有多个实例，则拒绝执行，避免熔断器键歧义
        if (ctx == null || ctx.getProviderName() == null) {
            log.warn("[运行时配置] executionContext.providerName 为空，回退使用 providerType: {}，"
                    + "若同类型存在多个提供商实例，可能导致熔断器键冲突", providerName);
        }

        GatewayProperties.ProviderProperties props = gatewayProperties.getProviders() == null
                ? null
                : gatewayProperties.getProviders().get(providerName);

        String apiKey = ctx != null && ctx.getProviderApiKey() != null
                ? ctx.getProviderApiKey()
                : (props != null ? props.getApiKey() : null);
        if (apiKey == null || apiKey.isBlank()) {
            throw new GatewayException(ErrorCode.PROVIDER_NOT_FOUND,
                    "provider " + providerName + " 无可用 API Key，请检查该 Provider 是否已添加并启用 Key");
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
                maxRetries, initialIntervalMs, maxIntervalMs,
                resolveCustomHeaders(request)
        );
    }

    /**
     * 解析 provider 运行时配置，并用 apiKeyOverride 替换原始 apiKey。
     * 用于 Key 降级重试场景，避免修改 request 对象。
     */
    protected ProviderRuntimeConfig resolveRuntimeConfig(UnifiedRequest request, String apiKeyOverride) {
        ProviderRuntimeConfig config = resolveRuntimeConfig(request);
        if (apiKeyOverride != null) {
            return new ProviderRuntimeConfig(
                    config.providerName(), config.baseUrl(), apiKeyOverride,
                    config.timeoutSeconds(), config.maxRetries(),
                    config.initialIntervalMs(), config.maxIntervalMs(),
                    config.customHeaders());
        }
        return config;
    }

    // ==================== WebClient 构建 ====================

    /**
     * 构建 WebClient，默认使用 Bearer Token 认证。
     * 子类可覆盖以自定义认证方式（如 Anthropic 的 x-api-key header）。
     * <p>
     * 自定义请求头在认证头之前设置，认证头后设置可确保同名头不被覆盖（defaultHeader 为追加语义）。
     * 子类覆盖此方法时，应按相同顺序：先调用 CustomHeaderUtils.applyCustomHeaders 设置自定义头，
     * 再设置认证相关头，确保认证头不可被自定义头覆盖。
     * </p>
     *
     * @param config        运行时配置
     * @param correlationId 请求链路追踪 ID，透传至下游 Provider
     */
    protected WebClient buildWebClient(ProviderRuntimeConfig config, String correlationId) {
        WebClient.Builder builder = WebClient.builder()
                .clientConnector(httpConnector)
                .baseUrl(config.baseUrl());
        // 先设置自定义请求头（优先级最低）
        CustomHeaderUtils.applyCustomHeaders(builder, config.customHeaders(), "Provider客户端");
        // 再设置认证头（优先级最高，不可被自定义头覆盖）
        builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey());
        if (correlationId != null && !correlationId.isBlank()) {
            builder.defaultHeader("X-Correlation-Id", correlationId);
        }
        return builder.build();
    }

    /**
     * 从请求上下文中解析自定义请求头。
     */
    private Map<String, String> resolveCustomHeaders(UnifiedRequest request) {
        UnifiedRequest.ProviderExecutionContext ctx = request.getExecutionContext();
        return ctx != null && ctx.getCustomHeaders() != null
                ? ctx.getCustomHeaders()
                : Map.of();
    }

    // ==================== 重试策略 ====================

    /**
     * 构建指数退避重试策略（非流式请求）
     */
    protected Retry buildRetrySpec(ProviderRuntimeConfig config, RequestStatsContext statsContext) {
        return Retry.backoff(config.maxRetries(), Duration.ofMillis(config.initialIntervalMs()))
                .maxBackoff(Duration.ofMillis(config.maxIntervalMs()))
                .filter(this::isRetryableError)
                .doBeforeRetry(signal -> {
                    if (statsContext != null) {
                        statsContext.incrementRetryCount();
                        statsContext.setTerminalStage("UPSTREAM");
                    }
                    log.warn(
                            "[Provider重试] 提供商: {}, 第{}次重试(共{}次), 失败原因: {}",
                            config.providerName(),
                            signal.totalRetries() + 1,
                            config.maxRetries(),
                            signal.failure().getMessage()
                    );
                });
    }

    /**
     * 构建流式请求重试策略（仅首 token 前可重试）
     * <p>
     * 首个 token 到达后不再重试，避免向客户端重复输出。
     * </p>
     */
    protected Retry buildStreamRetrySpec(ProviderRuntimeConfig config,
                                         AtomicBoolean firstTokenReceived,
                                         RequestStatsContext statsContext) {
        return Retry.backoff(config.maxRetries(), Duration.ofMillis(config.initialIntervalMs()))
                .maxBackoff(Duration.ofMillis(config.maxIntervalMs()))
                .filter(error -> !firstTokenReceived.get() && isRetryableError(error))
                .doBeforeRetry(signal -> {
                    if (statsContext != null) {
                        statsContext.incrementRetryCount();
                        statsContext.setTerminalStage("STREAMING");
                    }
                    log.warn(
                            "[Provider流式重试] 提供商: {}, 第{}次重试(共{}次), 首 token 前失败, 原因: {}",
                            config.providerName(),
                            signal.totalRetries() + 1,
                            config.maxRetries(),
                            signal.failure().getMessage()
                    );
                });
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

    // ==================== Key 降级重试 ====================

    /** metadata key：该 Provider 所有可用 Key 列表 */
    private static final String META_PROVIDER_KEY_ENTRIES = GatewayMetadataKeys.PROVIDER_KEY_ENTRIES;

    /** 最大 Key 降级重试次数上限，实际不超过 allKeys.size()-1 */
    private static final int MAX_KEY_DEGRADATION_RETRIES = 5;

    /**
     * 判断异常是否可触发 Key 降级重试（仅 429 限流）。
     *
     * <p>设计决策：仅对 429 (Rate Limit) 触发降级，原因：
     * <ul>
     *   <li>429 — 该 Key 被限流，切换其他 Key 可能正常服务</li>
     *   <li>401/403 — Key 永久失效（过期/错误/权限不足），切换其他 Key 也大概率失败</li>
     *   <li>5xx — 上游服务故障，与 Key 无关，应由 Provider 级别的 Failover 处理</li>
     * </ul>
     * 如需扩展（如 Key 额度耗尽的特定错误码），子类可覆写此方法。</p>
     */
    protected boolean isKeyDegradableError(Throwable throwable) {
        if (throwable instanceof GatewayException gwEx) {
            return gwEx.getErrorCode() == ErrorCode.PROVIDER_RATE_LIMIT;
        }
        return false;
    }

    /**
     * Provider 内部 Key 降级重试（非流式）。
     * 遇到 429 时自动切换同 Provider 其他可用 Key 重试。
     */
    protected <T> Mono<T> withKeyDegradedRetry(UnifiedRequest request,
                                                java.util.function.Function<ProviderRuntimeConfig, Mono<T>> callFunction) {
        List<ProviderKeyEntry> allKeys = getProviderKeyEntries(request);
        if (allKeys == null || allKeys.size() <= 1) {
            return callFunction.apply(resolveRuntimeConfig(request));
        }

        Set<Long> usedKeyIds = initUsedKeyIds(allKeys, request);

        int maxRetries = Math.min(MAX_KEY_DEGRADATION_RETRIES, allKeys.size() - 1);

        return callFunction.apply(resolveRuntimeConfig(request))
                .onErrorResume(ex -> isKeyDegradableError(ex)
                        ? degradedRetryMono(request, allKeys, usedKeyIds, callFunction, ex, 0, maxRetries)
                        : Mono.error(ex));
    }

    /**
     * Provider 内部 Key 降级重试（流式）。
     * 仅在首 token 前可降级，避免向客户端重复输出。
     */
    protected <T> reactor.core.publisher.Flux<T> withStreamKeyDegradedRetry(
            UnifiedRequest request,
            java.util.function.Function<ProviderRuntimeConfig, reactor.core.publisher.Flux<T>> callFunction,
            AtomicBoolean firstTokenReceived) {
        List<ProviderKeyEntry> allKeys = getProviderKeyEntries(request);
        if (allKeys == null || allKeys.size() <= 1) {
            return callFunction.apply(resolveRuntimeConfig(request));
        }

        Set<Long> usedKeyIds = initUsedKeyIds(allKeys, request);

        int maxRetries = Math.min(MAX_KEY_DEGRADATION_RETRIES, allKeys.size() - 1);

        return callFunction.apply(resolveRuntimeConfig(request))
                .onErrorResume(ex -> {
                    if (firstTokenReceived.get() || !isKeyDegradableError(ex)) {
                        return reactor.core.publisher.Flux.error(ex);
                    }
                    return degradedRetryFlux(request, allKeys, usedKeyIds, callFunction, firstTokenReceived, ex, 0, maxRetries);
                });
    }

    /** 初始化已使用的 Key ID 集合 */
    private Set<Long> initUsedKeyIds(List<ProviderKeyEntry> allKeys, UnifiedRequest request) {
        Set<Long> usedKeyIds = new HashSet<>();
        String currentApiKey = request.getExecutionContext() != null
                ? request.getExecutionContext().getProviderApiKey() : null;
        ProviderKeyEntry currentKey = findCurrentKey(allKeys, currentApiKey);
        if (currentKey != null) {
            usedKeyIds.add(currentKey.id());
        }
        return usedKeyIds;
    }

    /** 判断是否可以继续 Key 降级重试 */
    private boolean canDegradeRetry(Throwable ex, AtomicBoolean firstTokenReceived) {
        if (!isKeyDegradableError(ex)) return false;
        return firstTokenReceived == null || !firstTokenReceived.get();
    }

    private <T> Mono<T> degradedRetryMono(UnifiedRequest request,
                                           List<ProviderKeyEntry> allKeys,
                                           Set<Long> usedKeyIds,
                                           java.util.function.Function<ProviderRuntimeConfig, Mono<T>> callFunction,
                                           Throwable lastError,
                                           int depth,
                                           int maxRetries) {
        if (depth >= maxRetries) return Mono.error(lastError);
        ProviderKeyEntry nextKey = pickNextKey(request, allKeys, usedKeyIds, "");
        if (nextKey == null) return Mono.error(lastError);

        return callFunction.apply(resolveRuntimeConfig(request, nextKey.apiKey()))
                .onErrorResume(ex -> canDegradeRetry(ex, null)
                        ? degradedRetryMono(request, allKeys, usedKeyIds, callFunction, ex, depth + 1, maxRetries)
                        : Mono.error(ex));
    }

    private <T> reactor.core.publisher.Flux<T> degradedRetryFlux(
            UnifiedRequest request,
            List<ProviderKeyEntry> allKeys,
            Set<Long> usedKeyIds,
            java.util.function.Function<ProviderRuntimeConfig, reactor.core.publisher.Flux<T>> callFunction,
            AtomicBoolean firstTokenReceived,
            Throwable lastError,
            int depth,
            int maxRetries) {
        if (depth >= maxRetries) return reactor.core.publisher.Flux.error(lastError);
        ProviderKeyEntry nextKey = pickNextKey(request, allKeys, usedKeyIds, "-流式");
        if (nextKey == null) return reactor.core.publisher.Flux.error(lastError);

        return callFunction.apply(resolveRuntimeConfig(request, nextKey.apiKey()))
                .onErrorResume(ex -> canDegradeRetry(ex, firstTokenReceived)
                        ? degradedRetryFlux(request, allKeys, usedKeyIds, callFunction, firstTokenReceived, ex, depth + 1, maxRetries)
                        : reactor.core.publisher.Flux.error(ex));
    }

    @SuppressWarnings("unchecked")
    private List<ProviderKeyEntry> getProviderKeyEntries(UnifiedRequest request) {
        if (request.getMetadata() == null) return null;
        Object value = request.getMetadata().get(META_PROVIDER_KEY_ENTRIES);
        if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof ProviderKeyEntry) {
            return (List<ProviderKeyEntry>) list;
        }
        return null;
    }

    /** 从 metadata 获取 Key 选择策略枚举 */
    private KeySelectionStrategy getKeySelectionStrategy(UnifiedRequest request) {
        if (request.getMetadata() == null) return KeySelectionStrategy.ROUND_ROBIN;
        Object strategy = request.getMetadata().get(GatewayMetadataKeys.KEY_SELECTION_STRATEGY);
        if (strategy instanceof KeySelectionStrategy kss) return kss;
        return strategy != null ? KeySelectionStrategy.from(strategy.toString()) : KeySelectionStrategy.ROUND_ROBIN;
    }

    private ProviderKeyEntry findCurrentKey(List<ProviderKeyEntry> keys, String apiKey) {
        if (apiKey == null) return null;
        return keys.stream()
                .filter(k -> apiKey.equals(k.apiKey()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 选择下一个降级 Key（共用逻辑）。
     * FALLBACK 策略按 sortOrder 顺序选下一个，其余策略用随机选择。
     */
    private ProviderKeyEntry pickNextKey(UnifiedRequest request,
                                          List<ProviderKeyEntry> allKeys,
                                          Set<Long> usedKeyIds,
                                          String streamTag) {
        List<ProviderKeyEntry> remaining = allKeys.stream()
                .filter(k -> !usedKeyIds.contains(k.id()))
                .toList();
        if (remaining.isEmpty()) {
            return null;
        }

        KeySelectionStrategy strategy = getKeySelectionStrategy(request);
        ProviderKeyEntry nextKey;
        if (strategy == KeySelectionStrategy.FALLBACK || strategy == KeySelectionStrategy.ROUND_ROBIN) {
            // FALLBACK 与 ROUND_ROBIN 降级时均按 sortOrder 顺序选择，保证每个 Key 都有均等的降级机会
            nextKey = remaining.stream()
                    .min(java.util.Comparator.comparingInt(ProviderKeyEntry::sortOrder))
                    .orElse(remaining.get(0));
        } else {
            nextKey = remaining.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(remaining.size()));
        }
        usedKeyIds.add(nextKey.id());

        log.warn("[Key降级{}] provider={}, 原Key失败(429), 切换到Key prefix={}, 策略={}",
                streamTag,
                request.getExecutionContext() != null ? request.getExecutionContext().getProviderName() : "unknown",
                nextKey.apiKeyPrefix(),
                strategy);

        // 更新 metadata 中的 usedApiKeyPrefix，用于统计采集。
        // 安全说明：request 对象为单请求隔离，不会跨请求共享，因此此处修改是安全的。
        if (request.getMetadata() != null) {
            request.getMetadata().put(GatewayMetadataKeys.USED_API_KEY_PREFIX, nextKey.apiKeyPrefix());
            // 同步更新统计上下文，确保日志记录的是实际使用的 Key 标识
            Object statsCtx = request.getMetadata().get(GatewayMetadataKeys.STATS_CONTEXT);
            if (statsCtx instanceof RequestStatsContext ctx) {
                ctx.setProviderApiKeyMasked(nextKey.apiKeyPrefix());
                ctx.setProviderKeyId(nextKey.id());
            }
        }

        return nextKey;
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
                    String rawBody = body != null && !body.isBlank() ? truncateForLog(body, 500) : "N/A";
                    String safeErrorType = errorType.isBlank() ? "N/A" : errorType;
                    String safeErrorMsg = errorMessage.isBlank() ? "N/A" : truncateForLog(errorMessage, 200);
                    log.warn("[上游错误] 提供商: {}, HTTP状态: {}, 错误类型: {}, 错误描述: {}, rawBody: {}",
                            config.providerName(), statusCode.value(),
                            safeErrorType, safeErrorMsg, rawBody);

                    // 细粒度错误映射：区分认证、参数、资源、限流、服务端等错误
                    ErrorCode errorCode;
                    if (statusCode.value() == 429) {
                        errorCode = ErrorCode.PROVIDER_RATE_LIMIT;
                    } else if (statusCode.value() == 401 || statusCode.value() == 403) {
                        errorCode = ErrorCode.PROVIDER_AUTH_ERROR;
                    } else if (statusCode.value() == 400 || statusCode.value() == 422) {
                        errorCode = ErrorCode.PROVIDER_BAD_REQUEST;
                    } else if (statusCode.value() == 404) {
                        errorCode = ErrorCode.PROVIDER_RESOURCE_NOT_FOUND;
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
    protected String truncateForLog(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...(truncated)";
    }

    // ==================== 工具方法 ====================

    /**
     * 从消息的 parts 中提取纯文本内容。
     * <p>
     * 由于各 Provider 协议不同，但消息统一为 UnifiedMessage 后，
     * 提取文本的逻辑完全一致，故此方法提取到基类复用。
     * </p>
     */
    protected String extractTextContent(UnifiedMessage msg) {
        if (msg.getParts() == null || msg.getParts().isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (UnifiedPart part : msg.getParts()) {
            if ("text".equals(part.getType()) && part.getText() != null) {
                sb.append(part.getText());
            }
        }
        return sb.toString();
    }

    /**
     * 安全解析 JSON 参数字符串，失败时返回空 Map。
     */
    protected Object parseJsonArgs(String json) {
        if (json == null || json.isEmpty() || "{}".equals(json)) return Map.of();
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * 将 JsonNode 序列化为 JSON 字符串，null/missing 返回 "{}"。
     */
    protected String objectToString(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return "{}";
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{}";
        }
    }

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

        // 解析缓存命中 Token（OpenAI Chat Completions: prompt_tokens_details.cached_tokens）
        JsonNode cachedTokensNode = usageNode.path("prompt_tokens_details").path("cached_tokens");
        if (!cachedTokensNode.isMissingNode() && !cachedTokensNode.isNull()) {
            usage.setCachedInputTokens(cachedTokensNode.asInt());
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
     * 从请求 metadata 中安全提取 RequestStatsContext，metadata 为 null 时返回 null。
     */
    protected RequestStatsContext getStatsContext(UnifiedRequest request) {
        if (request.getMetadata() == null) return null;
        return (RequestStatsContext) request.getMetadata().get(GatewayMetadataKeys.STATS_CONTEXT);
    }

    /**
     * 判断是否使用简化 thinking 模式。
     * <p>
     * 从 UnifiedRequest.metadata 中读取 thinkingCompatMode，
     * 由路由层根据提供商配置写入，不硬编码在 SDK 中。
     * </p>
     */
    protected boolean isSimplifiedThinkingMode(UnifiedRequest request) {
        if (request.getMetadata() == null) {
            return false;
        }
        Object mode = request.getMetadata().get(META_THINKING_COMPAT_MODE);
        return "simplified".equals(mode != null ? mode.toString() : null);
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

    /**
     * 读取 JSON 节点长整型值，null/missing 返回 null
     */
    protected Long longOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return node.asLong();
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
            long maxIntervalMs,
            Map<String, String> customHeaders
    ) {
    }
}
