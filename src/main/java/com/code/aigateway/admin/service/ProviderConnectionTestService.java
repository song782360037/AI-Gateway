package com.code.aigateway.admin.service;

import com.code.aigateway.admin.mapper.ProviderConfigMapper;
import com.code.aigateway.admin.model.dataobject.ProviderConfigDO;
import com.code.aigateway.admin.model.rsp.ConnectionTestResult;
import com.code.aigateway.infra.crypto.ApiKeyEncryptor;
import com.code.aigateway.provider.ProviderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * 提供商连接测试服务
 * <p>
 * 根据 ProviderType 构造轻量级验证请求，确认服务可达性和凭证有效性。
 * <ul>
 *   <li>OpenAI / OpenAI Responses — GET /v1/models（零消耗，验证连通性 + 认证）</li>
 *   <li>Anthropic — POST /v1/messages（最小化请求，max_tokens=1）</li>
 *   <li>Gemini — GET /v1beta/models?key=xxx（零消耗，验证连通性 + 认证）</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderConnectionTestService {

    /** 连接测试专用超时，不宜过长 */
    private static final int TEST_TIMEOUT_SECONDS = 10;

    private final ProviderConfigMapper providerConfigMapper;
    private final ApiKeyEncryptor apiKeyEncryptor;
    private final WebClient.Builder webClientBuilder;

    /**
     * 加载提供商配置并解密 API Key（阻塞操作，调用方需切线程）
     *
     * @param providerId 提供商配置主键
     * @return 加载结果，配置不存在时返回 null
     */
    public TestContext loadTestContext(Long providerId) {
        ProviderConfigDO config = providerConfigMapper.selectById(providerId);
        if (config == null) {
            return null;
        }
        String apiKey = apiKeyEncryptor.decrypt(config.getApiKeyIv(), config.getApiKeyCiphertext());
        ProviderType providerType = ProviderType.valueOf(config.getProviderType());
        return new TestContext(config.getProviderCode(), providerType, trimTrailingSlash(config.getBaseUrl()), apiKey);
    }

    /**
     * 执行连接测试（纯响应式，无阻塞操作）
     *
     * @param ctx 加载的测试上下文
     * @return 测试结果
     */
    public Mono<ConnectionTestResult> executeTest(TestContext ctx) {
        return doExecuteTest(ctx.type(), ctx.baseUrl(), ctx.apiKey())
                .doOnNext(result -> log.info("[连接测试] 提供商: {}, 类型: {}, 结果: {}, 延迟: {}ms",
                        ctx.providerCode(), ctx.type(),
                        result.isSuccess() ? "成功" : "失败",
                        result.getLatencyMs()));
    }

    /**
     * 按协议类型分发测试请求
     */
    private Mono<ConnectionTestResult> doExecuteTest(ProviderType type, String baseUrl, String apiKey) {
        return switch (type) {
            case OPENAI, OPENAI_RESPONSES -> testOpenAi(baseUrl, apiKey);
            case ANTHROPIC -> testAnthropic(baseUrl, apiKey);
            case GEMINI -> testGemini(baseUrl, apiKey);
        };
    }

    /**
     * OpenAI 测试：GET /v1/models
     * <p>仅验证连通性和认证，不消耗 token。</p>
     */
    private Mono<ConnectionTestResult> testOpenAi(String baseUrl, String apiKey) {
        long startMs = System.currentTimeMillis();

        return webClientBuilder.baseUrl(baseUrl)
                .build()
                .get()
                .uri("/v1/models")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(TEST_TIMEOUT_SECONDS))
                .map(response -> ConnectionTestResult.builder()
                        .success(true)
                        .latencyMs(System.currentTimeMillis() - startMs)
                        .build())
                .onErrorResume(ex -> Mono.just(buildErrorResult(ex, startMs)));
    }

    /**
     * Anthropic 测试：POST /v1/messages
     * <p>Anthropic 没有轻量级的 models list 接口，发送最小化消息请求。
     * 注意：此请求会消耗约 1 个 output token，产生极少费用。</p>
     */
    private Mono<ConnectionTestResult> testAnthropic(String baseUrl, String apiKey) {
        long startMs = System.currentTimeMillis();

        // 构造最小化请求体，仅需验证连通性和认证
        String requestBody = """
                {
                  "model": "claude-3-5-haiku-20241022",
                  "max_tokens": 1,
                  "messages": [{"role": "user", "content": "hi"}]
                }
                """;

        return webClientBuilder.baseUrl(baseUrl)
                .build()
                .post()
                .uri("/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(TEST_TIMEOUT_SECONDS))
                .map(response -> ConnectionTestResult.builder()
                        .success(true)
                        .latencyMs(System.currentTimeMillis() - startMs)
                        .build())
                .onErrorResume(ex -> Mono.just(buildErrorResult(ex, startMs)));
    }

    /**
     * Gemini 测试：GET /v1beta/models?key=xxx
     * <p>仅验证连通性和认证，不消耗 token。</p>
     */
    private Mono<ConnectionTestResult> testGemini(String baseUrl, String apiKey) {
        long startMs = System.currentTimeMillis();

        return webClientBuilder.baseUrl(baseUrl)
                .build()
                .get()
                .uri("/v1beta/models?key=" + apiKey)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(TEST_TIMEOUT_SECONDS))
                .map(response -> ConnectionTestResult.builder()
                        .success(true)
                        .latencyMs(System.currentTimeMillis() - startMs)
                        .build())
                .onErrorResume(ex -> Mono.just(buildErrorResult(ex, startMs)));
    }

    // ==================== 错误处理 ====================

    /**
     * 将异常分类为用户友好的错误类型和描述
     */
    private ConnectionTestResult buildErrorResult(Throwable ex, long startMs) {
        long latencyMs = System.currentTimeMillis() - startMs;
        String errorType;
        String errorMessage;

        if (isAuthError(ex)) {
            errorType = "AUTH_FAILED";
            errorMessage = "认证失败：API Key 无效或已过期";
        } else if (isRateLimitError(ex)) {
            errorType = "RATE_LIMIT";
            errorMessage = "请求频率超限：上游服务商返回 429 Too Many Requests";
        } else if (isTimeout(ex)) {
            errorType = "TIMEOUT";
            errorMessage = "连接超时：服务商未在 " + TEST_TIMEOUT_SECONDS + " 秒内响应";
        } else if (isNetworkError(ex)) {
            errorType = "NETWORK_ERROR";
            errorMessage = "网络错误：" + resolveNetworkDetail(ex);
        } else if (isServerError(ex)) {
            errorType = "SERVER_ERROR";
            errorMessage = "服务端错误：上游服务商返回 5xx 错误";
        } else {
            errorType = "UNKNOWN";
            errorMessage = "未知错误：" + resolveRootMessage(ex);
        }

        log.warn("[连接测试] 失败, errorType={}, message={}", errorType, errorMessage, ex);
        return ConnectionTestResult.builder()
                .success(false)
                .latencyMs(latencyMs)
                .errorType(errorType)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 从异常链中提取有意义的错误描述，避免返回 null
     */
    private String resolveRootMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause.getMessage() != null && !cause.getMessage().isBlank()) {
                return cause.getMessage();
            }
            cause = cause.getCause();
        }
        return ex.getClass().getSimpleName();
    }

    /**
     * 针对 SSL 握手失败等网络异常，提取更具体的描述
     */
    private String resolveNetworkDetail(Throwable ex) {
        // 检查 suppressed 异常（SSL 握手失败信息在 suppressed 中）
        for (Throwable suppressed : ex.getSuppressed()) {
            if (suppressed instanceof javax.net.ssl.SSLHandshakeException) {
                return "SSL/TLS 握手失败，请检查 Base URL 和网络配置";
            }
        }
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof java.net.UnknownHostException) {
                return "DNS 解析失败，请检查 Base URL";
            }
            if (cause instanceof ConnectException) {
                return "连接被拒绝，请检查 Base URL 和端口";
            }
            if (cause instanceof ClosedChannelException) {
                return "连接被对端关闭，可能是 SSL 握手失败或网络中断";
            }
            if (cause instanceof javax.net.ssl.SSLException) {
                return "SSL/TLS 握手失败，请检查 Base URL 和网络配置";
            }
            // 检查 suppressed
            for (Throwable suppressed : cause.getSuppressed()) {
                if (suppressed instanceof javax.net.ssl.SSLHandshakeException) {
                    return "SSL/TLS 握手失败，请检查 Base URL 和网络配置";
                }
            }
            cause = cause.getCause();
        }
        return "无法连接到服务商，请检查 Base URL 和网络配置";
    }

    /** 认证失败：HTTP 401/403 */
    private boolean isAuthError(Throwable ex) {
        if (ex instanceof org.springframework.web.reactive.function.client.WebClientResponseException webEx) {
            int status = webEx.getStatusCode().value();
            return status == 401 || status == 403;
        }
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof org.springframework.web.reactive.function.client.WebClientResponseException webEx) {
                int status = webEx.getStatusCode().value();
                return status == 401 || status == 403;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /** 频率限制：HTTP 429 */
    private boolean isRateLimitError(Throwable ex) {
        if (ex instanceof org.springframework.web.reactive.function.client.WebClientResponseException webEx) {
            return webEx.getStatusCode().value() == 429;
        }
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof org.springframework.web.reactive.function.client.WebClientResponseException webEx) {
                return webEx.getStatusCode().value() == 429;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /** 超时异常 */
    private boolean isTimeout(Throwable ex) {
        if (ex instanceof TimeoutException) return true;
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof TimeoutException) return true;
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * 网络异常：DNS 解析失败、连接拒绝、SSL 握手失败、连接关闭等
     */
    private boolean isNetworkError(Throwable ex) {
        if (matchesNetworkError(ex)) return true;
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (matchesNetworkError(cause)) return true;
            cause = cause.getCause();
        }
        // 检查 suppressed（SSL 握手失败信息在 suppressed 中）
        if (hasSuppressedNetworkError(ex)) return true;
        return false;
    }

    /** 判断单个异常是否为网络异常 */
    private boolean matchesNetworkError(Throwable ex) {
        return ex instanceof ConnectException
                || ex instanceof java.net.UnknownHostException
                || ex instanceof ClosedChannelException
                || ex instanceof javax.net.ssl.SSLException;
    }

    /** 检查异常链中是否有 suppressed 的网络异常 */
    private boolean hasSuppressedNetworkError(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            for (Throwable suppressed : current.getSuppressed()) {
                if (suppressed instanceof javax.net.ssl.SSLHandshakeException) return true;
                if (suppressed instanceof javax.net.ssl.SSLException) return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /** 服务端错误：HTTP 5xx */
    private boolean isServerError(Throwable ex) {
        if (ex instanceof org.springframework.web.reactive.function.client.WebClientResponseException webEx) {
            return webEx.getStatusCode().is5xxServerError();
        }
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof org.springframework.web.reactive.function.client.WebClientResponseException webEx) {
                return webEx.getStatusCode().is5xxServerError();
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * 连接测试上下文（不可变），承载从数据库加载的配置信息
     */
    public record TestContext(String providerCode, ProviderType type, String baseUrl, String apiKey) {
    }

    private String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
