-- V4: 为 API Key 表添加限流配置字段
ALTER TABLE `api_key_config`
    ADD COLUMN `rpm_limit` int DEFAULT NULL COMMENT '每分钟请求上限（NULL 使用全局默认）' AFTER `daily_limit`,
    ADD COLUMN `hourly_limit` int DEFAULT NULL COMMENT '每小时请求上限（NULL 使用全局默认）' AFTER `rpm_limit`;
