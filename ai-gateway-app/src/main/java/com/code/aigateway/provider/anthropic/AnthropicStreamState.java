package com.code.aigateway.provider.anthropic;

/**
 * Anthropic 流式解析状态跟踪器
 */
class AnthropicStreamState {
    String messageId;
    Integer inputTokens;
    /** Anthropic 原始 input_tokens（归一化前保存，用于协议编码还原） */
    Integer rawInputTokens;
    Integer outputTokens;
    Integer cachedInputTokens;
    Integer cacheCreationInputTokens;
    Integer totalTokens;
    String currentToolId;
    String currentToolName;
    StringBuilder currentToolArgs;
    int currentToolIndex = -1;
}
