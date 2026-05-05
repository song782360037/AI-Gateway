package com.code.aigateway.admin.model.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 查询支持模型请求对象
 */
@Data
public class SupportedModelQueryReq {

    /** 模型标识，支持模糊查询 */
    private String modelId;

    /** 展示名称，支持模糊查询 */
    private String displayName;

    /** 模型所有者，支持模糊查询 */
    private String ownedBy;

    /** 是否启用 */
    private Boolean enabled;

    /** 页码，默认第 1 页 */
    @Min(value = 1, message = "页码必须大于等于 1")
    private int page = 1;

    /** 每页条数，默认 20，最大 100 */
    @Min(value = 1, message = "每页条数必须大于等于 1")
    @Max(value = 100, message = "每页条数不能超过 100")
    private int pageSize = 20;
}
