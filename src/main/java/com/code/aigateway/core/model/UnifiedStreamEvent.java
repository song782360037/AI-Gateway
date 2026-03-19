package com.code.aigateway.core.model;

import lombok.Data;

@Data
public class UnifiedStreamEvent {
    private String type;
    private Integer outputIndex;
    private String textDelta;
    private String toolCallId;
    private String toolName;
    private String argumentsDelta;
    private UnifiedUsage usage;
    private String finishReason;
    private String errorCode;
    private String errorMessage;
}
