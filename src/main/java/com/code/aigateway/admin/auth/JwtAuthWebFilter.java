package com.code.aigateway.admin.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * JWT 认证 WebFilter
 * <p>
 * 必须在 Spring Security 的 WebFilterChainProxy（order=-100）之前执行，
 * 否则 Security 先检查到无认证直接返回 401，JWT 过滤器来不及设置上下文。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(-200)
public class JwtAuthWebFilter implements WebFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final JwtUtil jwtUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // 非管理路径直接放行
        if (!path.startsWith("/admin/")) {
            return chain.filter(exchange);
        }

        // 登录接口放行
        if ("/admin/login".equals(path)) {
            return chain.filter(exchange);
        }

        // 提取 Bearer Token
        String token = extractBearerToken(exchange.getRequest());
        if (token == null) {
            // 无 Token，交给 Spring Security 的 authorizeExchange 处理 401
            return chain.filter(exchange);
        }

        // 验证 Token 并设置认证上下文
        try {
            var claims = jwtUtil.validateToken(token);
            String username = claims.getSubject();
            var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    username, token,
                    java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(ROLE_ADMIN)));

            return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
        } catch (Exception e) {
            log.debug("[JWT认证] Token 验证失败: {}", e.getMessage());
            // Token 无效，不设置上下文，Spring Security 会返回 401
            return chain.filter(exchange);
        }
    }

    /**
     * 从请求头提取 Bearer Token
     */
    private String extractBearerToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
