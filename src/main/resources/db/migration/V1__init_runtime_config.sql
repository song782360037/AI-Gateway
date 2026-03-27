-- ============================================
-- AI-Gateway 运行时配置表
-- 版本: V1
-- 说明: 创建提供商配置表和模型重定向配置表
-- ============================================

-- 提供商配置表
CREATE TABLE IF NOT EXISTS `provider_config` (
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `provider_code`     varchar(64)  NOT NULL COMMENT '提供商业务编码（如 openai-main）',
    `provider_type`     varchar(32)  NOT NULL COMMENT '提供商类型：OPENAI / ANTHROPIC / GEMINI',
    `display_name`      varchar(128) DEFAULT NULL COMMENT '展示名称',
    `enabled`           bit(1)       NOT NULL DEFAULT b'1' COMMENT '是否启用',
    `base_url`          varchar(512) NOT NULL COMMENT '接口基础地址',
    `api_key_ciphertext` varchar(768) NOT NULL COMMENT 'API Key 密文（AES-256-GCM，Base64 编码）',
    `api_key_iv`        varchar(64)  NOT NULL COMMENT 'AES-GCM IV 向量（Base64 编码）',
    `api_version`       varchar(64)  DEFAULT NULL COMMENT 'API 版本号（部分提供商需要）',
    `timeout_seconds`   int          NOT NULL DEFAULT 60 COMMENT '请求超时时间（秒）',
    `priority`          int          NOT NULL DEFAULT 0 COMMENT '提供商优先级（数值越大越高）',
    `ext_config_json`   json         DEFAULT NULL COMMENT '预留扩展配置（JSON 格式）',
    `version_no`        bigint       NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `creator`           varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`           varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           bit(1)       NOT NULL DEFAULT b'0' COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_code_deleted` (`provider_code`, `deleted`) COMMENT '业务编码唯一（排除已删除）',
    KEY `idx_provider_type_enabled` (`provider_type`, `enabled`, `deleted`) COMMENT '按类型和状态查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 提供商配置表';

-- 模型重定向配置表
-- 注意：alias_name 不做全局唯一，允许一个别名挂多条规则，为后续多 provider / 多模型路由预留
CREATE TABLE IF NOT EXISTS `model_redirect_config` (
    `id`                  bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `alias_name`          varchar(128) NOT NULL COMMENT '外部模型别名（用户请求时使用的名称）',
    `provider_code`       varchar(64)  NOT NULL COMMENT '目标提供商编码（对应 provider_config.provider_code）',
    `target_model`        varchar(128) NOT NULL COMMENT '真实目标模型名称（实际发给提供商的模型名）',
    `enabled`             bit(1)       NOT NULL DEFAULT b'1' COMMENT '是否启用',
    `priority`            int          NOT NULL DEFAULT 0 COMMENT '规则优先级（数值越大越高）',
    `route_strategy`      varchar(32)  NOT NULL DEFAULT 'PRIORITY' COMMENT '路由策略：PRIORITY / WEIGHT / FALLBACK（预留）',
    `weight`              int          NOT NULL DEFAULT 100 COMMENT '加权路由权重（预留）',
    `match_condition_json` json         DEFAULT NULL COMMENT '匹配条件（JSON 格式，预留）',
    `ext_config_json`     json         DEFAULT NULL COMMENT '扩展配置（JSON 格式，预留）',
    `version_no`          bigint       NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `creator`             varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`             varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             bit(1)       NOT NULL DEFAULT b'0' COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_alias_provider_model_deleted` (`alias_name`, `provider_code`, `target_model`, `deleted`) COMMENT '别名+提供商+模型联合唯一（排除已删除）',
    KEY `idx_alias_enabled_priority` (`alias_name`, `enabled`, `deleted`, `priority`, `update_time`) COMMENT '按别名查询有效规则（按优先级排序）',
    KEY `idx_provider_code_enabled` (`provider_code`, `enabled`, `deleted`) COMMENT '按提供商查询有效规则'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型重定向配置表';
