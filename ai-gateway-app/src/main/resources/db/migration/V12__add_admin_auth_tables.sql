-- ============================================
-- AI-Gateway 管理员认证与持久化会话
-- 版本: V12
-- 说明: 新增单管理员账号表和后台登录会话表
-- ============================================

CREATE TABLE IF NOT EXISTS `admin_user` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `singleton_key` char(1) NOT NULL DEFAULT 'A' COMMENT '单用户模式固定占位键，确保系统仅有一个管理员',
    `username` varchar(64) NOT NULL COMMENT '管理员用户名',
    `password_hash` varchar(100) NOT NULL COMMENT 'BCrypt 密码哈希',
    `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否启用',
    `last_login_at` datetime DEFAULT NULL COMMENT '最后登录时间',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_admin_user_singleton` (`singleton_key`),
    UNIQUE KEY `uk_admin_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='后台管理员账号表';

CREATE TABLE IF NOT EXISTS `admin_session` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` bigint NOT NULL COMMENT '管理员ID',
    `session_token_hash` char(64) NOT NULL COMMENT '会话令牌 SHA-256 哈希，原始令牌仅保存在 HttpOnly Cookie 中',
    `expire_time` datetime NOT NULL COMMENT '会话过期时间',
    `last_access_time` datetime NOT NULL COMMENT '最后访问时间',
    `revoked` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否已吊销',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_admin_session_token_hash` (`session_token_hash`),
    KEY `idx_admin_session_user_id` (`user_id`),
    KEY `idx_admin_session_expire_time` (`expire_time`),
    CONSTRAINT `fk_admin_session_user_id` FOREIGN KEY (`user_id`) REFERENCES `admin_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='后台管理员会话表';
