package com.code.aigateway.admin.model.req;

import com.code.aigateway.core.model.UnifiedRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Auto 智能路由评估请求对象
 */
@Data
public class AutoRouteEvaluateReq {

    @NotBlank(message = "路由键不能为空")
    @Pattern(regexp = "^[a-z0-9_-]{1,64}$", message = "路由键只能包含小写字母、数字、短横线和下划线")
    private String routeKey;

    @Valid
    @NotNull(message = "示例请求不能为空")
    private UnifiedRequest request;
}
