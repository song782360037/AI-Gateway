package com.code.aigateway.api.request;

import com.code.aigateway.core.stats.StatsRequestInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages API 请求
 * <p>
 * 兼容 Anthropic Messages API 的请求格式（POST /v1/messages）。
 * </p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnthropicMessagesRequest implements StatsRequestInfo {

    /** 模型名称（必填） */
    @NotBlank
    private String model;

    /** 消息列表（必填，要求 user/assistant 交替） */
    @Valid
    private List<Message> messages;

    /**
     * 系统提示词
     * <p>
     * 支持两种格式：
     * <ul>
     *   <li>字符串：纯文本系统提示</li>
     *   <li>数组：[{"type":"text","text":"...","cache_control":...}]</li>
     * </ul>
     * </p>
     */
    private Object system;

    /** 最大输出 token 数（必填） */
    @JsonProperty("max_tokens")
    private Integer maxTokens;

    /** 是否启用流式输出 */
    private Boolean stream = false;

    /** 工具定义列表 */
    @Valid
    private List<Tool> tools;

    /** 工具选择策略 */
    @JsonProperty("tool_choice")
    private Object toolChoice;

    /** 停止序列 */
    private List<String> stopSequences;

    /** 温度参数 */
    private Double temperature;

    /** Top-P 采样参数 */
    @JsonProperty("top_p")
    private Double topP;

    /** Top-K 采样参数 */
    @JsonProperty("top_k")
    private Integer topK;

    /** 扩展元数据 */
    private Map<String, Object> metadata;

    @Override
    public Boolean isStream() {
        return stream;
    }

    // ===================== 消息格式 =====================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        /** 消息角色：user 或 assistant */
        private String role;
        /** 消息内容（字符串或 ContentBlock 数组） */
        private Object content;
    }

    // ===================== 工具定义 =====================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Tool {
        private String name;
        private String description;
        /** Anthropic 使用 input_schema 而非 parameters */
        @JsonProperty("input_schema")
        private Map<String, Object> inputSchema;
    }
}
