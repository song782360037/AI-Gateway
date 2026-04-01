package com.code.aigateway.core.stats;

import com.code.aigateway.core.filter.CorrelationIdWebFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * 请求统计上下文初始化过滤器
 * <p>
 * 在请求进入控制器前初始化统计上下文，避免在多个层级重复解析来源信息。
 * </p>
 */
@Component
public class RequestStatsContextFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 只对网关转发路径初始化统计上下文，管理后台和健康检查等无需统计
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/v1/") && !path.startsWith("/v1beta/")) {
            return chain.filter(exchange);
        }

        RequestStatsContext context = new RequestStatsContext();
        context.setStartTimeMs(System.currentTimeMillis());
        context.setSourceIp(resolveSourceIp(exchange.getRequest()));
        // 读取 CorrelationIdWebFilter 设置的链路追踪 ID
        context.setCorrelationId(exchange.getAttribute(CorrelationIdWebFilter.CORRELATION_ID_ATTR));
        exchange.getAttributes().put(RequestStatsContext.ATTRIBUTE_KEY, context);
        return chain.filter(exchange);
    }

    /**
     * 解析来源 IP，优先取 X-Forwarded-For，取不到时回退到 remoteAddress
     */
    private String resolveSourceIp(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int commaIndex = forwardedFor.indexOf(',');
            return commaIndex >= 0 ? forwardedFor.substring(0, commaIndex).trim() : forwardedFor.trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        return remoteAddress != null && remoteAddress.getAddress() != null
                ? remoteAddress.getAddress().getHostAddress()
                : "unknown";
    }
}
