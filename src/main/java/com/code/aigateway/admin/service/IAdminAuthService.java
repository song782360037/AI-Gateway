package com.code.aigateway.admin.service;

import com.code.aigateway.admin.model.rsp.AdminAuthStatusRsp;

/**
 * 后台管理员认证服务
 */
public interface IAdminAuthService {

    /**
     * 获取当前初始化与登录状态。
     *
     * @param sessionToken 原始会话令牌，可为空
     */
    AdminAuthStatusRsp getStatus(String sessionToken);

    /**
     * 首次初始化管理员并创建登录会话。
     */
    SessionLoginResult initialize(String username, String password);

    /**
     * 管理员登录并创建登录会话。
     */
    SessionLoginResult login(String username, String password);

    /**
     * 更新管理员用户名。
     */
    AdminAuthStatusRsp updateUsername(String sessionToken, String currentPassword, String newUsername);

    /**
     * 更新管理员密码，并安全轮换当前会话。
     */
    SessionLoginResult changePassword(String sessionToken, String currentPassword, String newPassword);

    /**
     * 按会话令牌校验当前登录管理员。
     *
     * @param sessionToken 原始会话令牌，可为空
     * @return 命中返回已认证管理员，否则返回 null
     */
    AuthenticatedAdmin authenticate(String sessionToken);

    /**
     * 吊销当前会话。
     */
    void logout(String sessionToken);

    record SessionLoginResult(String sessionToken, AdminAuthStatusRsp status) {}

    record AuthenticatedAdmin(Long userId, String username) {}
}
