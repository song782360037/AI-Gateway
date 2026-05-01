package com.code.aigateway.admin.auth;

import com.code.aigateway.config.GatewayProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminCsrfWebFilterTest {

    private AdminCsrfTokenManager csrfTokenManager;
    private AdminCsrfWebFilter filter;
    private WebFilterChain filterChain;

    @BeforeEach
    void setUp() {
        GatewayProperties gatewayProperties = new GatewayProperties();
        csrfTokenManager = new AdminCsrfTokenManager(gatewayProperties);
        filter = new AdminCsrfWebFilter(csrfTokenManager, new ObjectMapper(), gatewayProperties);
        filterChain = Mockito.mock(WebFilterChain.class);
        when(filterChain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void filter_v1PostWithoutCsrf_passesThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/v1/chat/completions").build()
        );

        StepVerifier.create(filter.filter(exchange, filterChain)).verifyComplete();

        verify(filterChain).filter(exchange);
    }

    @Test
    void filter_adminGetWithoutCsrf_passesThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/admin/bootstrap/status").build()
        );

        StepVerifier.create(filter.filter(exchange, filterChain)).verifyComplete();

        verify(filterChain).filter(exchange);
    }

    @Test
    void filter_adminPostWithoutToken_returnsCsrfInvalidCode() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/admin/logout")
                        .header(HttpHeaders.ORIGIN, "http://localhost")
                        .build()
        );

        StepVerifier.create(filter.filter(exchange, filterChain)).verifyComplete();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertTrue(responseBody(exchange).contains("\"code\":\"CSRF_TOKEN_INVALID\""));
        verify(filterChain, never()).filter(any());
    }

    @Test
    void filter_adminPostWhenErrorBodySerializationFails_returnsCsrfInvalidCode() throws Exception {
        ObjectMapper failingObjectMapper = Mockito.mock(ObjectMapper.class);
        when(failingObjectMapper.writeValueAsBytes(any()))
                .thenThrow(new JsonProcessingException("serialize failed") {
                });
        AdminCsrfWebFilter failingFilter = new AdminCsrfWebFilter(
                csrfTokenManager, failingObjectMapper, new GatewayProperties());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/admin/logout")
                        .header(HttpHeaders.ORIGIN, "http://localhost")
                        .build()
        );

        StepVerifier.create(failingFilter.filter(exchange, filterChain)).verifyComplete();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertTrue(responseBody(exchange).contains("\"code\":\"CSRF_TOKEN_INVALID\""));
        verify(filterChain, never()).filter(any());
    }

    @Test
    void filter_adminPostWithoutOriginAndReferer_returns403() {
        MockServerWebExchange tokenExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/admin/csrf").build()
        );
        String token = csrfTokenManager.issueToken(tokenExchange.getRequest(), tokenExchange.getResponse());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.POST, "http://localhost/admin/logout")
                        .header(AdminCsrfTokenManager.HEADER_NAME, token)
                        .cookie(new org.springframework.http.HttpCookie(AdminCsrfTokenManager.COOKIE_NAME, token))
                        .build()
        );

        StepVerifier.create(filter.filter(exchange, filterChain)).verifyComplete();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(filterChain, never()).filter(any());
    }

    @Test
    void filter_adminPostWithValidToken_passesThrough() {
        MockServerWebExchange tokenExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/admin/csrf").build()
        );
        String token = csrfTokenManager.issueToken(tokenExchange.getRequest(), tokenExchange.getResponse());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.POST, "http://localhost/admin/logout")
                        .header(HttpHeaders.ORIGIN, "http://localhost")
                        .header(AdminCsrfTokenManager.HEADER_NAME, token)
                        .cookie(new org.springframework.http.HttpCookie(AdminCsrfTokenManager.COOKIE_NAME, token))
                        .build()
        );

        StepVerifier.create(filter.filter(exchange, filterChain)).verifyComplete();

        verify(filterChain).filter(exchange);
    }

    @Test
    void filter_adminPostWithCrossOrigin_returns403() {
        MockServerWebExchange tokenExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/admin/csrf").build()
        );
        String token = csrfTokenManager.issueToken(tokenExchange.getRequest(), tokenExchange.getResponse());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.POST, "http://localhost/admin/logout")
                        .header(HttpHeaders.ORIGIN, "http://evil.example")
                        .header(AdminCsrfTokenManager.HEADER_NAME, token)
                        .cookie(new org.springframework.http.HttpCookie(AdminCsrfTokenManager.COOKIE_NAME, token))
                        .build()
        );

        StepVerifier.create(filter.filter(exchange, filterChain)).verifyComplete();

        assertNotNull(exchange.getResponse().getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(filterChain, never()).filter(any());
    }

    @Test
    void filter_adminPostWithForgedForwardedHost_defaultRejects() {
        MockServerWebExchange tokenExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/admin/csrf").build()
        );
        String token = csrfTokenManager.issueToken(tokenExchange.getRequest(), tokenExchange.getResponse());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.POST, "http://localhost/admin/logout")
                        .header(HttpHeaders.ORIGIN, "https://admin.example.com")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "admin.example.com")
                        .header(AdminCsrfTokenManager.HEADER_NAME, token)
                        .cookie(new org.springframework.http.HttpCookie(AdminCsrfTokenManager.COOKIE_NAME, token))
                        .build()
        );

        StepVerifier.create(filter.filter(exchange, filterChain)).verifyComplete();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(filterChain, never()).filter(any());
    }

    @Test
    void filter_adminPostWithForgedForwardedHost_whenTrusted_passesThrough() {
        GatewayProperties gatewayProperties = new GatewayProperties();
        GatewayProperties.AdminAuthProperties adminAuth = new GatewayProperties.AdminAuthProperties();
        adminAuth.setTrustForwardedHeaders(true);
        gatewayProperties.setAdminAuth(adminAuth);
        AdminCsrfTokenManager trustedCsrfTokenManager = new AdminCsrfTokenManager(gatewayProperties);
        AdminCsrfWebFilter trustedFilter = new AdminCsrfWebFilter(trustedCsrfTokenManager, new ObjectMapper(), gatewayProperties);

        MockServerWebExchange tokenExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/admin/csrf").build()
        );
        String token = trustedCsrfTokenManager.issueToken(tokenExchange.getRequest(), tokenExchange.getResponse());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.POST, "http://localhost/admin/logout")
                        .header(HttpHeaders.ORIGIN, "https://admin.example.com")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "admin.example.com")
                        .header(AdminCsrfTokenManager.HEADER_NAME, token)
                        .cookie(new org.springframework.http.HttpCookie(AdminCsrfTokenManager.COOKIE_NAME, token))
                        .build()
        );

        StepVerifier.create(trustedFilter.filter(exchange, filterChain)).verifyComplete();

        verify(filterChain).filter(exchange);
    }

    private String responseBody(MockServerWebExchange exchange) {
        String body = exchange.getResponse().getBodyAsString().block();
        assertNotNull(body);
        return body;
    }
}
