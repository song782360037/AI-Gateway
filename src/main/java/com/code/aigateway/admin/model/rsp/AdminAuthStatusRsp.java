package com.code.aigateway.admin.model.rsp;

import lombok.Data;

/**
 * 后台认证状态响应
 */
@Data
public class AdminAuthStatusRsp {

    /** 是否已完成管理员初始化 */
    private boolean initialized;

    /** 当前请求是否已登录 */
    private boolean authenticated;

    /** 当前登录用户名，未登录时为空 */
    private String username;
}
