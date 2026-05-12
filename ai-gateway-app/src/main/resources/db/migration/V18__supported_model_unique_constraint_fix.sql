-- 修复 supported_model 表唯一约束：将 uk_model_id 改为 uk_model_id_deleted
-- 原来的 uk_model_id (model_id) 与软删除冲突，删除后无法重建同名模型
-- 新的 uk_model_id_deleted (model_id, deleted) 配合软删除时修改 model_id 的策略，避免冲突

-- 先删除旧的唯一索引
ALTER TABLE supported_model DROP INDEX uk_model_id;

-- 创建新的组合唯一索引
ALTER TABLE supported_model ADD UNIQUE INDEX uk_model_id_deleted (model_id, deleted)
    COMMENT '模型标识唯一（排除已删除），软删除时修改 model_id 避免冲突';
