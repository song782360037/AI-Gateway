-- ============================================
-- AI-Gateway Auto 智能路由默认策略升级
-- 版本: V16
-- 说明: 将 auto_route_config 默认选择策略从旧 PRIORITY 升级为 SMART_SCORE
-- ============================================

ALTER TABLE `auto_route_config`
    MODIFY COLUMN `selection_strategy` varchar(32) NOT NULL DEFAULT 'SMART_SCORE' COMMENT '选择策略：SMART_SCORE-智能评分';
