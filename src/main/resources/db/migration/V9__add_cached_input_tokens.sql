-- ============================================
-- AI-Gateway cached input token 统计补充
-- 版本: V9
-- 说明: 为 request_log 和 request_stat_hourly 增加 cached_input_tokens 字段
-- ============================================

ALTER TABLE `request_log`
    ADD COLUMN `cached_input_tokens` int DEFAULT NULL COMMENT '输入命中缓存的 Token 数'
    AFTER `prompt_tokens`;

ALTER TABLE `request_stat_hourly`
    ADD COLUMN `cached_input_tokens` bigint NOT NULL DEFAULT 0 COMMENT '输入命中缓存的 Token 总数'
    AFTER `prompt_tokens`;
