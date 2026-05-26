ALTER TABLE `request_log`
    ADD COLUMN `provider_key_id` bigint DEFAULT NULL COMMENT '选中的 provider_api_key 记录 ID，用于关联备注';

CREATE INDEX `idx_request_log_provider_key_id` ON `request_log` (`provider_key_id`);
