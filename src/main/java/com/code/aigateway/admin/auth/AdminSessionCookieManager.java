package com.code.aigateway.admin.auth;

import com.code.aigateway.config.GatewayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 管理后台会话 Cookie 读写工具
 */
@Component
@RequiredArgsConstructor
public class AdminSessionCookieManager {

    public static final String COOKIE_NAME = "AI_GATEWAY_ADMIN_SESSION";
    private static final String COOKIE_PATH = "/admin";
    private static final String LEGACY_COOKIE_PATH = "/";

    private final GatewayProperties gatewayProperties;

    /** 读取当前请求中的管理员会话 Cookie */
    public String resolveSessionToken(ServerHttpRequest request) {
        HttpCookie cookie = request.getCookies().getFirst(COOKIE_NAME);
        if (cookie == null || !StringUtils.hasText(cookie.getValue())) {
            return null;
        }
        return cookie.getValue();
    }

    /** 写入新的管理员会话 Cookie */
    public void writeSessionCookie(ServerHttpResponse response, ServerHttpRequest request, String sessionToken) {
        clearLegacyRootCookie(response, request);
        response.addCookie(buildCookie(request, COOKIE_PATH, sessionToken, getTtl()));
    }

    /** 清理管理员会话 Cookie */
    public void clearSessionCookie(ServerHttpResponse response, ServerHttpRequest request) {
        response.addCookie(buildCookie(request, COOKIE_PATH, "", Duration.ZERO));
        clearLegacyRootCookie(response, request);
    }

    private void clearLegacyRootCookie(ServerHttpResponse response, ServerHttpRequest request) {
        response.addCookie(buildCookie(request, LEGACY_COOKIE_PATH, "", Duration.ZERO));
    }

    private ResponseCookie buildCookie(ServerHttpRequest request, String path, String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(isSecureRequest(request))
                .path(path)
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
        if (request.getSslInfo() != null) {
            return true;
        }
        String forwardedProto = request.getHeaders().getFirst("X-Forwarded-Proto");
        return "https".equalsIgnoreCase(forwardedProto);
    }
}
