package com.code.aigateway.core.error;

import com.code.aigateway.api.response.OpenAiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * <p>
 * 统一处理网关中抛出的异常，将其转换为 OpenAI 格式的错误响应。
 * 确保所有错误都以一致的格式返回给客户端。
 * </p>
 *
 * @author sst
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理网关业务异常
     * <p>
     * 根据错误码映射 HTTP 状态码，并返回 OpenAI 格式的错误响应
     * </p>
     *
     * @param ex 网关异常
     * @return OpenAI 格式的错误响应
     */
    @ExceptionHandler(GatewayException.class)
    public org.springframework.http.ResponseEntity<OpenAiErrorResponse> handleGatewayException(GatewayException ex) {
        HttpStatus status = mapStatus(ex.getErrorCode());
        return org.springframework.http.ResponseEntity.status(status)
                .body(new OpenAiErrorResponse(
                        new OpenAiErrorResponse.Error(
                                ex.getMessage(),
                                "invalid_request_error",
                                ex.getErrorCode().name()
                        )
                ));
    }

    /**
     * 处理未知异常
     * <p>
     * 对于未预期的异常，返回 500 内部服务器错误
     * </p>
     *
     * @param ex 异常
     * @return OpenAI 格式的错误响应
     */
    @ExceptionHandler(Exception.class)
    public org.springframework.http.ResponseEntity<OpenAiErrorResponse> handleException(Exception ex) {
        return org.springframework.http.ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new OpenAiErrorResponse(
                        new OpenAiErrorResponse.Error(
                                ex.getMessage(),
                                "server_error",
                                ErrorCode.INTERNAL_ERROR.name()
                        )
                ));
    }

    /**
     * 将错误码映射为 HTTP 状态码
     *
     * @param errorCode 错误码
     * @return 对应的 HTTP 状态码
     */
    private HttpStatus mapStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_REQUEST, MODEL_NOT_FOUND, CAPABILITY_NOT_SUPPORTED -> HttpStatus.BAD_REQUEST;
            case AUTH_FAILED -> HttpStatus.UNAUTHORIZED;
            case PROVIDER_NOT_FOUND, PROVIDER_DISABLED -> HttpStatus.BAD_GATEWAY;
            case PROVIDER_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case PROVIDER_RATE_LIMIT -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
