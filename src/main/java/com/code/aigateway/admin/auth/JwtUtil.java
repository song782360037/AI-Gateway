package com.code.aigateway.admin.auth;

import com.code.aigateway.config.GatewayProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT 工具类
 *
 * <p>启动时自动生成 HMAC-SHA256 密钥，无需任何配置。
 * 重启后密钥变化，已有 Token 自然失效，重新登录即可。</p>
 */
@Slf4j
@Component
public class JwtUtil {

    private static final String ISSUER = "ai-gateway-admin";

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtUtil(GatewayProperties gatewayProperties) {
        GatewayProperties.AdminAuthProperties config = gatewayProperties.getAdminAuth();
        this.expirationMs = config != null ? config.getJwtExpirationMs() : 86400000L;
        this.signingKey = Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256);
    }

    /** 生成 JWT Token */
    public String generateToken(String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(username)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    /** 验证并解析 Token */
    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
