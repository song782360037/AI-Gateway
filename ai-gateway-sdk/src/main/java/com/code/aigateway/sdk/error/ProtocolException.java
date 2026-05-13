package com.code.aigateway.sdk.error;

import lombok.Getter;

/**
 * 协议转换异常
 * <p>
 * SDK 中协议转换过程异常的统一封装，包含错误码和错误消息。
 * </p>
 */
@Getter
public class ProtocolException extends RuntimeException {

    /** 错误码 */
    private final ErrorCode errorCode;

    /** 出错参数路径，用于定位错误位置 */
    private final String param;

    public ProtocolException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public ProtocolException(ErrorCode errorCode, String message, String param) {
        super(message);
        this.errorCode = errorCode;
        this.param = param;
    }
}
