package com.code.aigateway.core.router.auto;

/**
 * Auto 路由请求画像。
 */
public record AutoRequestProfile(
        boolean visionRequired,
        boolean toolsRequired,
        boolean toolChoiceRequired,
        boolean reasoningRequired,
        boolean jsonRequired,
        boolean streamRequired,
        int estimatedInputTokens,
        Integer requestedOutputTokens,
        Complexity complexity,
        Intent intent
) {

    public enum Complexity {
        SIMPLE,
        NORMAL,
        COMPLEX
    }

    public enum Intent {
        BALANCED,
        FAST,
        CHEAP,
        QUALITY,
        AGENT,
        VISION
    }
}
