package com.code.aigateway.api.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OpenAiErrorResponse {
    private Error error;

    @Data
    @AllArgsConstructor
    public static class Error {
        private String message;
        private String type;
        private String code;
    }
}
