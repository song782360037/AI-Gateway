-- ============================================
-- AI-Gateway Auto 智能路由候选能力与评分字段
-- 版本: V15
-- 说明: 为 auto_route_candidate 增加智能选择所需的硬过滤能力与评分维度
-- ============================================

ALTER TABLE `auto_route_candidate`
    ADD COLUMN `supports_vision` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否支持视觉输入' AFTER `weight`,
    ADD COLUMN `supports_tools` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否支持工具调用' AFTER `supports_vision`,
    ADD COLUMN `supports_tool_choice_required` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否支持强制工具调用' AFTER `supports_tools`,
    ADD COLUMN `supports_reasoning` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否支持推理能力' AFTER `supports_tool_choice_required`,
    ADD COLUMN `supports_json` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否支持 JSON 或结构化输出' AFTER `supports_reasoning`,
    ADD COLUMN `supports_stream` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否支持流式输出' AFTER `supports_json`,
    ADD COLUMN `max_input_tokens` int DEFAULT NULL COMMENT '最大输入 Token 数，空或 0 表示不限制' AFTER `supports_stream`,
    ADD COLUMN `max_output_tokens` int DEFAULT NULL COMMENT '最大输出 Token 数，空或 0 表示不限制' AFTER `max_input_tokens`,
    ADD COLUMN `quality_score` int NOT NULL DEFAULT 50 COMMENT '质量评分，0-100' AFTER `max_output_tokens`,
    ADD COLUMN `latency_score` int NOT NULL DEFAULT 50 COMMENT '延迟评分，0-100，越高表示越快' AFTER `quality_score`,
    ADD COLUMN `cost_score` int NOT NULL DEFAULT 50 COMMENT '成本评分，0-100，越高表示越便宜' AFTER `latency_score`,
    ADD COLUMN `tool_score` int NOT NULL DEFAULT 50 COMMENT '工具调用适配评分，0-100' AFTER `cost_score`,
    ADD COLUMN `vision_score` int NOT NULL DEFAULT 50 COMMENT '视觉任务适配评分，0-100' AFTER `tool_score`,
    ADD COLUMN `reasoning_score` int NOT NULL DEFAULT 50 COMMENT '推理任务适配评分，0-100' AFTER `vision_score`,
    ADD COLUMN `reliability_score` int NOT NULL DEFAULT 50 COMMENT '可靠性评分，0-100' AFTER `reasoning_score`,
    ADD COLUMN `score_bias` int NOT NULL DEFAULT 0 COMMENT '评分偏置，可正可负' AFTER `reliability_score`;
