-- ============================================
-- 同一提供商多 API Key 支持（第一步：建表 + 迁移数据）
-- 第二步（V23）在确认数据迁移成功后删除旧列，避免迁移中断导致数据丢失。
-- ============================================

-- 1. 创建 provider_api_key 子表
CREATE TABLE IF NOT EXISTS `provider_api_key` (
    `id`                bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `provider_code`     varchar(64)   NOT NULL COMMENT '所属提供商编码（对应 provider_config.provider_code）',
    `api_key_ciphertext` varchar(768) NOT NULL COMMENT 'API Key 密文（AES-256-GCM，Base64 编码）',
    `api_key_iv`        varchar(64)   NOT NULL COMMENT 'AES-GCM IV 向量（Base64 编码）',
    `api_key_prefix`    varchar(20)   NOT NULL COMMENT 'API Key 脱敏标识（前8后4格式），列表展示用',
    `remark`            varchar(256)  DEFAULT NULL COMMENT '备注（如"生产主Key"、"备用Key"）',
    `enabled`           bit(1)        NOT NULL DEFAULT b'1' COMMENT '是否启用',
    `weight`            int           NOT NULL DEFAULT 100 COMMENT '权重（用于加权随机策略，值越大被选中概率越高）',
    `sort_order`        int           NOT NULL DEFAULT 0 COMMENT '排序号（用于 FALLBACK 策略，值越小越优先）',
    `version_no`        bigint        NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `creator`           varchar(64)   DEFAULT '' COMMENT '创建者',
    `create_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`           varchar(64)   DEFAULT '' COMMENT '更新者',
    `update_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           bit(1)        NOT NULL DEFAULT b'0' COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_pak_provider_enabled` (`provider_code`, `enabled`, `deleted`) COMMENT '按提供商查询可用Key',
    KEY `idx_pak_provider_sort` (`provider_code`, `sort_order`, `deleted`) COMMENT '按提供商+排序查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提供商 API Key 子表';

-- 2. provider_config 增加 key_selection_strategy 字段
ALTER TABLE `provider_config`
    ADD COLUMN `key_selection_strategy` varchar(32) NOT NULL DEFAULT 'ROUND_ROBIN'
        COMMENT 'Key 选择策略：ROUND_ROBIN / RANDOM / FALLBACK'
        AFTER `thinking_compat_mode`;

-- 3. 迁移现有 provider_config 中的单 Key 到 provider_api_key 子表
-- 注意：api_key_prefix 此处使用密文前缀作为临时标识，不准确。
-- 应用启动后 RuntimeConfigRefreshService.repairMigratedKeyPrefixes() 会自动从明文重新计算真实掩码并回写数据库。
INSERT INTO `provider_api_key` (
    `provider_code`, `api_key_ciphertext`, `api_key_iv`, `api_key_prefix`,
    `remark`, `enabled`, `weight`, `sort_order`, `version_no`,
    `creator`, `create_time`, `updater`, `update_time`, `deleted`
)
SELECT
    pc.provider_code,
    pc.api_key_ciphertext,
    pc.api_key_iv,
    CONCAT(LEFT(pc.api_key_ciphertext, 8), '****') AS api_key_prefix,
    '迁移自原有单Key' AS remark,
    b'1' AS enabled,
    100 AS weight,
    0 AS sort_order,
    0 AS version_no,
    COALESCE(pc.creator, '') AS creator,
    COALESCE(pc.create_time, NOW()) AS create_time,
    COALESCE(pc.updater, '') AS updater,
    COALESCE(pc.update_time, NOW()) AS update_time,
    b'0' AS deleted
FROM `provider_config` pc
WHERE pc.deleted = 0
  AND pc.api_key_ciphertext IS NOT NULL;

-- 4. request_log 增加 provider_api_key_masked 字段
ALTER TABLE `request_log`
    ADD COLUMN `provider_api_key_masked` varchar(20) DEFAULT NULL
        COMMENT '本次请求使用的提供商 API Key（脱敏，前8后4）'
        AFTER `api_key_prefix`;
