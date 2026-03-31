package com.code.aigateway.admin.controller;

import com.code.aigateway.admin.auth.JwtUtil;
import com.code.aigateway.admin.model.rsp.LoginRsp;
import com.code.aigateway.common.exception.BizException;
import com.code.aigateway.common.result.R;
import com.code.aigateway.config.GatewayProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 管理后台认证接口
 */
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/admin")
public class AdminAuthController {

    private final JwtUtil jwtUtil;
    private final GatewayProperties gatewayProperties;

    /**
     * 管理员登录，返回 JWT Token
     */
    @PostMapping("/login")
    public Mono<R<LoginRsp>> login(@Validated @RequestBody LoginReq req) {
        GatewayProperties.AdminAuthProperties authConfig = gatewayProperties.getAdminAuth();
        if (authConfig == null) {
            throw new BizException("AUTH_DISABLED", "管理后台认证未配置");
        }

        // 校验用户名密码
        if (!authConfig.getUsername().equals(req.getUsername())
                || !authConfig.getPassword().equals(req.getPassword())) {
            throw new BizException("AUTH_FAILED", "用户名或密码错误");
        }

        // 生成 JWT Token
        String token = jwtUtil.generateToken(req.getUsername());
        long expiresInSeconds = authConfig.getJwtExpirationMs() / 1000;

        LoginRsp rsp = new LoginRsp();
        rsp.setToken(token);
        rsp.setExpiresIn(expiresInSeconds);
        return Mono.just(R.ok(rsp));
    }

    /**
     * 登录请求体
     */
    @Data
    public static class LoginReq {
        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;
    }
}
