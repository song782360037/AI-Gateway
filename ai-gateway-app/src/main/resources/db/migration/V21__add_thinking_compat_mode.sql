-- 为 provider_config 增加 thinking 兼容模式字段
-- 用于标识第三方 API 对 thinking 参数的兼容程度，避免发送上游不识别的字段导致 400 错误
-- 默认 'full'：输出完整官方 thinking 参数（budget_tokens、summary、output_config 等）
-- 'simplified'：仅输出 {"type":"enabled"}，适用于 MiMo 等第三方 Anthropic 兼容 API
ALTER TABLE provider_config
    ADD COLUMN thinking_compat_mode VARCHAR(20) NOT NULL DEFAULT 'full'
        COMMENT 'thinking 参数兼容模式：full=完整官方参数，simplified=仅输出 type 字段'
        AFTER custom_headers;

-- 回滚语句（需在 Flyway undo 迁移中执行）：
-- ALTER TABLE provider_config DROP COLUMN thinking_compat_mode;
