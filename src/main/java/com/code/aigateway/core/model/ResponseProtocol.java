package com.code.aigateway.core.model;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    GEMINI;

    /** 所有枚举名称集合，用于快速校验 */
    private static final Set<String> VALID_NAMES = Arrays.stream(values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    /**
     * 解析逗号分隔的协议字符串为列表。
     * <p>null 或空白 → 空列表（语义为支持所有协议）</p>
     *
     * @param commaSeparated 逗号分隔的协议字符串，如 "OPENAI_CHAT,ANTHROPIC"
     * @return 协议名称列表；输入为空时返回空列表
     */
    public static List<String> parseCommaSeparated(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) {
            return List.of();
        }
        return Arrays.stream(commaSeparated.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * 校验协议名称是否为合法的 ResponseProtocol 枚举值。
     *
     * @param protocolName 协议名称
     * @return true 表示合法
     */
    public static boolean isValid(String protocolName) {
        return protocolName != null && VALID_NAMES.contains(protocolName);
    }
}
