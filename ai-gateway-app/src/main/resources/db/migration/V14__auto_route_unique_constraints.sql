-- ============================================
-- AI-Gateway Auto 智能路由唯一约束补丁
-- 版本: V14
-- 说明: 将普通索引升级为唯一索引，防止并发插入重复 routeKey 和候选模型。
--       逻辑删除时追加 _del_{id} 后缀，允许同名 routeKey 删除后重建。
-- ============================================

-- route_key 唯一索引（逻辑删除时 route_key 被追加 _del_{id} 后缀，不会冲突）
ALTER TABLE `auto_route_config`
    DROP INDEX `idx_auto_route_config_route_key`,
    ADD UNIQUE INDEX `uk_auto_route_config_route_key_deleted` (`route_key`, `deleted`);

-- (config_id, provider_code, target_model) 唯一索引
ALTER TABLE `auto_route_candidate`
    DROP INDEX `idx_auto_route_candidate_unique`,
    ADD UNIQUE INDEX `uk_auto_route_candidate_unique` (`config_id`, `provider_code`, `target_model`, `deleted`);
