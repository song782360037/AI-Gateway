package com.code.aigateway.admin.model.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * Auto 智能路由配置分页查询请求对象
 */
@Data
public class AutoRouteConfigQueryReq {

    private String routeKey;

    private String displayName;

    private Boolean enabled;

    @Min(value = 1, message = "页码必须大于 0")
    private int page = 1;

    @Min(value = 1, message = "每页大小必须大于 0")
    @Max(value = 100, message = "每页大小不能超过 100")
    private int pageSize = 10;
}
