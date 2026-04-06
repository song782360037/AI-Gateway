-- 提供商增加支持的下游协议配置
-- NULL 或空串表示支持所有协议，逗号分隔多个协议
ALTER TABLE provider_config
    ADD COLUMN supported_protocols VARCHAR(255) DEFAULT NULL
        COMMENT '支持的下游协议（逗号分隔，如 OPENAI_CHAT,ANTHROPIC；NULL=全部支持）'
        AFTER priority;
