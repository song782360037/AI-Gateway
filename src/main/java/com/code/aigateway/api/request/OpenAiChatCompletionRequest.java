package com.code.aigateway.api.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * OpenAI 聊天完成请求
 * <p>
 * 与 OpenAI Chat Completion API 的请求格式完全兼容。
 * 支持文本对话、多模态内容（图片）和工具调用。
 * </p>
 *
 * @author sst
 */
@Data
public class OpenAiChatCompletionRequest {

    /**
     * 模型名称（必填）
     * <p>
     * 可以是实际模型名或网关配置的别名
     * </p>
     */
    @NotBlank
    private String model;

    /**
     * 消息列表（必填）
     */
    @NotEmpty
    @Valid
    private List<OpenAiMessage> messages;

    /**
     * 温度参数（0-2），控制输出随机性
     */
    private Double temperature;

    /**
     * Top-P 采样参数
     */
    @JsonProperty("top_p")
    private Double topP;

    /**
     * 最大输出 token 数
     */
    @JsonProperty("max_tokens")
    private Integer maxTokens;

    /**
     * 停止序列列表
     */
    private List<String> stop;

    /**
     * 是否启用流式输出，默认 false
     */
    private Boolean stream = false;

    /**
     * 工具定义列表
     */
    private List<OpenAiTool> tools;

    /**
     * 工具选择策略
     * <p>
     * 支持值：
     * <ul>
     *   <li>"auto": 自动决定是否调用工具</li>
     *   <li>"none": 不调用工具</li>
     *   <li>{"type": "function", "function": {"name": "xxx"}}: 强制调用指定工具</li>
     * </ul>
     * </p>
     */
    @JsonProperty("tool_choice")
    private Object toolChoice;

    /**
     * OpenAI 消息格式
     */
    @Data
    public static class OpenAiMessage {

        /**
         * 消息角色
         * <p>
         * 支持值：system、user、assistant、tool
         * </p>
         */
        @NotBlank
        private String role;

        /**
         * 消息内容
         * <p>
         * 支持两种格式：
         * <ul>
         *   <li>字符串：纯文本内容</li>
         *   <li>数组：多模态内容，支持 text 和 image_url 类型</li>
         * </ul>
         * </p>
         */
        private Object content;

        /**
         * 工具调用 ID（当 role 为 tool 时使用）
         */
        @JsonProperty("tool_call_id")
        private String toolCallId;
    }

    /**
     * OpenAI 工具定义
     */
    @Data
    public static class OpenAiTool {

        /**
         * 工具类型，通常为 "function"
         */
        private String type;

        /**
         * 函数定义
         */
        private FunctionDef function;

        /**
         * 函数定义详情
         */
        @Data
        public static class FunctionDef {

            /**
             * 函数名称
             */
            private String name;

            /**
             * 函数描述
             */
            private String description;

            /**
             * 函数参数 JSON Schema
             */
            private Map<String, Object> parameters;
        }
    }
}
