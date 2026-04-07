-- V8: 模型路由规则增加匹配类型，支持通配符和正则匹配

-- 1. 新增 match_type 列，默认 EXACT（精确匹配），兼容存量数据
ALTER TABLE `model_redirect_config`
    ADD COLUMN `match_type` varchar(16) NOT NULL DEFAULT 'EXACT'
        COMMENT '匹配类型：EXACT-精确匹配, GLOB-通配符匹配, REGEX-正则匹配'
        AFTER `alias_name`;

-- 2. 调整唯一索引：加入 match_type，允许同名不同匹配类型的规则共存
--    例如 gpt-4o (EXACT) 和 gpt-4o* (GLOB) 可以同时存在
DROP INDEX `uk_alias_provider_model_deleted` ON `model_redirect_config`;
CREATE UNIQUE INDEX `uk_alias_match_provider_model_deleted`
    ON `model_redirect_config` (`alias_name`, `match_type`, `provider_code`, `target_model`, `deleted`);
