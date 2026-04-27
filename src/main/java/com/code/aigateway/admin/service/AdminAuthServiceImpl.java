package com.code.aigateway.admin.service;

import com.code.aigateway.admin.mapper.AdminSessionMapper;
import com.code.aigateway.admin.mapper.AdminUserMapper;
import com.code.aigateway.admin.model.dataobject.AdminSessionDO;
import com.code.aigateway.admin.model.dataobject.AdminUserDO;
import com.code.aigateway.admin.model.rsp.AdminAuthStatusRsp;
import com.code.aigateway.common.exception.BizException;
import com.code.aigateway.config.GatewayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 后台管理员认证服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl implements IAdminAuthService {

    private static final String SINGLETON_KEY = "A";
    private static final int SESSION_TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AdminUserMapper adminUserMapper;
    private final AdminSessionMapper adminSessionMapper;
    private final TransactionTemplate transactionTemplate;
    private final BCryptPasswordEncoder passwordEncoder;
    private final GatewayProperties gatewayProperties;

    @Override
    public AdminAuthStatusRsp getStatus(String sessionToken) {
        adminSessionMapper.revokeExpiredSessions();
        AdminUserDO adminUser = adminUserMapper.selectCurrentAdmin();
        if (adminUser == null) {
            return buildStatus(false, false, null);
        }
        AuthenticatedAdmin authenticatedAdmin = authenticate(sessionToken);
        if (authenticatedAdmin == null) {
            return buildStatus(true, false, null);
        }
        return buildStatus(true, true, authenticatedAdmin.username());
    }

    @Override
    public SessionLoginResult initialize(String username, String password) {
        validateUsername(username);
        validatePassword(password);
        AdminUserDO existingAdmin = adminUserMapper.selectCurrentAdmin();
        if (existingAdmin != null || adminUserMapper.countAll() > 0) {
            throw new BizException("ADMIN_ALREADY_INITIALIZED", "系统已完成初始化，请使用管理员账号登录");
        }

        LocalDateTime now = LocalDateTime.now();
        String rawSessionToken = generateSessionToken();
        String sessionTokenHash = sha256Hex(rawSessionToken);

        AdminUserDO adminUser = new AdminUserDO();
        adminUser.setSingletonKey(SINGLETON_KEY);
        adminUser.setUsername(username.trim());
        adminUser.setPasswordHash(passwordEncoder.encode(password));
        adminUser.setEnabled(true);
        adminUser.setLastLoginAt(now);
        adminUser.setCreateTime(now);
        adminUser.setUpdateTime(now);

        try {
            transactionTemplate.executeWithoutResult(status -> {
                int userRows = adminUserMapper.insert(adminUser);
                if (userRows <= 0 || adminUser.getId() == null) {
                    throw new BizException("DB_ERROR", "初始化管理员失败，请稍后重试");
                }
                createSession(adminUser.getId(), sessionTokenHash, now);
                log.info("[管理员认证] 首次初始化完成，username: {}", adminUser.getUsername());
            });
        } catch (DuplicateKeyException ex) {
            log.warn("[管理员认证] 初始化并发冲突，系统已被其他请求完成初始化");
            throw new BizException("ADMIN_ALREADY_INITIALIZED", "系统已完成初始化，请使用管理员账号登录");
        }

        return new SessionLoginResult(rawSessionToken, buildStatus(true, true, adminUser.getUsername()));
    }

    @Override
    public SessionLoginResult login(String username, String password) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new BizException("INVALID_PARAM", "用户名和密码不能为空");
        }

        adminSessionMapper.revokeExpiredSessions();
        AdminUserDO adminUser = getEnabledAdminOrThrow();
        if (!adminUser.getUsername().equals(username.trim())
                || !passwordEncoder.matches(password, adminUser.getPasswordHash())) {
            throw new BizException("AUTH_FAILED", "用户名或密码错误");
        }

        LocalDateTime now = LocalDateTime.now();
        String rawSessionToken = generateSessionToken();
        String sessionTokenHash = sha256Hex(rawSessionToken);

        transactionTemplate.executeWithoutResult(status -> {
            createSession(adminUser.getId(), sessionTokenHash, now);
            updateLastLoginAt(adminUser.getId(), now);
            log.info("[管理员认证] 登录成功，username: {}", adminUser.getUsername());
        });

        return new SessionLoginResult(rawSessionToken, buildStatus(true, true, adminUser.getUsername()));
    }

    @Override
    public AdminAuthStatusRsp updateUsername(String sessionToken, String currentPassword, String newUsername) {
        if (!StringUtils.hasText(currentPassword)) {
            throw new BizException("INVALID_PARAM", "当前密码不能为空");
        }
        validateUsername(newUsername);

        AuthenticatedAdmin authenticatedAdmin = requireAuthenticated(sessionToken);
        AdminUserDO adminUser = getEnabledAdminOrThrow();
        if (!adminUser.getId().equals(authenticatedAdmin.userId())) {
            throw new BizException("UNAUTHORIZED", "登录状态已失效，请重新登录");
        }
        if (!passwordEncoder.matches(currentPassword, adminUser.getPasswordHash())) {
            throw new BizException("AUTH_FAILED", "当前密码错误");
        }

        String trimmedUsername = newUsername.trim();
        if (trimmedUsername.equals(adminUser.getUsername())) {
            throw new BizException("INVALID_PARAM", "新用户名不能与当前用户名相同");
        }

        LocalDateTime now = LocalDateTime.now();
        transactionTemplate.executeWithoutResult(status -> {
            int rows = adminUserMapper.updateUsername(adminUser.getId(), trimmedUsername, now);
            if (rows <= 0) {
                throw new BizException("DB_ERROR", "修改用户名失败，请稍后重试");
            }
            log.info("[管理员认证] 用户名已更新: {} -> {}", adminUser.getUsername(), trimmedUsername);
        });

        return buildStatus(true, true, trimmedUsername);
    }

    @Override
    public SessionLoginResult changePassword(String sessionToken, String currentPassword, String newPassword) {
        if (!StringUtils.hasText(currentPassword)) {
            throw new BizException("INVALID_PARAM", "当前密码不能为空");
        }
        validatePassword(newPassword);

        AuthenticatedAdmin authenticatedAdmin = requireAuthenticated(sessionToken);
        AdminUserDO adminUser = getEnabledAdminOrThrow();
        if (!adminUser.getId().equals(authenticatedAdmin.userId())) {
            throw new BizException("UNAUTHORIZED", "登录状态已失效，请重新登录");
        }
        if (!passwordEncoder.matches(currentPassword, adminUser.getPasswordHash())) {
            throw new BizException("AUTH_FAILED", "当前密码错误");
        }
        if (passwordEncoder.matches(newPassword, adminUser.getPasswordHash())) {
            throw new BizException("INVALID_PARAM", "新密码不能与当前密码相同");
        }

        LocalDateTime now = LocalDateTime.now();
        String rawSessionToken = generateSessionToken();
        String sessionTokenHash = sha256Hex(rawSessionToken);
        String newPasswordHash = passwordEncoder.encode(newPassword);

        transactionTemplate.executeWithoutResult(status -> {
            int rows = adminUserMapper.updatePasswordHash(adminUser.getId(), newPasswordHash, now);
            if (rows <= 0) {
                throw new BizException("DB_ERROR", "修改密码失败，请稍后重试");
            }
            adminSessionMapper.revokeByUserId(adminUser.getId());
            createSession(adminUser.getId(), sessionTokenHash, now);
            updateLastLoginAt(adminUser.getId(), now);
            log.info("[管理员认证] 密码修改成功，已轮换全部会话，username: {}", adminUser.getUsername());
        });

        return new SessionLoginResult(rawSessionToken, buildStatus(true, true, adminUser.getUsername()));
    }

    @Override
    public AuthenticatedAdmin authenticate(String sessionToken) {
        if (!StringUtils.hasText(sessionToken)) {
            return null;
        }

        String tokenHash = sha256Hex(sessionToken);
        AdminSessionDO session = adminSessionMapper.selectActiveByTokenHash(tokenHash);
        if (session == null) {
            return null;
        }

        AdminUserDO adminUser = adminUserMapper.selectCurrentAdmin();
        if (adminUser == null || !adminUser.getId().equals(session.getUserId()) || !Boolean.TRUE.equals(adminUser.getEnabled())) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        adminSessionMapper.updateLastAccessTime(session.getId(), now, now);
        return new AuthenticatedAdmin(adminUser.getId(), adminUser.getUsername());
    }

    @Override
    public void logout(String sessionToken) {
        if (!StringUtils.hasText(sessionToken)) {
            return;
        }
        adminSessionMapper.revokeByTokenHash(sha256Hex(sessionToken));
    }

    private AuthenticatedAdmin requireAuthenticated(String sessionToken) {
        AuthenticatedAdmin authenticatedAdmin = authenticate(sessionToken);
        if (authenticatedAdmin == null) {
            throw new BizException("UNAUTHORIZED", "登录状态已失效，请重新登录");
        }
        return authenticatedAdmin;
    }

    private AdminUserDO getEnabledAdminOrThrow() {
        AdminUserDO adminUser = adminUserMapper.selectCurrentAdmin();
        if (adminUser == null) {
            throw new BizException("INIT_REQUIRED", "系统尚未初始化，请先创建管理员账户");
        }
        if (!Boolean.TRUE.equals(adminUser.getEnabled())) {
            throw new BizException("ADMIN_DISABLED", "管理员账号已被禁用");
        }
        return adminUser;
    }

    private void updateLastLoginAt(Long userId, LocalDateTime now) {
        int rows = adminUserMapper.updateLastLoginAt(userId, now, now);
        if (rows <= 0) {
            throw new BizException("DB_ERROR", "更新登录时间失败，请稍后重试");
        }
    }

    private void validateUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new BizException("INVALID_PARAM", "用户名不能为空");
        }
        String trimmedUsername = username.trim();
        if (trimmedUsername.length() < 3 || trimmedUsername.length() > 32) {
            throw new BizException("INVALID_PARAM", "用户名长度需为 3 到 32 位");
        }
    }

    private void validatePassword(String password) {
        if (!StringUtils.hasText(password)) {
            throw new BizException("INVALID_PARAM", "密码不能为空");
        }
        if (password.length() < 8 || password.length() > 64) {
            throw new BizException("INVALID_PARAM", "密码长度需为 8 到 64 位");
        }
        if (!password.matches("^(?=.*[A-Za-z])(?=.*\\d).{8,64}$")) {
            throw new BizException("INVALID_PARAM", "密码至少包含 1 个字母和 1 个数字");
        }
    }

    private void createSession(Long userId, String sessionTokenHash, LocalDateTime now) {
        AdminSessionDO session = new AdminSessionDO();
        session.setUserId(userId);
        session.setSessionTokenHash(sessionTokenHash);
        session.setExpireTime(now.plusDays(getSessionTtlDays()));
        session.setLastAccessTime(now);
        session.setRevoked(false);
        session.setCreateTime(now);
        session.setUpdateTime(now);
        int rows = adminSessionMapper.insert(session);
        if (rows <= 0) {
            throw new BizException("DB_ERROR", "创建登录会话失败，请稍后重试");
        }
    }

    private AdminAuthStatusRsp buildStatus(boolean initialized, boolean authenticated, String username) {
        AdminAuthStatusRsp rsp = new AdminAuthStatusRsp();
        rsp.setInitialized(initialized);
        rsp.setAuthenticated(authenticated);
        rsp.setUsername(username);
        return rsp;
    }

    private String generateSessionToken() {
        byte[] bytes = new byte[SESSION_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BizException("HASH_ERROR", "会话令牌摘要计算失败", e);
        }
    }

    private long getSessionTtlDays() {
        GatewayProperties.AdminAuthProperties adminAuth = gatewayProperties.getAdminAuth();
        if (adminAuth == null || adminAuth.getSessionTtlDays() <= 0) {
            return 7L;
        }
        return adminAuth.getSessionTtlDays();
    }
}
