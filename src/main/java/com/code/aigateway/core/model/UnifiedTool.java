package com.code.aigateway.core.model;

import lombok.Data;

import java.util.Map;

/**
 * 统一的工具
 */
@Data
public class UnifiedTool {
    private String name;
    private String description;
    private Map<String, Object> inputSchema;
}
