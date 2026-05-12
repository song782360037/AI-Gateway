-- V5: 精简路由配置 —— 移除模型路由级别的 priority/weight/route_strategy/match_condition_json/ext_config_json，
--      移除提供商级别的 ext_config_json 和 api_version，路由排序统一由 provider.priority 决定。

-- 1. model_redirect_config: 移除冗余调度字段
ALTER TABLE `model_redirect_config`
    DROP COLUMN `priority`,
    DROP COLUMN `weight`,
    DROP COLUMN `route_strategy`,
    DROP COLUMN `match_condition_json`,
    DROP COLUMN `ext_config_json`;

-- 2. provider_config: 移除扩展配置和 API 版本
ALTER TABLE `provider_config`
    DROP COLUMN `ext_config_json`,
    DROP COLUMN `api_version`;

-- 3. 重建索引：原 idx_alias_enabled_priority 包含已删除的 priority 列，需重建
DROP INDEX `idx_alias_enabled_priority` ON `model_redirect_config`;
CREATE INDEX `idx_alias_enabled` ON `model_redirect_config` (`alias_name`, `enabled`, `deleted`, `update_time`);
