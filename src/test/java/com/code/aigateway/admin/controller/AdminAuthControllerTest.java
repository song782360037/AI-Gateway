package com.code.aigateway.admin.controller;

import com.code.aigateway.admin.auth.AdminCsrfTokenManager;
import com.code.aigateway.admin.auth.AdminSessionCookieManager;
import com.code.aigateway.admin.exception.AdminExceptionHandler;
import com.code.aigateway.admin.model.rsp.AdminAuthStatusRsp;
import com.code.aigateway.admin.service.IAdminAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminAuthController 单元测试（会话认证模式）
 *
 * <p>覆盖场景：登录成功、登录失败、获取状态、首次初始化。</p>
 * <p>使用 WebTestClient 绑定 Controller 进行切片测试。</p>
 */
class AdminAuthControllerTest {

    private IAdminAuthService adminAuthService;
    private AdminSessionCookieManager cookieManager;
    private AdminCsrfTokenManager csrfTokenManager;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        adminAuthService = Mockito.mock(IAdminAuthService.class);
        cookieManager = Mockito.mock(AdminSessionCookieManager.class);
        csrfTokenManager = Mockito.mock(AdminCsrfTokenManager.class);

        AdminAuthController controller = new AdminAuthController(adminAuthService, cookieManager, csrfTokenManager);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new AdminExceptionHandler())
                .build();
    }

    // ==================== 获取状态 ====================

    @Test
    void status_noSession_returnsUnauthenticated() {
        when(cookieManager.resolveSessionToken(any())).thenReturn(null);
        AdminAuthStatusRsp status = new AdminAuthStatusRsp();
        status.setInitialized(true);
        status.setAuthenticated(false);
        when(adminAuthService.getStatus(null)).thenReturn(status);

        webTestClient.get().uri("/admin/bootstrap/status")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.authenticated").isEqualTo(false);
    }

    // ==================== 登录成功 ====================

    @Test
    void login_success_returnsAuthStatus() {
        AdminAuthStatusRsp authStatus = new AdminAuthStatusRsp();
        authStatus.setInitialized(true);
        authStatus.setAuthenticated(true);
        authStatus.setUsername("admin");

        when(adminAuthService.login("admin", "admin123"))
                .thenReturn(new IAdminAuthService.SessionLoginResult("session-token-123", authStatus));

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
                .jsonPath("$.data.authenticated").isEqualTo(true)
                .jsonPath("$.data.username").isEqualTo("admin");

        verify(cookieManager).writeSessionCookie(any(), any(), eq("session-token-123"));
    }

    // ==================== 登录失败 ====================

    @Test
    void login_wrongPassword_throwsBizException() {
        when(adminAuthService.login("admin", "wrong-password"))
                .thenThrow(new com.code.aigateway.common.exception.BizException("AUTH_FAILED", "用户名或密码错误"));

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
                .jsonPath("$.code").isEqualTo("AUTH_FAILED");

        // 登录失败不应写 Cookie
        verify(cookieManager, never()).writeSessionCookie(any(), any(), anyString());
    }

    // ==================== 首次初始化 ====================

    @Test
    void setup_success_initializesAndLogsIn() {
        AdminAuthStatusRsp authStatus = new AdminAuthStatusRsp();
        authStatus.setInitialized(true);
        authStatus.setAuthenticated(true);
        authStatus.setUsername("newadmin");

        when(adminAuthService.initialize("newadmin", "Password1"))
                .thenReturn(new IAdminAuthService.SessionLoginResult("setup-token-456", authStatus));

        webTestClient.post()
                .uri("/admin/bootstrap/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "username": "newadmin",
                          "password": "Password1"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.authenticated").isEqualTo(true)
                .jsonPath("$.data.username").isEqualTo("newadmin");

        verify(cookieManager).writeSessionCookie(any(), any(), eq("setup-token-456"));
    }

    // ==================== 退出登录 ====================

    @Test
    void logout_clearsSessionCookie() {
        when(cookieManager.resolveSessionToken(any())).thenReturn("session-token-123");
        Mockito.doNothing().when(adminAuthService).logout("session-token-123");

        webTestClient.post()
                .uri("/admin/logout")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true);

        verify(adminAuthService).logout("session-token-123");
        verify(cookieManager).clearSessionCookie(any(), any());
    }
}
