package com.code.aigateway.sdk.model;

import lombok.Data;

import java.util.List;

/**
 * 统一的输出模型
 * <p>
 * 表示 AI 生成的输出内容，包含角色、内容部分和工具调用。
 * </p>
 */
@Data
public class UnifiedOutput {

    /** 输出角色 */
    private String role;

    /** 内容部分列表 */
    private List<UnifiedPart> parts;

    /** 工具调用列表 */
    private List<UnifiedToolCall> toolCalls;
}
