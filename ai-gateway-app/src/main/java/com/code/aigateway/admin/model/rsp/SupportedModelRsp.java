package com.code.aigateway.admin.model.rsp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支持模型响应对象
 */
@Data
public class SupportedModelRsp {

    /** 主键 ID */
    private Long id;

    /** 模型标识 */
    private String modelId;

    /** 展示名称 */
    private String displayName;

    /** 模型所有者 */
    private String ownedBy;

    /** 是否启用 */
    private Boolean enabled;

    /** 排序权重 */
    private Integer sortOrder;

    /** 乐观锁版本号 */
    private Long versionNo;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
