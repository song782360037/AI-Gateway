package com.code.aigateway.admin.model.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新增支持模型请求对象
 */
@Data
public class SupportedModelAddReq {

    /** 模型标识，如 gpt-4o */
    @NotBlank(message = "模型标识不能为空")
    private String modelId;

    /** 展示名称，如 GPT-4o */
    @NotBlank(message = "展示名称不能为空")
    private String displayName;

    /** 模型所有者，如 openai、anthropic */
    private String ownedBy = "";

    /** 是否启用，默认启用 */
    private Boolean enabled = true;

    /** 排序权重，值越小越靠前 */
    private Integer sortOrder = 0;
}
