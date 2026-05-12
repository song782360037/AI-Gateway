package com.code.aigateway.provider.anthropic;

/**
 * Anthropic 流式解析状态跟踪器
 */
class AnthropicStreamState {
    String messageId;
    Integer inputTokens;
    Integer outputTokens;
    Integer totalTokens;
    String currentToolId;
    String currentToolName;
    StringBuilder currentToolArgs;
    int currentToolIndex = -1;
}
