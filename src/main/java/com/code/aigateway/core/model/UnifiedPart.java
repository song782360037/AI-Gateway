package com.code.aigateway.core.model;

import lombok.Data;

import java.util.Map;

@Data
public class UnifiedPart {
    private String type;
    private String text;
    private String mimeType;
    private String url;
    private String base64Data;
    private Map<String, Object> attributes;
}
