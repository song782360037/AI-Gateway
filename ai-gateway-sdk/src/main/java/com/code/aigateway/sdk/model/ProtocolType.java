package com.code.aigateway.sdk.model;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 协议类型枚举
 * <p>
 * 定义 SDK 支持的 AI API 协议格式。
 * </p>
 */
public enum ProtocolType {

    /** OpenAI Chat Completions */
    OPENAI_CHAT,

    /** OpenAI Responses API */
    OPENAI_RESPONSES,

    /** Anthropic Messages API */
    ANTHROPIC,

    /** Google Gemini API */
    GEMINI,

    /** OpenAI Embeddings API */
    OPENAI_EMBEDDING,

    /** Rerank API (Cohere/Jina 格式) */
    RERANK;

    private static final Set<String> VALID_NAMES = Arrays.stream(values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    /**
     * 校验协议名称是否合法
     */
    public static boolean isValid(String protocolName) {
        return protocolName != null && VALID_NAMES.contains(protocolName);
    }

    /**
     * 解析逗号分隔的协议字符串为列表
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
     * 归一化协议名称，用于跨格式比较
     * <p>openai-chat → openai_chat, OPENAI_CHAT → openai_chat</p>
     */
    public static String normalize(String protocol) {
        if (protocol == null) {
            return null;
        }
        return protocol.trim().toLowerCase().replace('-', '_');
    }
}
