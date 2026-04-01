package com.code.aigateway.admin.auth;

import com.code.aigateway.config.GatewayProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 单元测试
 * <p>
 * 覆盖场景：正常生成/验证、过期 Token、无效 Token、密钥不匹配。
 * 注意：每次 new JwtUtil() 都会生成独立的 HMAC-SHA256 密钥，
 * 因此不同实例间 Token 不互通，这是预期行为。
 * </p>
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(buildProperties(86400000L));
    }

    // ==================== 正常场景 ====================

    @Test
    void generateToken_success() {
        // 生成 Token 后验证 subject 一致
        String token = jwtUtil.generateToken("admin");

        Claims claims = jwtUtil.validateToken(token);

        assertEquals("admin", claims.getSubject());
        assertEquals("ai-gateway-admin", claims.getIssuer());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    // ==================== 异常场景 ====================

    @Test
    void validateToken_expired() throws InterruptedException {
        // 构造极短有效期（1ms）的 JwtUtil，等待 Token 过期
        JwtUtil shortLivedUtil = new JwtUtil(buildProperties(1L));
        String token = shortLivedUtil.generateToken("admin");

        // 等待确保 Token 已过期
        Thread.sleep(50);

        assertThrows(JwtException.class, () -> shortLivedUtil.validateToken(token));
    }

    @Test
    void validateToken_invalidToken() {
        // 传入垃圾字符串，解析应直接抛异常
        assertThrows(JwtException.class, () -> jwtUtil.validateToken("this.is.not-a-jwt"));
    }

    @Test
    void validateToken_wrongKey() {
        // 两个 JwtUtil 实例持有不同密钥，Token 不互通
        JwtUtil anotherUtil = new JwtUtil(buildProperties(86400000L));

        String token = jwtUtil.generateToken("admin");

        // 用另一个实例的密钥去验证，应抛出签名校验失败异常
        assertThrows(JwtException.class, () -> anotherUtil.validateToken(token));
    }

    // ==================== 辅助方法 ====================

    /** 构造 GatewayProperties，仅设置 JWT 过期时间 */
    private GatewayProperties buildProperties(long jwtExpirationMs) {
        GatewayProperties properties = new GatewayProperties();
        GatewayProperties.AdminAuthProperties adminAuth = new GatewayProperties.AdminAuthProperties();
        adminAuth.setJwtExpirationMs(jwtExpirationMs);
        properties.setAdminAuth(adminAuth);
        return properties;
    }
}
