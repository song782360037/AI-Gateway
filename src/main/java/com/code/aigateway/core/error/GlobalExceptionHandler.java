package com.code.aigateway.core.error;

import com.code.aigateway.api.response.OpenAiErrorResponse;
import com.code.aigateway.core.stats.RequestStatsCollector;
import com.code.aigateway.core.stats.RequestStatsContext;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
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

    private final RequestStatsCollector requestStatsCollector;

    public GlobalExceptionHandler(RequestStatsCollector requestStatsCollector) {
        this.requestStatsCollector = requestStatsCollector;
    }

    /**
     * 处理网关业务异常
     */
    @ExceptionHandler(GatewayException.class)
    public ResponseEntity<OpenAiErrorResponse> handleGatewayException(GatewayException ex, ServerWebExchange exchange) {
        collectStats(exchange, ex);
        HttpStatus status = mapStatus(ex.getErrorCode());
        return ResponseEntity.status(status)
                .body(buildErrorResponse(ex.getMessage(), mapErrorType(ex.getErrorCode()), ex.getErrorCode().name(), ex.getParam()));
    }

    /**
     * 处理请求参数校验异常
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<OpenAiErrorResponse> handleWebExchangeBindException(WebExchangeBindException ex, ServerWebExchange exchange) {
        collectStats(exchange, ex);
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
     */
    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<OpenAiErrorResponse> handleServerWebInputException(ServerWebInputException ex, ServerWebExchange exchange) {
        collectStats(exchange, ex);
        String message = ex.getReason() == null ? "invalid request body" : ex.getReason();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildErrorResponse(message, "invalid_request_error", ErrorCode.INVALID_REQUEST.name(), null));
    }

    /**
     * 处理约束校验异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<OpenAiErrorResponse> handleConstraintViolationException(ConstraintViolationException ex, ServerWebExchange exchange) {
        collectStats(exchange, ex);
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildErrorResponse(message, "invalid_request_error", ErrorCode.INVALID_REQUEST.name(), null));
    }

    /**
     * 处理未知异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<OpenAiErrorResponse> handleException(Exception ex, ServerWebExchange exchange) {
        log.error("[OpenAI聊天接口] unexpected exception", ex);
        collectStats(exchange, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildErrorResponse("internal server error", "server_error", ErrorCode.INTERNAL_ERROR.name(), null));
    }

    /**
     * 从 exchange 中取出统计上下文，如果存在则记录失败事件
     */
    private void collectStats(ServerWebExchange exchange, Throwable ex) {
        RequestStatsContext context = exchange.getAttribute(RequestStatsContext.ATTRIBUTE_KEY);
        requestStatsCollector.collectError(context, ex);
    }

    private OpenAiErrorResponse buildErrorResponse(String message, String type, String code, String param) {
        return new OpenAiErrorResponse(
                new OpenAiErrorResponse.Error(message, type, code, param)
        );
    }

    private String mapErrorType(ErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_REQUEST, MODEL_NOT_FOUND, CAPABILITY_NOT_SUPPORTED -> "invalid_request_error";
            case AUTH_FAILED -> "authentication_error";
            case PROVIDER_RATE_LIMIT -> "rate_limit_error";
            default -> "server_error";
        };
    }

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
