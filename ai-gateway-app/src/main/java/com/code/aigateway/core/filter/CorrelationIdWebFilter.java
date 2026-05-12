package com.code.aigateway.core.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

/**
 * 请求链路追踪过滤器
 * <p>
 * 为每个入站请求生成或复用唯一的 X-Request-Id，
 * 贯穿整个请求生命周期，用于日志关联和问题排查。
 * <ul>
 *   <li>优先读取请求头中已有的 X-Request-Id</li>
 *   <li>不存在时生成 UUID v4</li>
 *   <li>存入 exchange attributes 供下游使用</li>
 *   <li>写入响应头供客户端获取</li>
 * </ul>
 * </p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    /** exchange attribute key */
    public static final String CORRELATION_ID_ATTR = CorrelationIdWebFilter.class.getName() + ".correlationId";

    /** 请求头名称 */
    public static final String X_REQUEST_ID = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 从请求头提取，不存在则生成
        String correlationId = Optional.ofNullable(
                exchange.getRequest().getHeaders().getFirst(X_REQUEST_ID)
        ).filter(id -> !id.isBlank()).orElseGet(() -> UUID.randomUUID().toString());

        // 存入 exchange attributes，供后续 filter/service 读取
        exchange.getAttributes().put(CORRELATION_ID_ATTR, correlationId);

        // 在响应头中返回
        exchange.getResponse().getHeaders().set(X_REQUEST_ID, correlationId);

        return chain.filter(exchange);
    }
}
