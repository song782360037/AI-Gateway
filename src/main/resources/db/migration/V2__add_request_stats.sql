-- ============================================
-- AI-Gateway 请求统计表
-- 版本: V2
-- 说明: 创建请求日志表和小时统计聚合表，支撑仪表盘统计查询
-- ============================================

-- 请求日志表：记录每次 API 调用的明细数据
CREATE TABLE IF NOT EXISTS `request_log` (
    `id`                  bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `request_id`          varchar(64)  NOT NULL COMMENT '请求唯一标识',
    `alias_model`         varchar(128) NOT NULL COMMENT '用户请求的模型别名',
    `target_model`        varchar(128) DEFAULT NULL COMMENT '实际路由到的目标模型',
    `provider_code`       varchar(64)  DEFAULT NULL COMMENT '提供商编码',
    `provider_type`       varchar(32)  DEFAULT NULL COMMENT '提供商类型',
    `is_stream`           bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否流式请求',
    `prompt_tokens`       int          DEFAULT NULL COMMENT '输入 Token 数',
    `completion_tokens`   int          DEFAULT NULL COMMENT '输出 Token 数',
    `total_tokens`        int          DEFAULT NULL COMMENT '总 Token 数',
    `duration_ms`         int          DEFAULT NULL COMMENT '响应耗时（毫秒）',
    `status`              varchar(16)  NOT NULL COMMENT 'SUCCESS / ERROR',
    `error_code`          varchar(64)  DEFAULT NULL COMMENT '错误码（失败时记录）',
    `source_ip`           varchar(64)  DEFAULT NULL COMMENT '来源 IP',
    `create_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_create_time` (`create_time`) COMMENT '按时间查询',
    KEY `idx_alias_model_time` (`alias_model`, `create_time`) COMMENT '按模型+时间查询',
    KEY `idx_provider_code_time` (`provider_code`, `create_time`) COMMENT '按提供商+时间查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='请求日志表';

-- 请求小时统计表：按小时粒度聚合请求指标，加速仪表盘查询
CREATE TABLE IF NOT EXISTS `request_stat_hourly` (
    `id`                  bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `stat_time`           datetime     NOT NULL COMMENT '统计小时（如 2026-03-28 14:00:00）',
    `alias_model`         varchar(128) NOT NULL COMMENT '模型别名',
    `provider_code`       varchar(64)  NOT NULL COMMENT '提供商编码',
    `request_count`       int          NOT NULL DEFAULT 0 COMMENT '请求总数',
    `success_count`       int          NOT NULL DEFAULT 0 COMMENT '成功数',
    `error_count`         int          NOT NULL DEFAULT 0 COMMENT '失败数',
    `prompt_tokens`       bigint       NOT NULL DEFAULT 0 COMMENT '输入 Token 总数',
    `completion_tokens`   bigint       NOT NULL DEFAULT 0 COMMENT '输出 Token 总数',
    `total_tokens`        bigint       NOT NULL DEFAULT 0 COMMENT '总 Token 数',
    `total_duration_ms`   bigint       NOT NULL DEFAULT 0 COMMENT '总耗时（ms），用于计算平均响应时间',
    `estimated_cost`      decimal(16,6) NOT NULL DEFAULT 0.000000 COMMENT '估算费用（USD）',
    `create_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_stat_model_provider` (`stat_time`, `alias_model`, `provider_code`) COMMENT '小时+模型+提供商联合唯一',
    KEY `idx_stat_time` (`stat_time`) COMMENT '按统计时间查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='请求小时统计聚合表';
