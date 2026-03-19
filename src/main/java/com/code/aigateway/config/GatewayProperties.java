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
}
