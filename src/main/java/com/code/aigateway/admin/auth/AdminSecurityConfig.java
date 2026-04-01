package com.code.aigateway.admin.auth;

import com.code.aigateway.common.result.R;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 管理后台 Spring Security 配置
 * <p>
 * JWT 认证由 JwtAuthWebFilter 独立处理（基于 WebFilter），
 * 此配置定义路径级别的访问控制策略和 401 响应格式。
 * </p>
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class AdminSecurityConfig {

    private final ObjectMapper objectMapper;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // 登录接口放行
                        .pathMatchers("/admin/login").permitAll()
                        // SpringDoc OpenAPI 文档路径放行
                        .pathMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // 管理后台路径需要认证
                        .pathMatchers("/admin/**").authenticated()
                        // 其余路径全部放行（/v1/** 由 ApiKeyAuthWebFilter 独立处理）
                        .anyExchange().permitAll()
                )
                // 自定义 401 响应：返回 R<> 格式
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(this::handleUnauthorized)
                )
                .build();
    }

    /**
     * 处理 401 未认证响应
     */
    private Mono<Void> handleUnauthorized(ServerWebExchange exchange,
                                           org.springframework.security.core.AuthenticationException ex) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        R<Void> body = R.fail("UNAUTHORIZED", "未登录或 Token 已过期");
        byte[] bytes = toJsonBytes(body);
        return response.writeWith(Mono.just(
                response.bufferFactory().wrap(bytes)));
    }

    private byte[] toJsonBytes(Object obj) {
        try {
            return objectMapper.writeValueAsBytes(obj);
        } catch (JsonProcessingException e) {
            return "{\"success\":false,\"code\":\"UNAUTHORIZED\"}".getBytes(StandardCharsets.UTF_8);
        }
    }
}
