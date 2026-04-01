package com.code.aigateway.admin.model.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 切换路由规则启用/禁用状态请求对象
 */
@Data
public class ModelRedirectConfigToggleReq {

    /** 主键 ID，用于定位要切换状态的路由配置 */
    @NotNull(message = "ID 不能为空")
    private Long id;

    /** 乐观锁版本号，用于并发更新控制 */
    @NotNull(message = "版本号不能为空")
    private Long versionNo;
}
