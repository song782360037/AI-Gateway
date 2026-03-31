-- ============================================
-- AI-Gateway API Key 配置表
-- 版本: V3
-- 说明: 用于网关对外接口 API Key 鉴权与管理
-- ============================================

CREATE TABLE IF NOT EXISTS `api_key_config` (
    `id`              bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `key_hash`        varchar(64)   NOT NULL COMMENT 'API Key SHA-256 哈希（hex，64字符）',
    `key_prefix`      varchar(8)    NOT NULL COMMENT '前缀（如 ak-a1b2c），列表展示用',
    `name`            varchar(128)  NOT NULL COMMENT '名称/备注',
    `status`          varchar(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED',
    `daily_limit`     int           DEFAULT NULL COMMENT '每日调用上限（NULL 不限）',
    `total_limit`     bigint        DEFAULT NULL COMMENT '累计调用上限（NULL 不限）',
    `used_count`      bigint        NOT NULL DEFAULT 0 COMMENT '累计已使用次数',
    `expire_time`     datetime      DEFAULT NULL COMMENT '过期时间（NULL 永不过期）',
    `version_no`      bigint        NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `creator`         varchar(64)   DEFAULT '' COMMENT '创建者',
    `create_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`         varchar(64)   DEFAULT '' COMMENT '更新者',
    `update_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         bit(1)        NOT NULL DEFAULT b'0' COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_key_hash_deleted` (`key_hash`, `deleted`) COMMENT 'Key 哈希唯一（排除已删除）',
    KEY `idx_status` (`status`, `deleted`) COMMENT '按状态查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API Key 配置表';
