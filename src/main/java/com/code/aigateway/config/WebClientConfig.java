package com.code.aigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient 配置类
 * <p>
 * 配置用于发起 HTTP 请求的响应式 WebClient Bean
 * </p>
 *
 * @author sst
 */
@Configuration
public class WebClientConfig {

    /**
     * 创建 WebClient Bean
     * <p>
     * 用于向各 AI 提供商发起 API 请求
     * </p>
     *
     * @param builder WebClient 构建器
     * @return 配置好的 WebClient 实例
     */
    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }
}
