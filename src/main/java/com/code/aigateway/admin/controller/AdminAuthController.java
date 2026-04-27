package com.code.aigateway.admin.controller;

import com.code.aigateway.admin.auth.AdminSessionCookieManager;
import com.code.aigateway.admin.model.rsp.AdminAuthStatusRsp;
import com.code.aigateway.admin.service.IAdminAuthService;
import com.code.aigateway.common.result.R;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 管理后台认证接口
 */
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/admin")
public class AdminAuthController {

    private final IAdminAuthService adminAuthService;
    private final AdminSessionCookieManager cookieManager;

    /**
     * 获取系统初始化状态与当前登录状态。
     */
    @GetMapping("/bootstrap/status")
    public Mono<R<AdminAuthStatusRsp>> status(ServerHttpRequest request, ServerHttpResponse response) {
        return Mono.fromCallable(() -> {
                    String sessionToken = cookieManager.resolveSessionToken(request);
                    AdminAuthStatusRsp status = adminAuthService.getStatus(sessionToken);
                    if (sessionToken != null && !status.isAuthenticated()) {
                        cookieManager.clearSessionCookie(response, request);
                    }
                    return status;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }

    /**
     * 首次初始化管理员账号，并自动完成登录。
     */
    @PostMapping("/bootstrap/setup")
    public Mono<R<AdminAuthStatusRsp>> setup(@Validated @RequestBody SetupReq req,
                                             ServerHttpRequest request,
                                             ServerHttpResponse response) {
        return Mono.fromCallable(() -> adminAuthService.initialize(req.getUsername(), req.getPassword()))
                .subscribeOn(Schedulers.boundedElastic())
                .map(result -> {
                    cookieManager.writeSessionCookie(response, request, result.sessionToken());
                    return R.ok(result.status());
                });
    }

    /**
     * 管理员登录，并创建持久化会话 Cookie。
     */
    @PostMapping("/login")
    public Mono<R<AdminAuthStatusRsp>> login(@Validated @RequestBody LoginReq req,
                                             ServerHttpRequest request,
                                             ServerHttpResponse response) {
        return Mono.fromCallable(() -> adminAuthService.login(req.getUsername(), req.getPassword()))
                .subscribeOn(Schedulers.boundedElastic())
                .map(result -> {
                    cookieManager.writeSessionCookie(response, request, result.sessionToken());
                    return R.ok(result.status());
                });
    }

    /**
     * 修改管理员用户名。
     */
    @PostMapping("/account/username")
    public Mono<R<AdminAuthStatusRsp>> updateUsername(@Validated @RequestBody UpdateUsernameReq req,
                                                      ServerHttpRequest request) {
        return Mono.fromCallable(() -> adminAuthService.updateUsername(
                        cookieManager.resolveSessionToken(request),
                        req.getCurrentPassword(),
                        req.getNewUsername()))
                .subscribeOn(Schedulers.boundedElastic())
                .map(R::ok);
    }

    /**
     * 修改管理员密码，并轮换当前会话 Cookie。
     */
    @PostMapping("/account/password")
    public Mono<R<AdminAuthStatusRsp>> changePassword(@Validated @RequestBody ChangePasswordReq req,
                                                      ServerHttpRequest request,
                                                      ServerHttpResponse response) {
        return Mono.fromCallable(() -> adminAuthService.changePassword(
                        cookieManager.resolveSessionToken(request),
                        req.getCurrentPassword(),
                        req.getNewPassword()))
                .subscribeOn(Schedulers.boundedElastic())
                .map(result -> {
                    cookieManager.writeSessionCookie(response, request, result.sessionToken());
                    return R.ok(result.status());
                });
    }

    /**
     * 退出登录，并清理当前会话 Cookie。
     */
    @PostMapping("/logout")
    public Mono<R<Void>> logout(ServerHttpRequest request, ServerHttpResponse response) {
        return Mono.fromRunnable(() -> adminAuthService.logout(cookieManager.resolveSessionToken(request)))
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.fromSupplier(() -> {
                    cookieManager.clearSessionCookie(response, request);
                    return R.<Void>ok();
                }));
    }

    /**
     * 登录请求体。
     */
    @Data
    public static class LoginReq {
        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;
    }

    /**
     * 首次初始化请求体。
     */
    @Data
    public static class SetupReq {
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 32, message = "用户名长度需为 3 到 32 位")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度需为 8 到 64 位")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$", message = "密码至少包含 1 个字母和 1 个数字")
        private String password;
    }

    /**
     * 修改用户名请求体。
     */
    @Data
    public static class UpdateUsernameReq {
        @NotBlank(message = "当前密码不能为空")
        private String currentPassword;

        @NotBlank(message = "新用户名不能为空")
        @Size(min = 3, max = 32, message = "新用户名长度需为 3 到 32 位")
        private String newUsername;
    }

    /**
     * 修改密码请求体。
     */
    @Data
    public static class ChangePasswordReq {
        @NotBlank(message = "当前密码不能为空")
        private String currentPassword;

        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 64, message = "新密码长度需为 8 到 64 位")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$", message = "新密码至少包含 1 个字母和 1 个数字")
        private String newPassword;
    }
}
