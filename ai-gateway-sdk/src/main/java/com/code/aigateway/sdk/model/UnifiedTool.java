package com.code.aigateway.sdk.model;

import lombok.Data;

import java.util.Map;

/**
 * 统一的工具定义
 */
@Data
public class UnifiedTool {

    /** 工具/函数名称 */
    private String name;

    /** 工具描述 */
    private String description;

    /** 工具类型 */
    private String type;

    /** 是否严格模式 */
    private Boolean strict;

    /** 输入参数 JSON Schema */
    private Map<String, Object> inputSchema;
}
