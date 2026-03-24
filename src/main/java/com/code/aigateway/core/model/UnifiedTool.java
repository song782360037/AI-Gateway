package com.code.aigateway.core.model;

import lombok.Data;

import java.util.Map;

/**
 * 统一的工具定义
 * <p>
 * 表示一个可供 AI 调用的工具（函数），包含名称、描述和参数模式。
 * </p>
 *
 * @author sst
 */
@Data
public class UnifiedTool {

    /**
     * 工具/函数名称
     */
    private String name;

    /**
     * 工具/函数描述
     * <p>
     * 帮助 AI 理解何时以及如何使用此工具
     * </p>
     */
    private String description;

    /**
     * 工具类型
     */
    private String type;

    /**
     * 是否启用严格模式
     */
    private Boolean strict;

    /**
     * 输入参数的 JSON Schema
     * <p>
     * 定义函数参数的结构和类型
     * </p>
     */
    private Map<String, Object> inputSchema;
}
