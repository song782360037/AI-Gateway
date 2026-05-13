package com.code.aigateway.sdk.model;

import lombok.Data;

/**
 * 统一的流式事件
 * <p>
 * 表示流式响应中的单个事件。事件类型包括：
 * text_delta / thinking_delta / tool_call / tool_call_delta / done / error
 * </p>
 */
@Data
public class UnifiedStreamEvent {

    /** 仅携带 usage 的事件类型，用于触发协议适配器生成 message_start 等初始化事件 */
    public static final String TYPE_USAGE_ONLY = "usage_only";

    /** 事件类型 */
    private String type;

    /** 输出索引（多输出场景） */
    private Integer outputIndex;

    /** 输出项 ID（OpenAI Responses 使用） */
    private String itemId;

    /** 文本增量 */
    private String textDelta;

    /** 思考内容增量 */
    private String thinkingDelta;

    /** 工具调用 ID */
    private String toolCallId;

    /** 工具名称 */
    private String toolName;

    /** 工具参数增量 JSON */
    private String argumentsDelta;

    /** Token 使用统计（done 事件中返回） */
    private UnifiedUsage usage;

    /** 完成原因 */
    private String finishReason;

    /** 错误码 */
    private String errorCode;

    /** 错误消息 */
    private String errorMessage;

    // ===================== 静态工厂方法 =====================

    public static UnifiedStreamEvent textDelta(String text) {
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("text_delta");
        event.setTextDelta(text);
        return event;
    }

    public static UnifiedStreamEvent thinkingDelta(String thinking) {
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("thinking_delta");
        event.setThinkingDelta(thinking);
        return event;
    }

    public static UnifiedStreamEvent toolCall(String toolCallId, String toolName, Integer outputIndex) {
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("tool_call");
        event.setToolCallId(toolCallId);
        event.setToolName(toolName);
        event.setOutputIndex(outputIndex);
        return event;
    }

    public static UnifiedStreamEvent toolCallDelta(Integer outputIndex, String argumentsDelta) {
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("tool_call_delta");
        event.setOutputIndex(outputIndex);
        event.setArgumentsDelta(argumentsDelta);
        return event;
    }

    public static UnifiedStreamEvent done(String finishReason, UnifiedUsage usage) {
        UnifiedStreamEvent event = new UnifiedStreamEvent();
        event.setType("done");
        event.setFinishReason(finishReason);
        event.setUsage(usage);
        return event;
    }

    public static UnifiedStreamEvent done(String finishReason) {
        return done(finishReason, null);
    }
}
