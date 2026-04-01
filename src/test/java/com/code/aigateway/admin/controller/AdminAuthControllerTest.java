package com.code.aigateway.admin.controller;

import com.code.aigateway.admin.auth.JwtUtil;
import com.code.aigateway.admin.exception.AdminExceptionHandler;
import com.code.aigateway.admin.model.rsp.LoginRsp;
import com.code.aigateway.common.result.R;
import com.code.aigateway.config.GatewayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminAuthController 单元测试
 * <p>
 * 覆盖场景：登录成功、密码错误、用户名为空（参数校验）。
 * 使用 WebTestClient 绑定 Controller + ControllerAdvice 进行切片测试。
 * </p>
 */
class AdminAuthControllerTest {

    private JwtUtil jwtUtil;
    private GatewayProperties gatewayProperties;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        jwtUtil = Mockito.mock(JwtUtil.class);
        gatewayProperties = buildDefaultProperties();

        AdminAuthController controller = new AdminAuthController(jwtUtil, gatewayProperties);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new AdminExceptionHandler())
                .build();
    }

    // ==================== 登录成功 ====================

    @Test
    void login_success() {
        // Mock JwtUtil 返回固定 Token
        when(jwtUtil.generateToken("admin")).thenReturn("mocked-jwt-token");

        webTestClient.post()
                .uri("/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "username": "admin",
                          "password": "admin123"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.code").isEqualTo("SUCCESS")
                .jsonPath("$.data.token").isEqualTo("mocked-jwt-token")
                .jsonPath("$.data.expiresIn").isEqualTo(86400);

        // 验证 JwtUtil.generateToken 被正确调用
        verify(jwtUtil).generateToken("admin");
    }

    // ==================== 密码错误 ====================

    @Test
    void login_wrongPassword() {
        // 错误密码应触发 BizException，由 AdminExceptionHandler 返回 400
        webTestClient.post()
                .uri("/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "username": "admin",
                          "password": "wrong-password"
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo("AUTH_FAILED")
                .jsonPath("$.message").exists();

        // 不应调用 Token 生成
        verify(jwtUtil, Mockito.never()).generateToken(anyString());
    }

    // ==================== 用户名不存在（业务校验） ====================

    @Test
    void login_userNotFound() {
        // 不存在的用户名应触发 AUTH_FAILED 业务异常
        webTestClient.post()
                .uri("/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "username": "unknown",
                          "password": "admin123"
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo("AUTH_FAILED");

        verify(jwtUtil, Mockito.never()).generateToken(anyString());
    }

    // ==================== 辅助方法 ====================

    /** 构造默认管理后台配置：admin / admin123 / 24h 过期 */
    private GatewayProperties buildDefaultProperties() {
        GatewayProperties properties = new GatewayProperties();
        GatewayProperties.AdminAuthProperties adminAuth = new GatewayProperties.AdminAuthProperties();
        adminAuth.setUsername("admin");
        adminAuth.setPassword("admin123");
        adminAuth.setJwtExpirationMs(86400000L);
        properties.setAdminAuth(adminAuth);
        return properties;
    }
}
