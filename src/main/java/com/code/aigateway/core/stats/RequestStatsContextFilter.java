package com.code.aigateway.core.stats;

import com.code.aigateway.core.filter.CorrelationIdWebFilter;
import lombok.RequiredArgsConstructor;
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
 * 同时将请求注册到 {@link ActiveRequestTracker}，用于实时展示正在处理的请求。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class RequestStatsContextFilter implements WebFilter {

    private final ActiveRequestTracker activeRequestTracker;

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
        context.setRequestPath(path);
        context.setHttpMethod(exchange.getRequest().getMethod() != null
                ? exchange.getRequest().getMethod().name() : null);
        // 读取 CorrelationIdWebFilter 设置的链路追踪 ID
        context.setCorrelationId(exchange.getAttribute(CorrelationIdWebFilter.CORRELATION_ID_ATTR));
        exchange.getAttributes().put(RequestStatsContext.ATTRIBUTE_KEY, context);

        // 注册活跃请求到跟踪器（此时 model/stream 尚未解析，后续由 Controller 补充）
        activeRequestTracker.register(
                context.getCorrelationId(),
                context.getSourceIp(),
                null, null
        );

        // 使用 doFinally 确保请求无论成功、失败还是被拒绝，都能从跟踪器中移除
        // 防止因前置过滤器异常或请求被拒绝导致的活跃请求泄漏
        return chain.filter(exchange)
                .doFinally(signal -> activeRequestTracker.remove(context.getCorrelationId()));
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
