package com.code.aigateway.api.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Google Gemini API 错误响应
 */
@Data
@AllArgsConstructor
public class GeminiErrorResponse {

    private ErrorDetail error;

    @Data
    @AllArgsConstructor
    public static class ErrorDetail {
        private Integer code;
        private String message;
        private String status;
    }
}
