package com.code.aigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * WebClientConfig 单元测试
 */
class WebClientConfigTest {

    @Test
    void reactorClientHttpConnector_withNullHttpClient_createsBean() {
        GatewayProperties props = new GatewayProperties();
        // httpClient 为 null，应使用默认值
        WebClientConfig config = new WebClientConfig(props);

        ReactorClientHttpConnector connector = config.reactorClientHttpConnector();
        assertNotNull(connector);
    }

    @Test
    void reactorClientHttpConnector_withCustomProperties_createsBean() {
        GatewayProperties props = new GatewayProperties();
        GatewayProperties.HttpClientProperties httpProps = new GatewayProperties.HttpClientProperties();
        httpProps.setMaxConnections(100);
        httpProps.setConnectTimeoutMs(5000);
        httpProps.setResponseTimeoutMs(30000);
        props.setHttpClient(httpProps);

        WebClientConfig config = new WebClientConfig(props);
        ReactorClientHttpConnector connector = config.reactorClientHttpConnector();
        assertNotNull(connector);
    }

    @Test
    void reactorClientHttpConnector_defaultProperties() {
        GatewayProperties.HttpClientProperties httpProps = new GatewayProperties.HttpClientProperties();
        // 验证默认值
        assertNotNull(httpProps);
        assertEquals(500, httpProps.getMaxConnections());
        assertEquals(10000, httpProps.getConnectTimeoutMs());
        assertEquals(0, httpProps.getResponseTimeoutMs());
    }
}
