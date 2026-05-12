package com.code.aigateway.admin.model.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 删除支持模型请求对象
 */
@Data
public class SupportedModelDeleteReq {

    /** 主键 ID */
    @NotNull(message = "ID 不能为空")
    private Long id;

    /** 乐观锁版本号 */
    @NotNull(message = "版本号不能为空")
    private Long versionNo;
}
