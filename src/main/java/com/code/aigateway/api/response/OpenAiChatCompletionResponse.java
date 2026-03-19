package com.code.aigateway.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * OpenAI 聊天完成响应
 * <p>
 * 与 OpenAI Chat Completion API 的响应格式完全兼容。
 * 用于非流式请求的响应。
 * </p>
 *
 * @author sst
 */
@Data
@Builder
public class OpenAiChatCompletionResponse {

    /**
     * 响应唯一标识
     */
    private String id;

    /**
     * 对象类型，固定为 "chat.completion"
     */
    private String object;

    /**
     * 创建时间戳（Unix 时间）
     */
    private Long created;

    /**
     * 使用的模型名称
     */
    private String model;

    /**
     * 选择列表（通常只有一个选择）
     */
    private List<Choice> choices;

    /**
     * Token 使用统计
     */
    private Usage usage;

    /**
     * 选择项
     */
    @Data
    @Builder
    public static class Choice {

        /**
         * 选择索引
         */
        private Integer index;

        /**
         * 响应消息
         */
        private Message message;

        /**
         * 完成原因
         * <p>
         * 可能值：stop、length、tool_calls
         * </p>
         */
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    /**
     * 响应消息
     */
    @Data
    @Builder
    public static class Message {

        /**
         * 消息角色，通常为 "assistant"
         */
        private String role;

        /**
         * 消息内容
         */
        private String content;
    }

    /**
     * Token 使用统计
     */
    @Data
    @Builder
    public static class Usage {

        /**
         * 输入 token 数
         */
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        /**
         * 输出 token 数
         */
        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        /**
         * 总 token 数
         */
        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }
}
