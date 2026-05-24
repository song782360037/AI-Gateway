package com.code.aigateway.admin.model.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新增提供商 API Key 请求对象
 */
@Data
public class ProviderApiKeyAddReq {

    /** 所属提供商编码 */
    @NotBlank(message = "提供商编码不能为空")
    private String providerCode;

    /** API Key 明文（入库前加密） */
    @NotBlank(message = "API Key 不能为空")
    private String apiKey;

    /** 备注 */
    private String remark;

    /** 是否启用，默认启用 */
    private Boolean enabled = true;

    /** 权重（用于加权随机策略） */
    private Integer weight = 100;

    /** 排序号（用于 FALLBACK 策略） */
    private Integer sortOrder = 0;
}
