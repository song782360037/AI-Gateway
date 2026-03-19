package com.code.aigateway.core.error;

import com.code.aigateway.api.response.OpenAiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

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
