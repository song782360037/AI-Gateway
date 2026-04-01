package com.code.aigateway.core.model;

/**
 * 响应协议枚举
 * <p>
 * 定义网关对外暴露的 API 接口格式，用于：
 * <ul>
 *   <li>Controller 在 exchange attributes 中标记当前请求的协议</li>
 *   <li>GlobalExceptionHandler 按协议选择错误响应格式</li>
 *   <li>ProtocolAdapter 按协议实现对应的编解码逻辑</li>
 * </ul>
 * </p>
 */
public enum ResponseProtocol {

    /** OpenAI Chat Completions — POST /v1/chat/completions */
    OPENAI_CHAT,

    /** OpenAI Responses API — POST /v1/responses */
    OPENAI_RESPONSES,

    /** Anthropic Messages API — POST /v1/messages */
    ANTHROPIC,

    /** Google Gemini API — POST /v1beta/models/{model}:generateContent */
    GEMINI
}
