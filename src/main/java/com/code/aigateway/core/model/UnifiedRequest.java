package com.code.aigateway.core.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 统一的请求
 */
@Data
public class UnifiedRequest {
    private String requestProtocol;
    private String responseProtocol;
    private String provider;
    private String model;
    private String systemPrompt;

    private List<UnifiedMessage> messages;
    private List<UnifiedTool> tools;
    private UnifiedToolChoice toolChoice;

    private UnifiedGenerationConfig generationConfig;
    private UnifiedResponseFormat responseFormat;

    private Boolean stream;
    private Map<String, Object> metadata;
}
