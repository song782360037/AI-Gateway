-- ============================================
-- AI-Gateway Auto 智能路由配置
-- 版本: V13
-- 说明: 新增 auto 模型智能路由配置表和候选模型表
-- ============================================

CREATE TABLE IF NOT EXISTS `auto_route_config` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `route_key` varchar(64) NOT NULL COMMENT 'Auto 路由键，default 对应 model=auto',
    `display_name` varchar(128) NOT NULL COMMENT '配置展示名称',
    `description` varchar(512) DEFAULT NULL COMMENT '配置说明',
    `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否启用',
    `selection_strategy` varchar(32) NOT NULL DEFAULT 'PRIORITY' COMMENT '选择策略：PRIORITY-按优先级',
    `version_no` bigint NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_auto_route_config_route_key` (`route_key`),
    KEY `idx_auto_route_config_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Auto 智能路由配置表';

CREATE TABLE IF NOT EXISTS `auto_route_candidate` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `config_id` bigint NOT NULL COMMENT 'Auto 路由配置ID',
    `provider_code` varchar(64) NOT NULL COMMENT '目标提供商编码',
    `target_model` varchar(128) NOT NULL COMMENT '目标模型名称',
    `priority` int NOT NULL DEFAULT 0 COMMENT '优先级，数值越大越优先',
    `weight` int NOT NULL DEFAULT 100 COMMENT '权重，预留给后续加权策略',
    `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否启用',
    `description` varchar(512) DEFAULT NULL COMMENT '候选说明',
    `version_no` bigint NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_auto_route_candidate_unique` (`config_id`, `provider_code`, `target_model`),
    KEY `idx_auto_route_candidate_config_id` (`config_id`),
    KEY `idx_auto_route_candidate_provider_code` (`provider_code`),
    CONSTRAINT `fk_auto_route_candidate_config_id` FOREIGN KEY (`config_id`) REFERENCES `auto_route_config` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Auto 智能路由候选模型表';
