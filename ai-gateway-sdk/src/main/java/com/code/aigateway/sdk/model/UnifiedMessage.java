package com.code.aigateway.sdk.model;

import lombok.Data;

import java.util.List;

/**
 * 统一的消息模型
 * <p>
 * 表示对话中的一条消息，支持多种角色和内容类型。
 * </p>
 */
@Data
public class UnifiedMessage {

    /** 消息角色：system / user / assistant / tool */
    private String role;

    /** 消息内容部分列表 */
    private List<UnifiedPart> parts;

    /** 工具调用 ID（role 为 tool 时使用） */
    private String toolCallId;

    /** 工具名称 */
    private String toolName;

    /** assistant 历史消息中的工具调用列表 */
    private List<UnifiedToolCall> toolCalls;
}
