package com.code.aigateway.admin.model.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 查询提供商 API Key 列表请求对象
 */
@Data
public class ProviderApiKeyListReq {

    /** 所属提供商编码 */
    @NotBlank(message = "提供商编码不能为空")
    private String providerCode;
}
