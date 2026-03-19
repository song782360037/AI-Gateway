package com.code.aigateway.core.model;

import lombok.Data;

import java.util.Map;

/**
 * 统一的响应格式
 */
@Data
public class UnifiedResponseFormat {
    private String type;
    private Map<String, Object> schema;
}
