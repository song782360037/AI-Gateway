-- 支持模型表：管理对外暴露的模型列表，供 /v1/models 接口返回
CREATE TABLE IF NOT EXISTS supported_model (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    model_id    VARCHAR(128) NOT NULL                COMMENT '模型标识，如 gpt-4o，对应 /v1/models 返回的 id',
    display_name VARCHAR(128) NOT NULL               COMMENT '展示名称，如 GPT-4o',
    owned_by    VARCHAR(64)  NOT NULL DEFAULT ''      COMMENT '模型所有者，如 openai、anthropic',
    enabled     BIT(1)       NOT NULL DEFAULT 1       COMMENT '是否启用，启用才在 /v1/models 中返回',
    sort_order  INT          NOT NULL DEFAULT 0       COMMENT '排序权重，值越小越靠前',
    version_no  BIGINT       NOT NULL DEFAULT 0       COMMENT '乐观锁版本号',
    creator     VARCHAR(64)  NOT NULL DEFAULT ''      COMMENT '创建人',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updater     VARCHAR(64)  NOT NULL DEFAULT ''      COMMENT '更新人',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     BIT(1)       NOT NULL DEFAULT 0       COMMENT '逻辑删除标记',
    PRIMARY KEY (id),
    UNIQUE KEY uk_model_id (model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支持模型配置';
