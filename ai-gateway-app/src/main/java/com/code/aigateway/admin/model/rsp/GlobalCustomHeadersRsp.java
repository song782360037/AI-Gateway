package com.code.aigateway.admin.model.rsp;

import lombok.Data;

import java.util.Map;

/**
 * 全局自定义请求头响应对象
 */
@Data
public class GlobalCustomHeadersRsp {

    /** 全局自定义请求头（键值对） */
    private Map<String, String> customHeaders;

    /** 乐观锁版本号 */
    private Long versionNo;
}
