package com.code.aigateway.admin.model.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新增模型重定向配置请求对象
 *
 * <p>用于维护模型别名到目标提供商模型的映射关系。</p>
 */
@Data
public class ModelRedirectConfigAddReq {

    /** 模型别名，例如 gpt-4o */
    @NotBlank(message = "模型别名不能为空")
    private String aliasName;

    /** 目标提供商编码，用于关联 provider_config */
    @NotBlank(message = "提供商编码不能为空")
    private String providerCode;

    /** 目标模型名称，即实际发送给提供商的模型标识 */
    @NotBlank(message = "目标模型不能为空")
    private String targetModel;

    /** 是否启用，默认启用 */
    private Boolean enabled = true;
}
