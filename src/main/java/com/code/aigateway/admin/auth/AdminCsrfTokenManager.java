package com.code.aigateway.admin.auth;

import com.code.aigateway.config.GatewayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * 管理后台 CSRF Token 读写与校验。
 */
@Slf4j
@Component
public class AdminCsrfTokenManager {

    public static final String COOKIE_NAME = "AI_GATEWAY_ADMIN_CSRF";
    public static final String HEADER_NAME = "X-CSRF-Token";
    private static final String COOKIE_PATH = "/admin";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int NONCE_BYTES = 32;
    private static final int SIGNING_KEY_BYTES = 32;

    private final GatewayProperties gatewayProperties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] signingKey;

    public AdminCsrfTokenManager(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
        this.signingKey = resolveSigningKey();
    }

    public String resolveCookieToken(ServerHttpRequest request) {
        HttpCookie cookie = request.getCookies().getFirst(COOKIE_NAME);
        if (cookie == null || !StringUtils.hasText(cookie.getValue())) {
            return null;
        }
        return cookie.getValue();
    }

    public String issueToken(ServerHttpRequest request, ServerHttpResponse response) {
        String token = createToken();
        response.addCookie(buildCookie(request, token, getTtl()));
        return token;
    }

    public void clearToken(ServerHttpRequest request, ServerHttpResponse response) {
        response.addCookie(buildCookie(request, "", Duration.ZERO));
    }

    public boolean isValid(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        // Token 格式：nonce.timestamp.signature
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return false;
        }

        String nonce = parts[0];
        String timestampStr = parts[1];
        String signature = parts[2];

        // 校验时间戳有效性
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException ex) {
            return false;
        }
        long ttlMs = getTtl().toMillis();
        if (System.currentTimeMillis() - timestamp > ttlMs) {
            log.debug("[CSRF] Token 已过期，age: {}ms", System.currentTimeMillis() - timestamp);
            return false;
        }

        // 校验签名
        String signedPayload = nonce + "." + timestampStr;
        String expected = sign(signedPayload);
        return MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.US_ASCII),
                expected.getBytes(StandardCharsets.US_ASCII));
    }

    private String createToken() {
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        String encodedNonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        long timestamp = System.currentTimeMillis();
        String payload = encodedNonce + "." + timestamp;
        return payload + "." + sign(payload);
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign CSRF token", ex);
        }
    }

    /**
     * 构建 CSRF Cookie。
     *
     * <p>注意：httpOnly 设为 false，因为前端 JS 需要读取此 Cookie 的值
     * 并通过 X-CSRF-Token 请求头回传。这是 Double Submit Cookie 模式的固有取舍：
     * 若站点存在 XSS 漏洞，攻击者可读取此 Cookie 从而绕过 CSRF 防护。
     * 因此务必确保已有足够的 XSS 防护措施（CSP 头、输入消毒等）。</p>
     */
    private ResponseCookie buildCookie(ServerHttpRequest request, String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(false)
                .secure(isSecureRequest(request))
                .path(COOKIE_PATH)
                .sameSite("Lax")
                .maxAge(maxAge)
                .build();
    }

    private Duration getTtl() {
        GatewayProperties.AdminAuthProperties adminAuth = gatewayProperties.getAdminAuth();
        long days = adminAuth != null && adminAuth.getSessionTtlDays() > 0 ? adminAuth.getSessionTtlDays() : 7L;
        return Duration.ofDays(days);
    }

    private boolean isSecureRequest(ServerHttpRequest request) {
        if (request.getSslInfo() != null || "https".equalsIgnoreCase(request.getURI().getScheme())) {
            return true;
        }
        GatewayProperties.AdminAuthProperties adminAuth = gatewayProperties.getAdminAuth();
        if (adminAuth == null || !adminAuth.isTrustForwardedHeaders()) {
            return false;
        }
        String forwardedProto = request.getHeaders().getFirst("X-Forwarded-Proto");
        if (!StringUtils.hasText(forwardedProto)) {
            return false;
        }
        return "https".equalsIgnoreCase(forwardedProto.split(",")[0].trim());
    }

    private byte[] resolveSigningKey() {
        GatewayProperties.AdminAuthProperties adminAuth = gatewayProperties.getAdminAuth();
        if (adminAuth != null && StringUtils.hasText(adminAuth.getCsrfSigningKey())) {
            try {
                byte[] key = Base64.getDecoder().decode(adminAuth.getCsrfSigningKey());
                if (key.length == SIGNING_KEY_BYTES) {
                    log.info("[CSRF] 使用配置的签名密钥");
                    return key;
                }
                log.warn("[CSRF] 配置的签名密钥长度不正确（期望 {} 字节，实际 {}），回退到随机生成",
                        SIGNING_KEY_BYTES, key.length);
            } catch (IllegalArgumentException ex) {
                log.warn("[CSRF] 配置的签名密钥 Base64 解码失败，回退到随机生成", ex);
            }
        }
        log.warn("[CSRF] 未配置固定签名密钥，使用随机生成（重启后旧 Token 失效，多实例部署需配置 gateway.admin-auth.csrf-signing-key）");
        byte[] key = new byte[SIGNING_KEY_BYTES];
        secureRandom.nextBytes(key);
        return key;
    }
}
