package com.code.aigateway.admin.model.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新模型重定向配置请求对象
 *
 * <p>用于后台更新模型别名路由配置。</p>
 */
@Data
public class ModelRedirectConfigUpdateReq {

    /** 主键 ID，用于定位要更新的路由配置 */
    @NotNull(message = "ID 不能为空")
    private Long id;

    /** 乐观锁版本号，用于并发更新控制 */
    @NotNull(message = "版本号不能为空")
    private Long versionNo;

    /** 模型别名，例如 gpt-4o */
    @NotBlank(message = "模型别名不能为空")
    private String aliasName;

    /** 目标提供商编码，用于关联 provider_config */
    @NotBlank(message = "提供商编码不能为空")
    private String providerCode;

    /** 目标模型名称，即实际发送给提供商的模型标识 */
    @NotBlank(message = "目标模型不能为空")
    private String targetModel;

    /** 是否启用 */
    private Boolean enabled = true;
}
