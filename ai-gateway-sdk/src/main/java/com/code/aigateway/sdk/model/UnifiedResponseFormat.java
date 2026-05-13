package com.code.aigateway.sdk.model;

import lombok.Data;

import java.util.Map;

/**
 * 统一的响应格式配置
 */
@Data
public class UnifiedResponseFormat {

    /** 格式类型：text / json_object / json_schema */
    private String type;

    /** JSON Schema 名称 */
    private String name;

    /** 是否严格模式 */
    private Boolean strict;

    /** JSON Schema */
    private Map<String, Object> schema;
}
