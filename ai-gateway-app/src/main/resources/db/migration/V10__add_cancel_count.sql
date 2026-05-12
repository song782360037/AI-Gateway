-- ============================================
-- AI-Gateway 流式取消状态支持
-- 版本: V10
-- 说明: request_stat_hourly 新增 cancel_count 列，区分流式请求的 CANCELLED 状态
-- ============================================

ALTER TABLE `request_stat_hourly`
    ADD COLUMN `cancel_count` int NOT NULL DEFAULT 0 COMMENT '取消数（客户端主动断开）' AFTER `error_count`;
