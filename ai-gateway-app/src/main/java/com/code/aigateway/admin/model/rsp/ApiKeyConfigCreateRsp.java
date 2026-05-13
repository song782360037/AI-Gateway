package com.code.aigateway.admin.model.rsp;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 新增 API Key 响应（含完整 key，仅创建时返回一次）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ApiKeyConfigCreateRsp extends ApiKeyConfigRsp {

    /** 完整 API Key 明文（仅创建时返回，之后不可再获取） */
    private String apiKey;
}
