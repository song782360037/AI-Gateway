package com.code.aigateway.core.model;

import lombok.Data;

import java.util.List;

/**
 * 统一的消息模型
 * <p>
 * 表示对话中的一条消息，支持多种角色和内容类型。
 * </p>
 *
 * @author sst
 */
@Data
public class UnifiedMessage {

    /**
     * 消息角色
     * <p>
     * 支持值：system、user、assistant、tool
     * </p>
     */
    private String role;

    /**
     * 消息内容部分列表
     * <p>
     * 支持多模态内容（文本、图片等）
     * </p>
     */
    private List<UnifiedPart> parts;

    /**
     * 工具调用 ID（当角色为 tool 时使用）
     */
    private String toolCallId;

    /**
     * 工具名称
     */
    private String toolName;
}
