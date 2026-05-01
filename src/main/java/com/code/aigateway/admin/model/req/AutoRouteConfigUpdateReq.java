package com.code.aigateway.admin.model.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新 Auto 智能路由配置请求对象
 */
@Data
public class AutoRouteConfigUpdateReq {

    @NotNull(message = "ID 不能为空")
    private Long id;

    @NotNull(message = "版本号不能为空")
    private Long versionNo;

    @NotBlank(message = "路由键不能为空")
    @Pattern(regexp = "^[a-z0-9_-]{1,64}$", message = "路由键只能包含小写字母、数字、短横线和下划线")
    private String routeKey;

    @NotBlank(message = "配置名称不能为空")
    @Size(max = 128, message = "配置名称不能超过 128 个字符")
    private String displayName;

    @Size(max = 512, message = "配置说明不能超过 512 个字符")
    private String description;

    private Boolean enabled = true;

    private String selectionStrategy = "SMART_SCORE";
}
