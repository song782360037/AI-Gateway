package com.code.aigateway.api.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * OpenAI 格式的错误响应
 * <p>
 * 当请求发生错误时，返回此格式的响应，与 OpenAI API 的错误格式保持一致。
 * </p>
 *
 * @author sst
 */
@Data
@AllArgsConstructor
public class OpenAiErrorResponse {

    /**
     * 错误详情
     */
    private Error error;

    /**
     * 错误详情类
     */
    @Data
    @AllArgsConstructor
    public static class Error {

        /**
         * 错误消息
         */
        private String message;

        /**
         * 错误类型
         */
        private String type;

        /**
         * 错误码
         */
        private String code;
    }
}
