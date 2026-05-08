package com.code.aigateway.admin.auth;

import com.code.aigateway.admin.mapper.GlobalConfigMapper;
import com.code.aigateway.admin.model.dataobject.GlobalConfigDO;
import com.code.aigateway.config.GatewayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
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
import java.util.List;

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
    private static final String DB_CONFIG_KEY = "csrf_signing_key";

    private final GatewayProperties gatewayProperties;
    private final GlobalConfigMapper globalConfigMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] signingKey;

    /**
     * Cookie SameSite 属性，从配置读取，默认 Lax。
     * <p>在 Docker 部署且使用反向代理时，可能需要设为 None（需同时启用 Secure）。</p>
     */
    private final String cookieSameSite;

    public AdminCsrfTokenManager(GatewayProperties gatewayProperties, GlobalConfigMapper globalConfigMapper) {
        this.gatewayProperties = gatewayProperties;
        this.globalConfigMapper = globalConfigMapper;
        this.signingKey = resolveSigningKey();
        this.cookieSameSite = resolveCookieSameSite();
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
     *
     * <p>SameSite 属性通过配置 {@code gateway.admin-auth.csrf-cookie-same-site} 控制，
     * 默认 Lax。在 Docker 部署且使用反向代理导致跨站请求时，需设为 None（同时确保启用 HTTPS）。</p>
     */
    private ResponseCookie buildCookie(ServerHttpRequest request, String value, Duration maxAge) {
        boolean secure = isSecureRequest(request);
        if ("None".equals(cookieSameSite) && !secure) {
            log.warn("[CSRF] SameSite=None 但请求非 HTTPS，浏览器将拒绝设置此 Cookie，" +
                    "CSRF 防护可能失效。请启用 HTTPS 或配置 trust-forwarded-headers=true");
        }
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(false)
                .secure(secure)
                .path(COOKIE_PATH)
                .sameSite(cookieSameSite)
                .maxAge(maxAge)
                .build();
    }

    private static final List<String> VALID_SAME_SITE_VALUES = List.of("Strict", "Lax", "None");

    private String resolveCookieSameSite() {
        GatewayProperties.AdminAuthProperties adminAuth = gatewayProperties.getAdminAuth();
        if (adminAuth != null && StringUtils.hasText(adminAuth.getCsrfCookieSameSite())) {
            String value = adminAuth.getCsrfCookieSameSite().trim();
            // 规范化：首字母大写，其余小写，以兼容 "none"/"NONE"/"None" 等写法
            String normalized = value.substring(0, 1).toUpperCase() + value.substring(1).toLowerCase();
            if (VALID_SAME_SITE_VALUES.contains(normalized)) {
                return normalized;
            }
            log.warn("[CSRF] 配置的 csrf-cookie-same-site 值 '{}' 不合法，合法值为 {}，回退为默认 Lax",
                    value, VALID_SAME_SITE_VALUES);
        }
        return "Lax";
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
        // 1. 优先使用显式配置的密钥（最高优先级）
        GatewayProperties.AdminAuthProperties adminAuth = gatewayProperties.getAdminAuth();
        if (adminAuth != null && StringUtils.hasText(adminAuth.getCsrfSigningKey())) {
            try {
                byte[] key = Base64.getDecoder().decode(adminAuth.getCsrfSigningKey());
                if (key.length == SIGNING_KEY_BYTES) {
                    log.info("[CSRF] 使用配置的签名密钥");
                    return key;
                }
                log.warn("[CSRF] 配置的签名密钥长度不正确（期望 {} 字节，实际 {}），回退到数据库/自动生成",
                        SIGNING_KEY_BYTES, key.length);
            } catch (IllegalArgumentException ex) {
                log.warn("[CSRF] 配置的签名密钥 Base64 解码失败，回退到数据库/自动生成", ex);
            }
        }

        // 2. 尝试从数据库读取已持久化的密钥
        byte[] dbKey = loadKeyFromDb();
        if (dbKey != null) {
            log.info("[CSRF] 使用数据库中已持久化的签名密钥");
            return dbKey;
        }

        // 3. 自动生成并持久化到数据库
        byte[] key = new byte[SIGNING_KEY_BYTES];
        secureRandom.nextBytes(key);
        persistKeyToDb(key);
        log.info("[CSRF] 自动生成签名密钥并已持久化到数据库，重启后不会失效");
        return key;
    }

    private byte[] loadKeyFromDb() {
        try {
            GlobalConfigDO record = globalConfigMapper.selectByConfigKey(DB_CONFIG_KEY);
            if (record != null && StringUtils.hasText(record.getConfigValue())) {
                byte[] key = Base64.getDecoder().decode(record.getConfigValue());
                if (key.length == SIGNING_KEY_BYTES) {
                    return key;
                }
                log.warn("[CSRF] 数据库中的签名密钥长度不正确（期望 {} 字节，实际 {}），将重新生成",
                        SIGNING_KEY_BYTES, key.length);
            }
        } catch (Exception ex) {
            log.warn("[CSRF] 从数据库读取签名密钥失败，将自动生成（数据库可能尚未初始化）", ex);
        }
        return null;
    }

    private void persistKeyToDb(byte[] key) {
        try {
            String base64Key = Base64.getEncoder().encodeToString(key);
            GlobalConfigDO record = new GlobalConfigDO();
            record.setConfigKey(DB_CONFIG_KEY);
            record.setConfigValue(base64Key);
            record.setDescription("CSRF Token 签名密钥（自动生成，请勿手动修改）");
            try {
                globalConfigMapper.insertByConfigKey(record);
            } catch (DuplicateKeyException e) {
                // 并发启动时另一实例已先写入，忽略
                log.info("[CSRF] 签名密钥已由其他实例写入数据库");
            }
        } catch (Exception ex) {
            // 持久化失败不影响启动，但重启后密钥会变化
            log.warn("[CSRF] 签名密钥持久化到数据库失败，重启后旧 Token 将失效", ex);
        }
    }
}
