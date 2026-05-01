package com.code.aigateway.admin.model.rsp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理后台 CSRF Token 响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminCsrfTokenRsp {

    private String token;
}
