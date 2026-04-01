package com.code.aigateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * CORS 跨域配置
 * <p>
 * 通过 CorsWebFilter 为 WebFlux 应用配置跨域策略，
 * 配置项来源于 gateway.cors.* 前缀。
 * 设置 gateway.cors.enabled=false 可关闭。
 * </p>
 */
@Configuration
@RequiredArgsConstructor
public class CorsConfig {

    private final GatewayProperties gatewayProperties;

    @Bean
    @ConditionalOnProperty(prefix = "gateway.cors", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CorsWebFilter corsWebFilter() {
        GatewayProperties.CorsProperties cors = gatewayProperties.getCors();
        CorsConfiguration config = new CorsConfiguration();

        if (cors != null) {
            cors.getAllowedOrigins().forEach(config::addAllowedOrigin);
            cors.getAllowedMethods().forEach(config::addAllowedMethod);
            cors.getAllowedHeaders().forEach(config::addAllowedHeader);
            config.setAllowCredentials(cors.isAllowCredentials());
            config.setMaxAge(cors.getMaxAgeSeconds());
        } else {
            // 未配置时使用安全默认值
            config.addAllowedOrigin("*");
            config.addAllowedMethod("*");
            config.addAllowedHeader("*");
            config.setMaxAge(3600L);
        }

        config.addExposedHeader("X-Request-Id");
        config.addExposedHeader("X-RateLimit-Limit");
        config.addExposedHeader("X-RateLimit-Remaining");
        config.addExposedHeader("X-RateLimit-Reset");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
