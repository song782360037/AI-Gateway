package com.code.aigateway.api.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Anthropic Messages API 错误响应
 */
@Data
@AllArgsConstructor
public class AnthropicErrorResponse {

    private String type;
    private ErrorDetail error;

    @Data
    @AllArgsConstructor
    public static class ErrorDetail {
        private String type;
        private String message;
    }
}
