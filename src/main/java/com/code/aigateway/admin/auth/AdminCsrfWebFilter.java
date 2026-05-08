package com.code.aigateway.admin.auth;

import com.code.aigateway.common.result.R;
import com.code.aigateway.config.GatewayProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * 仅保护管理端写接口的 CSRF 与 Origin 校验过滤器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(-300)
public class AdminCsrfWebFilter implements WebFilter {

    static final String CSRF_TOKEN_INVALID_CODE = "CSRF_TOKEN_INVALID";
    private static final Set<HttpMethod> SAFE_METHODS = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);

    private final AdminCsrfTokenManager csrfTokenManager;
    private final ObjectMapper objectMapper;
    private final GatewayProperties gatewayProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        if (!path.startsWith("/admin/") || SAFE_METHODS.contains(request.getMethod())) {
            return chain.filter(exchange);
        }
        if (!hasValidOrigin(request)) {
            return writeForbidden(exchange, "请求来源不可信");
        }
        if (!hasValidToken(request)) {
            return writeForbidden(exchange, "CSRF Token 无效或已过期");
        }
        return chain.filter(exchange);
    }

    private boolean hasValidToken(ServerHttpRequest request) {
        String headerToken = request.getHeaders().getFirst(AdminCsrfTokenManager.HEADER_NAME);
        String cookieToken = csrfTokenManager.resolveCookieToken(request);
        if (!StringUtils.hasText(headerToken) || !StringUtils.hasText(cookieToken)) {
            return false;
        }
        return headerToken.equals(cookieToken) && csrfTokenManager.isValid(headerToken);
    }

    private boolean hasValidOrigin(ServerHttpRequest request) {
        String origin = request.getHeaders().getOrigin();
        if (StringUtils.hasText(origin)) {
            // 先检查是否在可信来源列表中
            if (isTrustedOrigin(origin)) {
                return true;
            }
            return isSameOrigin(request, origin);
        }
        String referer = request.getHeaders().getFirst(HttpHeaders.REFERER);
        if (StringUtils.hasText(referer)) {
            // 先检查 Referer 是否在可信来源列表中
            if (isTrustedOrigin(referer)) {
                return true;
            }
            return isSameOrigin(request, referer);
        }
        log.debug("[管理端 CSRF] unsafe 请求缺少 Origin/Referer，path: {}", request.getPath().value());
        return false;
    }

    /**
     * 判断给定的 Origin/Referer 是否在可信来源列表中。
     * <p>仅比较 scheme + host + port 部分，忽略路径。</p>
     */
    private boolean isTrustedOrigin(String value) {
        List<String> trusted = getTrustedOrigins();
        // 支持 "*" 通配符，匹配所有来源
        if (trusted.contains("*")) {
            return true;
        }
        try {
            URI uri = URI.create(value);
            String originKey = (uri.getScheme() + "://" + uri.getHost()
                    + (uri.getPort() > 0 ? ":" + uri.getPort() : "")).toLowerCase();
            for (String trustedOrigin : trusted) {
                // 统一转为 scheme://host:port 格式进行比较
                String normalized = normalizeOrigin(trustedOrigin);
                if (originKey.equals(normalized)) {
                    return true;
                }
            }
        } catch (Exception ex) {
            log.debug("[管理端 CSRF] 可信来源解析失败: {}", value);
        }
        return false;
    }

    private List<String> getTrustedOrigins() {
        GatewayProperties.AdminAuthProperties adminAuth = gatewayProperties.getAdminAuth();
        if (adminAuth != null && adminAuth.getTrustedOrigins() != null) {
            return adminAuth.getTrustedOrigins();
        }
        return List.of();
    }

    /**
     * 将来源字符串标准化为 scheme://host:port 格式（不含尾部斜杠和路径）。
     */
    private String normalizeOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            return "";
        }
        String trimmed = origin.trim().replaceAll("/+$", "");
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return trimmed.toLowerCase();
            }
            int port = uri.getPort();
            return (scheme + "://" + host + (port > 0 ? ":" + port : "")).toLowerCase();
        } catch (Exception ex) {
            return trimmed.toLowerCase();
        }
    }

    private boolean isSameOrigin(ServerHttpRequest request, String value) {
        try {
            URI uri = URI.create(value);
            String requestScheme = resolveScheme(request);
            String requestHost = resolveHost(request);
            int requestPort = resolvePort(requestScheme, requestHost, request.getURI().getPort());
            return requestScheme.equalsIgnoreCase(uri.getScheme())
                    && stripPort(requestHost).equalsIgnoreCase(uri.getHost())
                    && requestPort == resolvePort(uri.getScheme(), uri.getHost(), uri.getPort());
        } catch (Exception ex) {
            log.debug("[管理端 CSRF] 来源校验失败: {}", value);
            return false;
        }
    }

    private String resolveScheme(ServerHttpRequest request) {
        if (isTrustForwardedHeaders()) {
            String forwardedProto = request.getHeaders().getFirst("X-Forwarded-Proto");
            if (StringUtils.hasText(forwardedProto)) {
                return forwardedProto.split(",")[0].trim();
            }
        }
        return request.getURI().getScheme();
    }

    private String resolveHost(ServerHttpRequest request) {
        if (isTrustForwardedHeaders()) {
            String forwardedHost = request.getHeaders().getFirst("X-Forwarded-Host");
            if (StringUtils.hasText(forwardedHost)) {
                return forwardedHost.split(",")[0].trim();
            }
        }
        if (request.getHeaders().getHost() != null) {
            return request.getHeaders().getHost().toString();
        }
        return request.getURI().getHost();
    }

    private boolean isTrustForwardedHeaders() {
        GatewayProperties.AdminAuthProperties adminAuth = gatewayProperties.getAdminAuth();
        return adminAuth != null && adminAuth.isTrustForwardedHeaders();
    }

    private int resolvePort(String scheme, String host, int explicitPort) {
        if (explicitPort > 0) {
            return explicitPort;
        }
        int hostPort = portFromHost(host);
        if (hostPort > 0) {
            return hostPort;
        }
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    private int portFromHost(String host) {
        if (!StringUtils.hasText(host)) {
            return -1;
        }
        int delimiter = host.lastIndexOf(':');
        if (delimiter < 0 || delimiter == host.length() - 1) {
            return -1;
        }
        try {
            return Integer.parseInt(host.substring(delimiter + 1));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private String stripPort(String host) {
        if (!StringUtils.hasText(host)) {
            return "";
        }
        int delimiter = host.lastIndexOf(':');
        return delimiter > 0 ? host.substring(0, delimiter) : host;
    }

    private Mono<Void> writeForbidden(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        byte[] bytes = toJsonBytes(R.fail(CSRF_TOKEN_INVALID_CODE, message));
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    private byte[] toJsonBytes(Object body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException ex) {
            return ("{\"success\":false,\"code\":\"" + CSRF_TOKEN_INVALID_CODE + "\"}").getBytes(StandardCharsets.UTF_8);
        }
    }
}
