package com.code.aigateway.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * OpenAI 聊天完成流式响应块
 * <p>
 * 与 OpenAI Chat Completion API 的流式响应格式兼容。
 * 用于流式请求的单个 SSE 事件数据。
 * </p>
 *
 * @author sst
 */
@Data
@Builder
public class OpenAiChatCompletionChunkResponse {

    /**
     * 响应唯一标识
     */
    private String id;

    /**
     * 对象类型，固定为 "chat.completion.chunk"
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
     * 选择列表
     */
    private List<Choice> choices;

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
         * 增量内容
         */
        private Delta delta;

        /**
         * 完成原因（最后一个块才会有值）
         */
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    /**
     * 增量内容
     * <p>
     * 每个流式事件中新增的消息内容
     * </p>
     */
    @Data
    @Builder
    public static class Delta {

        /**
         * 消息角色（通常只在第一个事件中出现）
         */
        private String role;

        /**
         * 增量文本内容
         */
        private String content;
    }
}
