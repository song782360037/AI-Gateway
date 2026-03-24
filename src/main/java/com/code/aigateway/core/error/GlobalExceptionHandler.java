package com.code.aigateway.core.error;

import com.code.aigateway.api.response.OpenAiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;

import java.util.stream.Collectors;

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

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
    public ResponseEntity<OpenAiErrorResponse> handleGatewayException(GatewayException ex) {
        HttpStatus status = mapStatus(ex.getErrorCode());
        return ResponseEntity.status(status)
                .body(buildErrorResponse(ex.getMessage(), mapErrorType(ex.getErrorCode()), ex.getErrorCode().name(), ex.getParam()));
    }

    /**
     * 处理请求参数校验异常
     *
     * @param ex 参数校验异常
     * @return OpenAI 格式的错误响应
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<OpenAiErrorResponse> handleWebExchangeBindException(WebExchangeBindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        String param = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField())
                .orElse(null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildErrorResponse(message, "invalid_request_error", ErrorCode.INVALID_REQUEST.name(), param));
    }

    /**
     * 处理请求体输入异常
     *
     * @param ex 输入异常
     * @return OpenAI 格式的错误响应
     */
    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<OpenAiErrorResponse> handleServerWebInputException(ServerWebInputException ex) {
        String message = ex.getReason() == null ? "invalid request body" : ex.getReason();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildErrorResponse(message, "invalid_request_error", ErrorCode.INVALID_REQUEST.name(), null));
    }

    /**
     * 处理约束校验异常
     *
     * @param ex 约束校验异常
     * @return OpenAI 格式的错误响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<OpenAiErrorResponse> handleConstraintViolationException(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildErrorResponse(message, "invalid_request_error", ErrorCode.INVALID_REQUEST.name(), null));
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
    public ResponseEntity<OpenAiErrorResponse> handleException(Exception ex) {
        log.error("[OpenAI聊天接口] unexpected exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildErrorResponse("internal server error", "server_error", ErrorCode.INTERNAL_ERROR.name(), null));
    }

    /**
     * 创建 OpenAI 风格错误响应
     *
     * @param message 错误消息
     * @param type    错误类型
     * @param code    错误码
     * @param param   出错参数路径
     * @return 错误响应
     */
    private OpenAiErrorResponse buildErrorResponse(String message, String type, String code, String param) {
        return new OpenAiErrorResponse(
                new OpenAiErrorResponse.Error(message, type, code, param)
        );
    }

    /**
     * 根据错误码映射 OpenAI 风格错误类型
     *
     * @param errorCode 错误码
     * @return 错误类型
     */
    private String mapErrorType(ErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_REQUEST, MODEL_NOT_FOUND, CAPABILITY_NOT_SUPPORTED -> "invalid_request_error";
            case AUTH_FAILED -> "authentication_error";
            case PROVIDER_RATE_LIMIT -> "rate_limit_error";
            default -> "server_error";
        };
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
            case PROVIDER_NOT_FOUND, PROVIDER_DISABLED, PROVIDER_ERROR, STREAM_PARSE_ERROR -> HttpStatus.BAD_GATEWAY;
            case PROVIDER_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case PROVIDER_RATE_LIMIT -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
