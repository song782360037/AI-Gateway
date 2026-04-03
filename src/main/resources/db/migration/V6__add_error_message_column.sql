-- ============================================
-- AI-Gateway 请求日志错误详情
-- 版本: V6
-- 说明: 为 request_log 表新增 error_message 列，记录上游原始错误描述
-- ============================================

ALTER TABLE `request_log`
    ADD COLUMN `error_message` varchar(512) DEFAULT NULL COMMENT '错误详情（失败时记录上游原始错误描述）'
    AFTER `error_code`;
