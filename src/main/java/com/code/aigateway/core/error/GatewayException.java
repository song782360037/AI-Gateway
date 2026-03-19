package com.code.aigateway.core.error;

import lombok.Getter;

/**
 * 异常处理
 */
@Getter
public class GatewayException extends RuntimeException {

    private final ErrorCode errorCode;

    public GatewayException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
