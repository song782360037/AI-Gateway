package com.code.aigateway.admin.model.dataobject;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支持模型配置数据对象
 */
@Data
public class SupportedModelDO {

    /** 主键 ID */
    private Long id;

    /** 模型标识，如 gpt-4o，对应 /v1/models 返回的 id */
    private String modelId;

    /** 展示名称，如 GPT-4o */
    private String displayName;

    /** 模型所有者，如 openai、anthropic */
    private String ownedBy;

    /** 是否启用，映射 MySQL bit(1) */
    private Boolean enabled;

    /** 排序权重，值越小越靠前 */
    private Integer sortOrder;

    /** 乐观锁版本号 */
    private Long versionNo;

    /** 创建人 */
    private String creator;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private String updater;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记，映射 MySQL bit(1) */
    private Boolean deleted;
}
