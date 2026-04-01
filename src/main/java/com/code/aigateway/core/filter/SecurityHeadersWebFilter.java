package com.code.aigateway.core.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 安全响应头过滤器
 * <p>
 * 为所有 HTTP 响应添加标准安全头，防止常见 Web 攻击。
 * 执行顺序在 CorrelationId 之后、认证之前。
 * </p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class SecurityHeadersWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpResponse response = exchange.getResponse();
        var headers = response.getHeaders();

        // 防止 MIME 类型嗅探
        headers.set("X-Content-Type-Options", "nosniff");
        // 禁止页面被嵌入 iframe（防止点击劫持）
        headers.set("X-Frame-Options", "DENY");
        // 禁用浏览器内置 XSS 过滤器（现代安全实践推荐禁用，由 CSP 替代）
        headers.set("X-XSS-Protection", "0");
        // 控制来源信息泄露
        headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
        // 内容安全策略
        headers.set("Content-Security-Policy", "default-src 'self'");
        // 禁用浏览器不必要的 API 权限
        headers.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        // 移除服务器标识
        headers.remove("Server");

        return chain.filter(exchange);
    }
}
