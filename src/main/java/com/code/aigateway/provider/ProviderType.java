package com.code.aigateway.provider;

/**
 * AI提供商类型
 */
public enum ProviderType {
    OPENAI,
    ANTHROPIC,
    GEMINI;

    public static ProviderType from(String value) {
        return switch (value.toLowerCase()) {
            case "openai" -> OPENAI;
            case "anthropic", "claude" -> ANTHROPIC;
            case "gemini", "google" -> GEMINI;
            default -> throw new IllegalArgumentException("Unsupported provider: " + value);
        };
    }
}
