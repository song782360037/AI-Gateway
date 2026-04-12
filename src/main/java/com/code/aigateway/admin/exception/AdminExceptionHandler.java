package com.code.aigateway.admin.exception;

import com.code.aigateway.common.exception.BizException;
import com.code.aigateway.common.result.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 管理端全局异常处理器
 *
 * <p>专门处理 /admin/** 路径下的管理接口异常，
 * 返回统一的 R 格式响应，区别于网关 OpenAI 风格错误。</p>
 */
@RestControllerAdvice(basePackages = "com.code.aigateway.admin")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AdminExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> handleBizException(BizException ex) {
        log.warn("[管理接口异常] code: {}, message: {}", ex.getCode(), ex.getMessage());
        HttpStatus status = "REQUEST_LOG_NOT_FOUND".equals(ex.getCode())
                ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(R.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleMethodArgumentNotValidException(
            org.springframework.web.bind.MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("[管理接口参数校验] {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail("INVALID_PARAM", message));
    }

    /**
     * 处理 WebFlux 环境下 @Valid 校验失败抛出的异常
     * <p>WebFlux 使用 WebExchangeBindException（继承自 BindException），
     * 区别于 Spring MVC 的 MethodArgumentNotValidException。</p>
     */
    @ExceptionHandler(org.springframework.web.bind.support.WebExchangeBindException.class)
    public ResponseEntity<R<Void>> handleWebExchangeBindException(
            org.springframework.web.bind.support.WebExchangeBindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("[管理接口参数校验] {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail("INVALID_PARAM", message));
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<R<Void>> handleConstraintViolationException(
            jakarta.validation.ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("[管理接口约束校验] {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail("INVALID_PARAM", message));
    }

    /**
     * 兜底异常处理：捕获所有未预期的异常，避免敏感信息泄露。
     * <p>返回通用错误提示，不在响应体中暴露内部堆栈或类名。</p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleUnexpectedException(Exception ex) {
        log.error("[管理接口未预期异常]", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail("INTERNAL_ERROR", "服务内部错误，请稍后重试"));
    }
}
