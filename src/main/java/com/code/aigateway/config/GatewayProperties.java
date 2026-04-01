package com.code.aigateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * 网关配置属性类
 * <p>
 * 用于映射 application.yml 中以 "gateway" 为前缀的配置项，
 * 包含认证、模型别名和提供商配置。
 * </p>
 *
 * @author sst
 */
@Data
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    /**
     * 认证配置
     */
    private AuthProperties auth;

    /**
     * 模型别名映射配置
     * <p>
     * key: 别名（用户请求时使用的模型名称）
     * value: 别名属性（映射到实际的提供商和模型）
     * </p>
     */
    private Map<String, ModelAliasProperties> modelAliases;

    /**
     * AI 提供商配置
     * <p>
     * key: 提供商名称（如 openai、anthropic、gemini）
     * value: 提供商的具体配置
     * </p>
     */
    private Map<String, ProviderProperties> providers;

    /**
     * 全局重试配置（流式和非流式统一）
     * <p>
     * 未配置时默认不重试（maxRetries=0）。
     * </p>
     */
    private RetryProperties retry;

    /**
     * 管理后台 JWT 认证配置
     */
    private AdminAuthProperties adminAuth;

    /**
     * CORS 跨域配置
     */
    private CorsProperties cors;

    /**
     * 限流配置
     */
    private RateLimitProperties rateLimit;

    /**
     * 熔断器配置
     */
    private CircuitBreakerProperties circuitBreaker;

    /**
     * 认证配置类
     * <p>
     * 用于配置网关的 API Key 认证
     * </p>
     */
    @Data
    public static class AuthProperties {
        /**
         * 是否启用认证，默认启用
         */
        private boolean enabled = true;

        /**
         * 允许访问的 API Key 列表
         */
        private List<String> apiKeys;
    }

    /**
     * 模型别名配置类
     * <p>
     * 将用户请求的模型别名映射到实际的提供商和模型名称
     * </p>
     */
    @Data
    public static class ModelAliasProperties {
        /**
         * 提供商名称（如 openai、anthropic）
         */
        private String provider;

        /**
         * 实际的模型名称（如 gpt-4-turbo、claude-3-opus）
         */
        private String model;
    }

    /**
     * 提供商配置类
     * <p>
     * 配置 AI 提供商的连接信息和认证
     * </p>
     */
    @Data
    public static class ProviderProperties {
        /**
         * 是否启用该提供商
         */
        private boolean enabled;

        /**
         * 提供商 API 基础地址
         */
        private String baseUrl;

        /**
         * 提供商 API Key
         */
        private String apiKey;

        /**
         * API 版本号（部分提供商需要）
         */
        private String version;

        /**
         * 请求超时时间（秒），默认 60 秒
         */
        private Integer timeoutSeconds = 60;
    }

    /**
     * 全局重试配置
     * <p>
     * 适用于非流式和流式请求。流式请求仅在首个 token 到达前重试。
     * 默认值仅在 retry 配置段存在但字段缺失时生效；
     * 整个 retry 段不存在时，默认不重试（maxRetries 回退为 0）。
     * </p>
     */
    @Data
    public static class RetryProperties {
        /**
         * 最大重试次数（不含首次请求），默认 3
         */
        private int maxRetries = 3;

        /**
         * 初始退避间隔（毫秒），默认 1000ms
         * <p>
         * 实际间隔 = min(initialIntervalMs * 2^attempt, maxIntervalMs)
         * </p>
         */
        private long initialIntervalMs = 1000;

        /**
         * 最大退避间隔（毫秒），默认 30000ms
         */
        private long maxIntervalMs = 30000;
    }

    /**
     * 管理后台 JWT 认证配置
     */
    @Data
    public static class AdminAuthProperties {
        /**
         * 管理员用户名
         */
        private String username = "admin";

        /**
         * 管理员密码
         */
        private String password = "admin123";

        /**
         * JWT Token 有效期（毫秒），默认 24 小时
         */
        private long jwtExpirationMs = 86400000L;
    }

    /**
     * CORS 跨域配置
     */
    @Data
    public static class CorsProperties {
        /** 允许的 Origin 列表 */
        private List<String> allowedOrigins = List.of("*");
        /** 允许的 HTTP 方法 */
        private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS");
        /** 允许的请求头 */
        private List<String> allowedHeaders = List.of("*");
        /** 是否允许携带凭证 */
        private boolean allowCredentials = false;
        /** 预检请求缓存时间（秒） */
        private long maxAgeSeconds = 3600;
    }

    /**
     * API Key 限流配置
     */
    @Data
    public static class RateLimitProperties {
        /** 是否启用限流 */
        private boolean enabled = true;
        /** 默认每分钟请求上限 */
        private int defaultRpm = 60;
        /** 默认每小时请求上限 */
        private int defaultHourlyRpm = 3600;
    }

    /**
     * 熔断器配置
     */
    @Data
    public static class CircuitBreakerProperties {
        /** 是否启用熔断 */
        private boolean enabled = true;
        /** 滑动窗口大小（最近 N 次调用） */
        private int slidingWindowSize = 10;
        /** 失败率阈值（百分比），超过则打开熔断 */
        private float failureRateThreshold = 50.0f;
        /** 慢调用判定阈值（毫秒） */
        private int slowCallDurationMs = 10000;
        /** 慢调用率阈值（百分比） */
        private float slowCallRateThreshold = 80.0f;
        /** 熔断打开后等待时间（毫秒），之后进入半开 */
        private int waitDurationInOpenStateMs = 60000;
        /** 半开状态允许的探测请求数 */
        private int permittedNumberOfCallsInHalfOpenState = 5;
        /** 触发熔断计算的最小调用次数 */
        private int minimumNumberOfCalls = 5;
    }
}
