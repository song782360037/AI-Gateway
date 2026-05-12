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

    /** 错误码 */
    private final ErrorCode errorCode;

    /** 出错参数路径 */
    private final String param;

    /** 上游 HTTP 状态码（如 429、500），仅来自上游响应时填充 */
    private final Integer upstreamHttpStatus;

    /** 上游错误类型（如 rate_limit_exceeded、overloaded_error），仅来自上游响应时填充 */
    private final String upstreamErrorType;

    public GatewayException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, null, null);
    }

    public GatewayException(ErrorCode errorCode, String message, String param) {
        this(errorCode, message, param, null, null);
    }

    /**
     * 完整构造，包含上游错误上下文
     *
     * @param errorCode         错误码
     * @param message           错误消息
     * @param param             出错参数路径
     * @param upstreamHttpStatus 上游 HTTP 状态码
     * @param upstreamErrorType  上游错误类型
     */
    public GatewayException(ErrorCode errorCode, String message, String param,
                            Integer upstreamHttpStatus, String upstreamErrorType) {
        super(message);
        this.errorCode = errorCode;
        this.param = param;
        this.upstreamHttpStatus = upstreamHttpStatus;
        this.upstreamErrorType = upstreamErrorType;
    }
}
