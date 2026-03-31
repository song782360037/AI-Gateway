package com.code.aigateway.admin.model.rsp;

import lombok.Data;

/**
 * 管理后台登录响应
 */
@Data
public class LoginRsp {

    /**
     * JWT Token
     */
    private String token;

    /**
     * Token 有效期（秒）
     */
    private Long expiresIn;
}
