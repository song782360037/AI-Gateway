package com.code.aigateway.config;

import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * WebClient 配置类
 * <p>
 * 配置共享的 HTTP 传输层基础设施：连接池、超时、压缩等。
 * 各 Provider 客户端通过注入 {@link ReactorClientHttpConnector} 共享连接池，
 * 在此基础上按 Provider 差异化设置 baseUrl、认证头等。
 * </p>
 *
 * @author sst
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final GatewayProperties gatewayProperties;

    /**
     * 创建共享的 HTTP 连接器，内含连接池和传输层配置。
     * <p>
     * 所有 Provider 客户端共享同一个 ConnectionProvider，
     * WebClient 实例本身是轻量的，每次 build() 共享底层连接池。
     * </p>
     */
    @Bean
    public ReactorClientHttpConnector reactorClientHttpConnector() {
        // 字段自带默认值，仅此处兜底整体为 null 的场景
        GatewayProperties.HttpClientProperties props = gatewayProperties.getHttpClient() != null
                ? gatewayProperties.getHttpClient()
                : new GatewayProperties.HttpClientProperties();

        ConnectionProvider connectionProvider = ConnectionProvider.builder("ai-gateway-pool")
                .maxConnections(props.getMaxConnections())
                .pendingAcquireTimeout(Duration.ofMillis(props.getPendingAcquireTimeoutMs()))
                .maxIdleTime(Duration.ofMillis(props.getMaxIdleTimeMs()))
                .maxLifeTime(Duration.ofMillis(props.getMaxLifeTimeMs()))
                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .compress(true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, props.getConnectTimeoutMs())
                .responseTimeout(props.getResponseTimeoutMs() > 0
                        ? Duration.ofMillis(props.getResponseTimeoutMs()) : null);

        log.info("[WebClient] 连接池已配置: maxConnections={}, connectTimeout={}ms, pendingAcquireTimeout={}ms",
                props.getMaxConnections(), props.getConnectTimeoutMs(), props.getPendingAcquireTimeoutMs());

        return new ReactorClientHttpConnector(httpClient);
    }
}
