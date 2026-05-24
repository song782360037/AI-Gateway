package com.code.aigateway.admin.model.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新提供商 API Key 请求对象（不支持更新密钥明文，仅更新备注/权重/排序等）
 */
@Data
public class ProviderApiKeyUpdateReq {

    /** 主键 ID */
    @NotNull(message = "ID 不能为空")
    private Long id;

    /** 乐观锁版本号 */
    @NotNull(message = "版本号不能为空")
    private Long versionNo;

    /** 备注 */
    private String remark;

    /** 是否启用 */
    private Boolean enabled;

    /** 权重 */
    private Integer weight;

    /** 排序号 */
    private Integer sortOrder;
}
