package com.code.aigateway.core.model;

import lombok.Data;

import java.util.List;

/**
 * 统一的消息
 */

@Data
public class UnifiedMessage {
    private String role;
    private List<UnifiedPart> parts;
    private String toolCallId;
    private String toolName;
}
