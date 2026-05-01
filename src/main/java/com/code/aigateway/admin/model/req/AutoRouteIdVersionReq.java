package com.code.aigateway.admin.model.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Auto 智能路由通用 ID + 版本号请求对象
 * <p>
 * 适用于 toggle、delete 等仅需 id + versionNo 的操作。
 * </p>
 */
@Data
public class AutoRouteIdVersionReq {

    @NotNull(message = "ID 不能为空")
    private Long id;

    @NotNull(message = "版本号不能为空")
    private Long versionNo;
}
