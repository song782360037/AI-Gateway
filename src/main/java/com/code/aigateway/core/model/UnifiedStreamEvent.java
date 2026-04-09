package com.code.aigateway.core.model;

import lombok.Data;

/**
 * 统一的流式事件
 * <p>
 * 表示流式响应中的单个事件，用于在流式传输过程中传递增量数据。
 * </p>
 *
 * @author sst
 */
@Data
public class UnifiedStreamEvent {

    /**
     * 事件类型
     * <p>
     * 支持的类型：
     * <ul>
     *   <li>text_delta: 文本增量</li>
     *   <li>thinking_delta: 思考内容增量</li>
     *   <li>tool_call: 工具调用</li>
     *   <li>done: 完成事件</li>
     *   <li>error: 错误事件</li>
     * </ul>
     * </p>
     */
    private String type;

    /**
     * 输出索引（用于多输出场景）
     */
    private Integer outputIndex;

    /**
     * 输出项 ID（主要用于 OpenAI Responses 的 item.id / item_id）
     */
    private String itemId;

    /**
     * 文本增量内容
     */
    private String textDelta;

    /**
     * 思考内容增量
     */
    private String thinkingDelta;

    /**
     * 工具调用 ID
     */
    private String toolCallId;

    /**
     * 工具名称
     */
    private String toolName;

    /**
     * 工具参数增量（JSON 字符串）
     */
    private String argumentsDelta;

    /**
     * Token 使用统计（通常在最后一个事件中返回）
     */
    private UnifiedUsage usage;

    /**
     * 完成原因（如 stop、length、tool_calls）
     */
    private String finishReason;

    /**
     * 错误码（错误事件时使用）
     */
    private String errorCode;

    /**
     * 错误消息（错误事件时使用）
     */
    private String errorMessage;
}
