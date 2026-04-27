package com.code.aigateway.admin.auth;

import com.code.aigateway.admin.service.IAdminAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Set;

/**
 * 基于 HttpOnly Cookie + 数据库会话的后台认证过滤器
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(-200)
public class AdminSessionAuthWebFilter implements WebFilter {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/admin/login",
            "/admin/bootstrap/status",
            "/admin/bootstrap/setup"
    );

    private final AdminSessionCookieManager cookieManager;
    private final IAdminAuthService adminAuthService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/admin/")) {
            return chain.filter(exchange);
        }
        if (PUBLIC_PATHS.contains(path)) {
            return chain.filter(exchange);
        }

        String sessionToken = cookieManager.resolveSessionToken(exchange.getRequest());
        if (sessionToken == null) {
            return chain.filter(exchange);
        }

        return Mono.fromCallable(() -> adminAuthService.authenticate(sessionToken))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(authenticatedAdmin -> {
                    if (authenticatedAdmin == null) {
                        return chain.filter(exchange);
                    }
                    var authentication = new UsernamePasswordAuthenticationToken(
                            authenticatedAdmin.username(),
                            sessionToken,
                            List.of(new SimpleGrantedAuthority(ROLE_ADMIN)));
                    return chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
                })
                .onErrorResume(ex -> {
                    log.debug("[后台认证] 会话校验失败: {}", ex.getMessage());
                    return chain.filter(exchange);
                });
    }
}
