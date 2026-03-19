package com.code.aigateway.core.model;

import lombok.Data;

import java.util.List;

/**
 * 统一的响应
 */
@Data
public class UnifiedResponse {
    private String id;
    private String model;
    private String provider;
    private String finishReason;
    private UnifiedUsage usage;
    private List<UnifiedOutput> outputs;
}
