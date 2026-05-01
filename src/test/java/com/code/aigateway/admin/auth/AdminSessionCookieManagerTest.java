package com.code.aigateway.admin.auth;

import com.code.aigateway.config.GatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AdminSessionCookieManager 单元测试
 */
class AdminSessionCookieManagerTest {

    private GatewayProperties gatewayProperties;

    @BeforeEach
    void setUp() {
        gatewayProperties = new GatewayProperties();
    }

    @Test
    void writeSessionCookie_withHttpsRequest_setsSecureFlag() {
        AdminSessionCookieManager manager = new AdminSessionCookieManager(gatewayProperties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("https://localhost/admin/profile").build());

        manager.writeSessionCookie(exchange.getResponse(), exchange.getRequest(), "session-token");

        var cookie = exchange.getResponse().getCookies().getFirst(AdminSessionCookieManager.COOKIE_NAME);
        assertNotNull(cookie);
        assertTrue(cookie.isSecure());
    }

    @Test
    void writeSessionCookie_withForwardedProto_defaultDoesNotSetSecureFlag() {
        AdminSessionCookieManager manager = new AdminSessionCookieManager(gatewayProperties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/admin/profile")
                        .header("X-Forwarded-Proto", "https")
                        .build());

        manager.writeSessionCookie(exchange.getResponse(), exchange.getRequest(), "session-token");

        var cookie = exchange.getResponse().getCookies().getFirst(AdminSessionCookieManager.COOKIE_NAME);
        assertNotNull(cookie);
        assertFalse(cookie.isSecure());
    }

    @Test
    void writeSessionCookie_withForwardedProto_whenTrusted_setsSecureFlag() {
        GatewayProperties.AdminAuthProperties adminAuth = new GatewayProperties.AdminAuthProperties();
        adminAuth.setTrustForwardedHeaders(true);
        gatewayProperties.setAdminAuth(adminAuth);
        AdminSessionCookieManager manager = new AdminSessionCookieManager(gatewayProperties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/admin/profile")
                        .header("X-Forwarded-Proto", "https")
                        .build());

        manager.writeSessionCookie(exchange.getResponse(), exchange.getRequest(), "session-token");

        var cookie = exchange.getResponse().getCookies().getFirst(AdminSessionCookieManager.COOKIE_NAME);
        assertNotNull(cookie);
        assertTrue(cookie.isSecure());
    }

    @Test
    void writeSessionCookie_withMultiValueForwardedProto_whenTrusted_usesFirstValue() {
        GatewayProperties.AdminAuthProperties adminAuth = new GatewayProperties.AdminAuthProperties();
        adminAuth.setTrustForwardedHeaders(true);
        gatewayProperties.setAdminAuth(adminAuth);
        AdminSessionCookieManager manager = new AdminSessionCookieManager(gatewayProperties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/admin/profile")
                        .header("X-Forwarded-Proto", "https,http")
                        .build());

        manager.writeSessionCookie(exchange.getResponse(), exchange.getRequest(), "session-token");

        var cookie = exchange.getResponse().getCookies().getFirst(AdminSessionCookieManager.COOKIE_NAME);
        assertNotNull(cookie);
        assertTrue(cookie.isSecure());
    }
}
