-- ==========================================
-- V19: 新增全局配置表 & 提供商自定义请求头
-- ==========================================

-- 1. 全局配置表（可扩展，当前仅存储 custom_headers）
CREATE TABLE IF NOT EXISTS global_config (
    id              bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    config_key      varchar(64)  NOT NULL COMMENT '配置键（唯一标识）',
    config_value    text         DEFAULT NULL COMMENT '配置值（JSON 或纯文本）',
    description     varchar(256) DEFAULT NULL COMMENT '配置描述',
    version_no      bigint       NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    creator         varchar(64)  DEFAULT '' COMMENT '创建者',
    create_time     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updater         varchar(64)  DEFAULT '' COMMENT '更新者',
    update_time     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         bit(1)       NOT NULL DEFAULT b'0' COMMENT '逻辑删除标记',
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key_deleted (config_key, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局配置表';

-- 2. 初始化全局自定义请求头配置（空 JSON 对象，表示默认无自定义头）
INSERT INTO global_config (config_key, config_value, description)
VALUES ('custom_headers', '{}', '全局自定义请求头（JSON 键值对），所有提供商共用；提供商级别可覆盖同名头');

-- 3. 提供商配置表新增自定义请求头列
ALTER TABLE provider_config
    ADD COLUMN custom_headers text DEFAULT NULL COMMENT '提供商级别自定义请求头（JSON 键值对），覆盖全局同名头' AFTER supported_protocols;
