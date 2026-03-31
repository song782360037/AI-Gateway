package com.code.aigateway.core.model;

import lombok.Data;

import java.util.List;

/**
 * 统一的输出模型
 * <p>
 * 表示 AI 生成的输出内容，包含角色和内容部分列表。
 * </p>
 *
 * @author sst
 */
@Data
public class UnifiedOutput {

    /**
     * 输出角色，通常为 "assistant"
     */
    private String role;

    /**
     * 输出内容部分列表
     */
    private List<UnifiedPart> parts;

    /**
     * 工具调用列表（当模型请求调用工具时）
     */
    private List<UnifiedToolCall> toolCalls;
}
