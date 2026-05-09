-- ============================================
-- AI-Gateway 思考配置与链路追踪增强
-- 版本: V20
-- 说明: 为 request_log 增加思考配置字段、详细链路追踪和首token响应时间
-- ============================================

ALTER TABLE `request_log`
    -- 思考配置相关字段
    ADD COLUMN `thinking_enabled` bit(1) DEFAULT NULL COMMENT '是否开启思考' AFTER `terminal_stage`,
    ADD COLUMN `thinking_depth` varchar(32) DEFAULT NULL COMMENT '思考深度（budgetTokens或effort）' AFTER `thinking_enabled`,
    ADD COLUMN `thinking_mapped` bit(1) DEFAULT NULL COMMENT '是否映射思考（ReasoningSemanticMapper）' AFTER `thinking_depth`,
    
    -- 链路追踪详细信息
    ADD COLUMN `trace_details_json` json DEFAULT NULL COMMENT '详细链路追踪信息（JSON格式）' AFTER `thinking_mapped`,
    
    -- 首token响应时间
    ADD COLUMN `first_token_latency_ms` int DEFAULT NULL COMMENT '首token响应时间（毫秒）' AFTER `trace_details_json`;

-- 为首token响应时间添加索引，便于按延迟排序查询
CREATE INDEX `idx_request_log_first_token_latency` ON `request_log` (`first_token_latency_ms`);