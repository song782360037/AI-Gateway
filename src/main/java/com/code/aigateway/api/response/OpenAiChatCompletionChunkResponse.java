package com.code.aigateway.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
    @JsonInclude(JsonInclude.Include.NON_NULL)
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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Delta {

        /**
         * 消息角色（通常只在第一个事件中出现）
         */
        private String role;

        /**
         * 增量文本内容
         */
        private String content;

        /**
         * 工具调用增量列表
         */
        @JsonProperty("tool_calls")
        private List<ToolCallDelta> toolCalls;
    }

    /**
     * 工具调用增量
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolCallDelta {

        /**
         * 工具调用索引
         */
        private Integer index;

        /**
         * 工具调用 ID（仅首个 chunk 包含）
         */
        private String id;

        /**
         * 调用类型，通常为 "function"
         */
        private String type;

        /**
         * 函数调用增量
         */
        private FunctionCallDelta function;
    }

    /**
     * 函数调用增量
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FunctionCallDelta {

        /**
         * 函数名称（仅首个 chunk 包含）
         */
        private String name;

        /**
         * 函数参数增量 JSON 字符串
         */
        private String arguments;
    }
}
