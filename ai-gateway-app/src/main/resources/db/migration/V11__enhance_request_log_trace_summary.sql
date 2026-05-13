-- ============================================
-- AI-Gateway 请求链路追踪摘要字段增强
-- 版本: V11
-- 说明: 为 request_log 增加请求路径、协议、治理计数和上游归因字段
-- ============================================

ALTER TABLE `request_log`
    ADD COLUMN `response_protocol` varchar(32) DEFAULT NULL COMMENT '响应协议' AFTER `provider_type`,
    ADD COLUMN `request_path` varchar(255) DEFAULT NULL COMMENT '请求路径' AFTER `response_protocol`,
    ADD COLUMN `http_method` varchar(16) DEFAULT NULL COMMENT 'HTTP方法' AFTER `request_path`,
    ADD COLUMN `api_key_prefix` varchar(32) DEFAULT NULL COMMENT 'API Key前缀（脱敏）' AFTER `http_method`,
    ADD COLUMN `candidate_count` int DEFAULT NULL COMMENT '候选路由数' AFTER `api_key_prefix`,
    ADD COLUMN `attempt_count` int DEFAULT NULL COMMENT '候选尝试次数' AFTER `candidate_count`,
    ADD COLUMN `failover_count` int DEFAULT NULL COMMENT 'Failover次数' AFTER `attempt_count`,
    ADD COLUMN `retry_count` int DEFAULT NULL COMMENT '重试次数' AFTER `failover_count`,
    ADD COLUMN `circuit_open_skipped_count` int DEFAULT NULL COMMENT '熔断打开跳过次数' AFTER `retry_count`,
    ADD COLUMN `rate_limit_triggered` bit(1) DEFAULT NULL COMMENT '是否命中限流' AFTER `circuit_open_skipped_count`,
    ADD COLUMN `upstream_http_status` int DEFAULT NULL COMMENT '上游HTTP状态码' AFTER `rate_limit_triggered`,
    ADD COLUMN `upstream_error_type` varchar(64) DEFAULT NULL COMMENT '上游错误类型' AFTER `upstream_http_status`,
    ADD COLUMN `terminal_stage` varchar(32) DEFAULT NULL COMMENT '链路终止阶段' AFTER `upstream_error_type`;

CREATE INDEX `idx_request_log_request_id` ON `request_log` (`request_id`);
