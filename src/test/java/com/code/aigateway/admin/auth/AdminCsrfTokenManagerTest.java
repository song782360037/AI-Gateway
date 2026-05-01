package com.code.aigateway.admin.auth;

import com.code.aigateway.config.GatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AdminCsrfTokenManager 单元测试
 *
 * <p>覆盖场景：Token 签发、校验、过期、签名密钥配置、Cookie 属性。</p>
 */
class AdminCsrfTokenManagerTest {

    private GatewayProperties gatewayProperties;

    @BeforeEach
    void setUp() {
        gatewayProperties = new GatewayProperties();
    }

    // ==================== Token 签发与校验 ====================

    @Test
    void issueToken_returnsNonBlankToken() {
        AdminCsrfTokenManager manager = new AdminCsrfTokenManager(gatewayProperties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/admin/csrf").build());

        String token = manager.issueToken(exchange.getRequest(), exchange.getResponse());

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void isValid_withFreshToken_returnsTrue() {
        AdminCsrfTokenManager manager = new AdminCsrfTokenManager(gatewayProperties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/admin/csrf").build());

        String token = manager.issueToken(exchange.getRequest(), exchange.getResponse());

        assertTrue(manager.isValid(token));
    }

    @Test
    void isValid_withNullToken_returnsFalse() {
        AdminCsrfTokenManager manager = new AdminCsrfTokenManager(gatewayProperties);
        assertFalse(manager.isValid(null));
    }

    @Test
    void isValid_withEmptyToken_returnsFalse() {
        AdminCsrfTokenManager manager = new AdminCsrfTokenManager(gatewayProperties);
        assertFalse(manager.isValid(""));
    }

    @Test
    void isValid_withMalformedToken_returnsFalse() {
        AdminCsrfTokenManager manager = new AdminCsrfTokenManager(gatewayProperties);
        assertFalse(manager.isValid("not-a-valid-token"));
    }

    @Test
    void isValid_withWrongSignature_returnsFalse() {
        AdminCsrfTokenManager manager = new AdminCsrfTokenManager(gatewayProperties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/admin/csrf").build());

        String token = manager.issueToken(exchange.getRequest(), exchange.getResponse());
        // 篡改签名部分
        String tampered = token + "tampered";

        assertFalse(manager.isValid(tampered));
    }

    @Test
    void isValid_withTokenFromDifferentKey_returnsFalse() {
        AdminCsrfTokenManager manager1 = new AdminCsrfTokenManager(gatewayProperties);
        AdminCsrfTokenManager manager2 = new AdminCsrfTokenManager(gatewayProperties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/admin/csrf").build());

        String token = manager1.issueToken(exchange.getRequest(), exchange.getResponse());

        // 不同实例使用随机密钥，Token 应无法互相验证
        assertFalse(manager2.isValid(token));
    }

    @Test
    void isValid_withSameFixedKey_differentInstances_returnTrue() {
        String fixedKey = Base64.getEncoder().encodeToString(new byte[32]); // 32 字节全零
        GatewayProperties.AdminAuthProperties adminAuth = new GatewayProperties.AdminAuthProperties();
        adminAuth.setCsrfSigningKey(fixedKey);
        gatewayProperties.setAdminAuth(adminAuth);

        AdminCsrfTokenManager manager1 = new AdminCsrfTokenManager(gatewayProperties);
        AdminCsrfTokenManager manager2 = new AdminCsrfTokenManager(gatewayProperties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/admin/csrf").build());

        String token = manager1.issueToken(exchange.getRequest(), exchange.getResponse());

        // 相同固定密钥的不同实例应能互相验证
        assertTrue(manager2.isValid(token));
    }

    // ==================== 拒绝缺少时间戳的旧格式 ====================

    @Test
    void isValid_withLegacyTwoPartToken_returnsFalse() {
        AdminCsrfTokenManager manager = new AdminCsrfTokenManager(gatewayProperties);

        assertFalse(manager.isValid("nonce.signature"));
        assertFalse(manager.isValid("onlyonepart"));
        assertFalse(manager.isValid("part1.part2.part3.part4"));
    }

    // ==================== Cookie 属性 ====================

    @Test
    void issueToken_setsCookieWithCorrectAttributes() {
        AdminCsrfTokenManager manager = new AdminCsrfTokenManager(gatewayProperties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/admin/csrf").build());

        manager.issueToken(exchange.getRequest(), exchange.getResponse());

        var cookie = exchange.getResponse().getCookies().getFirst(AdminCsrfTokenManager.COOKIE_NAME);
        assertNotNull(cookie);
        assertEquals("/admin", cookie.getPath());
        // httpOnly=false 因为前端 JS 需要读取
        assertFalse(cookie.isHttpOnly());
        assertEquals("Lax", cookie.getSameSite());
    }

    @Test
    void issueToken_overHttps_setsSecureFlag() {
        AdminCsrfTokenManager manager = new AdminCsrfTokenManager(gatewayProperties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("https://localhost/admin/csrf").build());

        manager.issueToken(exchange.getRequest(), exchange.getResponse());

        var cookie = exchange.getResponse().getCookies().getFirst(AdminCsrfTokenManager.COOKIE_NAME);
        assertNotNull(cookie);
        assertTrue(cookie.isSecure());
    }

    @Test
    void issueToken_withForwardedProto_doesNotSetSecureFlagByDefault() {
        AdminCsrfTokenManager manager = new AdminCsrfTokenManager(gatewayProperties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/admin/csrf")
                        .header("X-Forwarded-Proto", "https")
                        .build());

        manager.issueToken(exchange.getRequest(), exchange.getResponse());

        var cookie = exchange.getResponse().getCookies().getFirst(AdminCsrfTokenManager.COOKIE_NAME);
        assertNotNull(cookie);
        assertFalse(cookie.isSecure());
    }

    @Test
    void issueToken_withForwardedProto_whenTrusted_setsSecureFlag() {
        GatewayProperties.AdminAuthProperties adminAuth = new GatewayProperties.AdminAuthProperties();
        adminAuth.setTrustForwardedHeaders(true);
        gatewayProperties.setAdminAuth(adminAuth);
        AdminCsrfTokenManager manager = new AdminCsrfTokenManager(gatewayProperties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/admin/csrf")
                        .header("X-Forwarded-Proto", "https")
                        .build());

        manager.issueToken(exchange.getRequest(), exchange.getResponse());

        var cookie = exchange.getResponse().getCookies().getFirst(AdminCsrfTokenManager.COOKIE_NAME);
        assertNotNull(cookie);
        assertTrue(cookie.isSecure());
    }

    @Test
    void issueToken_withMultiValueForwardedProto_whenTrusted_usesFirstValue() {
        GatewayProperties.AdminAuthProperties adminAuth = new GatewayProperties.AdminAuthProperties();
        adminAuth.setTrustForwardedHeaders(true);
        gatewayProperties.setAdminAuth(adminAuth);
        AdminCsrfTokenManager manager = new AdminCsrfTokenManager(gatewayProperties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/admin/csrf")
                        .header("X-Forwarded-Proto", "https,http")
                        .build());

        manager.issueToken(exchange.getRequest(), exchange.getResponse());

        var cookie = exchange.getResponse().getCookies().getFirst(AdminCsrfTokenManager.COOKIE_NAME);
        assertNotNull(cookie);
        assertTrue(cookie.isSecure());
    }

    // ==================== clearToken ====================

    @Test
    void clearToken_setsCookieWithZeroMaxAge() {
        AdminCsrfTokenManager manager = new AdminCsrfTokenManager(gatewayProperties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/admin/csrf").build());

        manager.clearToken(exchange.getRequest(), exchange.getResponse());

        var cookie = exchange.getResponse().getCookies().getFirst(AdminCsrfTokenManager.COOKIE_NAME);
        assertNotNull(cookie);
        assertEquals(0L, cookie.getMaxAge().getSeconds());
        assertEquals("", cookie.getValue());
    }

    // ==================== resolveCookieToken ====================

    @Test
    void resolveCookieToken_withCookie_returnsValue() {
        AdminCsrfTokenManager manager = new AdminCsrfTokenManager(gatewayProperties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/admin/csrf")
                        .cookie(new org.springframework.http.HttpCookie(AdminCsrfTokenManager.COOKIE_NAME, "test-token"))
                        .build());

        String result = manager.resolveCookieToken(exchange.getRequest());
        assertEquals("test-token", result);
    }

    @Test
    void resolveCookieToken_withoutCookie_returnsNull() {
        AdminCsrfTokenManager manager = new AdminCsrfTokenManager(gatewayProperties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/admin/csrf").build());

        String result = manager.resolveCookieToken(exchange.getRequest());
        assertEquals(null, result);
    }

    // ==================== 签名密钥配置 ====================

    @Test
    void signingKey_withInvalidBase64_fallsBackToRandom() {
        GatewayProperties.AdminAuthProperties adminAuth = new GatewayProperties.AdminAuthProperties();
        adminAuth.setCsrfSigningKey("not-valid-base64!!!");
        gatewayProperties.setAdminAuth(adminAuth);

        // 应不抛异常，回退到随机密钥
        AdminCsrfTokenManager manager = new AdminCsrfTokenManager(gatewayProperties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/admin/csrf").build());
        String token = manager.issueToken(exchange.getRequest(), exchange.getResponse());

        assertTrue(manager.isValid(token));
    }

    @Test
    void signingKey_withWrongLength_fallsBackToRandom() {
        GatewayProperties.AdminAuthProperties adminAuth = new GatewayProperties.AdminAuthProperties();
        adminAuth.setCsrfSigningKey(Base64.getEncoder().encodeToString(new byte[16])); // 仅 16 字节
        gatewayProperties.setAdminAuth(adminAuth);

        // 应不抛异常，回退到随机密钥
        AdminCsrfTokenManager manager = new AdminCsrfTokenManager(gatewayProperties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/admin/csrf").build());
        String token = manager.issueToken(exchange.getRequest(), exchange.getResponse());

        assertTrue(manager.isValid(token));
    }

    // ==================== Token 唯一性 ====================

    @Test
    void issueToken_generatesUniqueTokens() {
        AdminCsrfTokenManager manager = new AdminCsrfTokenManager(gatewayProperties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/admin/csrf").build());

        String token1 = manager.issueToken(exchange.getRequest(), exchange.getResponse());
        String token2 = manager.issueToken(exchange.getRequest(), exchange.getResponse());

        assertNotEquals(token1, token2);
    }
}
