package com.code.aigateway.core.error;

import com.code.aigateway.core.protocol.ProtocolAdapter;
import com.code.aigateway.core.protocol.ProtocolResolver;
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
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * <p>
 * 统一处理网关中抛出的异常，根据当前请求的协议类型返回对应格式的错误响应。
 * 通过 ProtocolResolver 解析协议，委托 ProtocolAdapter 构建错误体。
 * </p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final RequestStatsCollector requestStatsCollector;
    private final List<ProtocolAdapter> protocolAdapters;

    public GlobalExceptionHandler(RequestStatsCollector requestStatsCollector, List<ProtocolAdapter> protocolAdapters) {
        this.requestStatsCollector = requestStatsCollector;
        this.protocolAdapters = protocolAdapters;
    }

    /**
     * 处理网关业务异常。
     * <p>
     * 对于 SSE/流式响应，如果响应头已经提交，则不能再尝试构造新的 ResponseEntity，
     * 否则会触发 ReadOnlyHttpHeaders/response already committed 异常。
     * </p>
     */
    @ExceptionHandler(GatewayException.class)
    public Mono<ResponseEntity<?>> handleGatewayException(GatewayException ex, ServerWebExchange exchange) {
        collectStats(exchange, ex);
        if (exchange.getResponse().isCommitted()) {
            log.warn("[网关] response already committed, skip gateway error rendering: {}", ex.getMessage());
            return Mono.empty();
        }
        ProtocolAdapter adapter = resolveAdapter(exchange);
        HttpStatus status = mapStatus(ex.getErrorCode());
        return Mono.just(ResponseEntity.status(status)
                .body(adapter.buildError(
                        ex.getMessage(),
                        adapter.mapErrorType(ex.getErrorCode()),
                        ex.getErrorCode().name(),
                        ex.getParam()
                )));
    }

    /**
     * 处理请求参数校验异常
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<?>> handleWebExchangeBindException(WebExchangeBindException ex, ServerWebExchange exchange) {
        collectStats(exchange, ex);
        if (exchange.getResponse().isCommitted()) {
            log.warn("[网关] response already committed, skip validation error rendering: {}", ex.getMessage());
            return Mono.empty();
        }
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        String param = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField())
                .orElse(null);
        return Mono.just(buildClientError(exchange, message, ErrorCode.INVALID_REQUEST, param));
    }

    /**
     * 处理请求体输入异常
     */
    @ExceptionHandler(ServerWebInputException.class)
    public Mono<ResponseEntity<?>> handleServerWebInputException(ServerWebInputException ex, ServerWebExchange exchange) {
        collectStats(exchange, ex);
        if (exchange.getResponse().isCommitted()) {
            log.warn("[网关] response already committed, skip input error rendering: {}", ex.getMessage());
            return Mono.empty();
        }
        String message = ex.getReason() == null ? "invalid request body" : ex.getReason();
        return Mono.just(buildClientError(exchange, message, ErrorCode.INVALID_REQUEST, null));
    }

    /**
     * 处理约束校验异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Mono<ResponseEntity<?>> handleConstraintViolationException(ConstraintViolationException ex, ServerWebExchange exchange) {
        collectStats(exchange, ex);
        if (exchange.getResponse().isCommitted()) {
            log.warn("[网关] response already committed, skip constraint violation rendering: {}", ex.getMessage());
            return Mono.empty();
        }
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .collect(Collectors.joining("; "));
        return Mono.just(buildClientError(exchange, message, ErrorCode.INVALID_REQUEST, null));
    }

    /**
     * 处理未知异常
     */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<?>> handleException(Exception ex, ServerWebExchange exchange) {
        log.error("[网关] unexpected exception", ex);
        collectStats(exchange, ex);
        if (exchange.getResponse().isCommitted()) {
            log.warn("[网关] response already committed, skip internal error rendering: {}", ex.getMessage());
            return Mono.empty();
        }
        ProtocolAdapter adapter = resolveAdapter(exchange);
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(adapter.buildError(
                        "internal server error",
                        adapter.mapErrorType(ErrorCode.INTERNAL_ERROR),
                        ErrorCode.INTERNAL_ERROR.name(),
                        null
                )));
    }

    /**
     * 从 exchange 中取出统计上下文，如果存在则记录失败事件
     */
    private void collectStats(ServerWebExchange exchange, Throwable ex) {
        RequestStatsContext context = exchange.getAttribute(RequestStatsContext.ATTRIBUTE_KEY);
        requestStatsCollector.collectError(context, ex);
    }

    /**
     * 构建客户端错误响应（BAD_REQUEST）
     */
    private ResponseEntity<?> buildClientError(ServerWebExchange exchange, String message, ErrorCode errorCode, String param) {
        ProtocolAdapter adapter = resolveAdapter(exchange);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(adapter.buildError(
                        message,
                        adapter.mapErrorType(errorCode),
                        errorCode.name(),
                        param
                ));
    }

    /**
     * 根据协议类型解析对应的适配器
     */
    private ProtocolAdapter resolveAdapter(ServerWebExchange exchange) {
        com.code.aigateway.core.model.ResponseProtocol protocol = ProtocolResolver.fromExchange(exchange);
        return protocolAdapters.stream()
                .filter(a -> a.getProtocol() == protocol)
                .findFirst()
                .orElseGet(() -> protocolAdapters.stream()
                        .filter(a -> a.getProtocol() == com.code.aigateway.core.model.ResponseProtocol.OPENAI_CHAT)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("no OpenAI Chat protocol adapter found")));
    }

    private HttpStatus mapStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_REQUEST, MODEL_NOT_FOUND, CAPABILITY_NOT_SUPPORTED -> HttpStatus.BAD_REQUEST;
            case AUTH_FAILED -> HttpStatus.UNAUTHORIZED;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case PROVIDER_RATE_LIMIT -> HttpStatus.TOO_MANY_REQUESTS;
            case PROVIDER_CIRCUIT_OPEN -> HttpStatus.SERVICE_UNAVAILABLE;
            case PROVIDER_AUTH_ERROR, PROVIDER_BAD_REQUEST, PROVIDER_RESOURCE_NOT_FOUND -> HttpStatus.BAD_GATEWAY;
            case PROVIDER_NOT_FOUND, PROVIDER_DISABLED, PROVIDER_ERROR, STREAM_PARSE_ERROR -> HttpStatus.BAD_GATEWAY;
            case PROVIDER_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
