package com.code.aigateway.core.model;

import lombok.Data;

/**
 * 统一的工具调用模型
 * <p>
 * 表示 assistant 历史消息中的一次工具调用。
 * </p>
 *
 * @author sst
 */
@Data
public class UnifiedToolCall {

    /**
     * 工具调用 ID
     */
    private String id;

    /**
     * 工具调用类型
     */
    private String type;

    /**
     * 工具名称
     */
    private String toolName;

    /**
     * 函数参数 JSON 字符串
     */
    private String argumentsJson;
}
