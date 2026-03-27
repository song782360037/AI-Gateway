package com.code.aigateway.common.exception;

import lombok.Getter;

/**
 * 管理端业务异常
 */
@Getter
public class BizException extends RuntimeException {

    /**
     * 业务异常码，便于上层统一转换为标准响应
     */
    private final String code;

    public BizException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
