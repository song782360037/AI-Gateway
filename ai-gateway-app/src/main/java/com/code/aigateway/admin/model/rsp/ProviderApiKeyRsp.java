package com.code.aigateway.admin.model.rsp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提供商 API Key 响应对象
 */
@Data
public class ProviderApiKeyRsp {

    /** 主键 ID */
    private Long id;

    /** 所属提供商编码 */
    private String providerCode;

    /** 脱敏后的 API Key（前8后4格式） */
    private String apiKeyMasked;

    /** 备注 */
    private String remark;

    /** 是否启用 */
    private Boolean enabled;

    /** 权重 */
    private Integer weight;

    /** 排序号 */
    private Integer sortOrder;

    /** 乐观锁版本号 */
    private Long versionNo;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
