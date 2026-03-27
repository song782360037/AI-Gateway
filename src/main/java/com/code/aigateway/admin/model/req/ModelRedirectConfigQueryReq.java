package com.code.aigateway.admin.model.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 查询模型重定向配置请求对象
 *
 * <p>用于后台分页查询模型别名路由配置列表。</p>
 */
@Data
public class ModelRedirectConfigQueryReq {

    /** 模型别名，支持模糊查询 */
    private String aliasName;

    /** 提供商编码，精确匹配 */
    private String providerCode;

    /** 目标模型名称，支持模糊查询 */
    private String targetModel;

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
