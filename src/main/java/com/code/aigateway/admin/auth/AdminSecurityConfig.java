package com.code.aigateway.admin.auth;

import com.code.aigateway.common.result.R;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 管理后台 Spring Security 配置
 * <p>
 * 认证由 AdminSessionAuthWebFilter 基于 HttpOnly Cookie + 数据库会话完成，
 * 此处只定义公开路径、受保护路径以及统一的 401 响应格式。
 * </p>
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class AdminSecurityConfig {

    private final ObjectMapper objectMapper;
    private final AdminSessionCookieManager cookieManager;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/admin/login", "/admin/bootstrap/**").permitAll()
                        .pathMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .pathMatchers("/admin/**").authenticated()
                        .anyExchange().permitAll()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(this::handleUnauthorized)
                )
                .build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 处理 401 未认证响应。
     */
    private Mono<Void> handleUnauthorized(ServerWebExchange exchange,
                                          org.springframework.security.core.AuthenticationException ex) {
        ServerHttpResponse response = exchange.getResponse();
        cookieManager.clearSessionCookie(response, exchange.getRequest());
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        R<Void> body = R.fail("UNAUTHORIZED", "未登录或登录状态已失效");
        byte[] bytes = toJsonBytes(body);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    private byte[] toJsonBytes(Object obj) {
        try {
            return objectMapper.writeValueAsBytes(obj);
        } catch (JsonProcessingException e) {
            return "{\"success\":false,\"code\":\"UNAUTHORIZED\"}".getBytes(StandardCharsets.UTF_8);
        }
    }
}
