-- ============================================
-- 多 API Key 支持第二步：删除 provider_config 上的旧 API Key 字段
-- 前置依赖：V22 已成功将数据迁移到 provider_api_key 子表
-- 独立版本号确保此 DDL 操作可单独重试，不影响已迁移的数据
-- ============================================

ALTER TABLE `provider_config`
    DROP COLUMN `api_key_ciphertext`,
    DROP COLUMN `api_key_iv`;
