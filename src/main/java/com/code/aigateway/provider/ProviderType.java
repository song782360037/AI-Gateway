package com.code.aigateway.provider;

/**
 * AI 提供商类型枚举
 * <p>
 * 定义了网关支持的 AI 服务提供商类型
 * </p>
 *
 * @author sst
 */
public enum ProviderType {
    /**
     * OpenAI 提供商
     */
    OPENAI,

    /**
     * Anthropic 提供商（Claude 系列）
     */
    ANTHROPIC,

    /**
     * Google Gemini 提供商
     */
    GEMINI;

    /**
     * 根据字符串值解析提供商类型
     * <p>
     * 支持多种别名映射：
     * <ul>
     *   <li>openai -> OPENAI</li>
     *   <li>anthropic、claude -> ANTHROPIC</li>
     *   <li>gemini、google -> GEMINI</li>
     * </ul>
     * </p>
     *
     * @param value 提供商名称字符串
     * @return 对应的提供商类型枚举
     * @throws IllegalArgumentException 当传入不支持的提供商名称时抛出
     */
    public static ProviderType from(String value) {
        return switch (value.toLowerCase()) {
            case "openai" -> OPENAI;
            case "anthropic", "claude" -> ANTHROPIC;
            case "gemini", "google" -> GEMINI;
            default -> throw new IllegalArgumentException("Unsupported provider: " + value);
        };
    }
}
