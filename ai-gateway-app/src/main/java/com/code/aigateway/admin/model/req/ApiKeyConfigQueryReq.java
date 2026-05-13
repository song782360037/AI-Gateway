package com.code.aigateway.admin.model.req;

import lombok.Data;

/**
 * 查询 API Key 配置请求对象
 */
@Data
public class ApiKeyConfigQueryReq {

    /** 名称（模糊匹配） */
    private String name;

    /** 状态筛选：ACTIVE / DISABLED */
    private String status;

    /** 当前页码，从 1 开始 */
    private int page = 1;

    /** 每页大小 */
    private int pageSize = 20;
}
