package com.code.aigateway.core.model;

import lombok.Data;

import java.util.List;

/**
 * 统一的输出
 */
@Data
public class UnifiedOutput {
    private String role;
    private List<UnifiedPart> parts;
}
