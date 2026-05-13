package com.code.aigateway.admin.model.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 全局自定义请求头更新请求对象
 */
@Data
public class GlobalCustomHeadersUpdateReq {

    /** 乐观锁版本号 */
    @NotNull(message = "版本号不能为空")
    private Long versionNo;

    /** 全局自定义请求头（键值对） */
    private Map<String, String> customHeaders;
}
