package com.code.aigateway.core.error;

import lombok.Getter;

/**
 * 网关异常
 * <p>
 * 网关中业务异常的统一封装，包含错误码、错误消息和参数路径。
 * 用于在业务处理过程中抛出可识别的错误，由全局异常处理器统一处理。
 * </p>
 *
 * @author sst
 */
@Getter
public class GatewayException extends RuntimeException {

    /**
     * 错误码
     */
    private final ErrorCode errorCode;

    /**
     * 出错参数路径
     */
    private final String param;

    /**
     * 构造网关异常
     *
     * @param errorCode 错误码
     * @param message   错误消息
     */
    public GatewayException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    /**
     * 构造网关异常
     *
     * @param errorCode 错误码
     * @param message   错误消息
     * @param param     出错参数路径
     */
    public GatewayException(ErrorCode errorCode, String message, String param) {
        super(message);
        this.errorCode = errorCode;
        this.param = param;
    }
}
