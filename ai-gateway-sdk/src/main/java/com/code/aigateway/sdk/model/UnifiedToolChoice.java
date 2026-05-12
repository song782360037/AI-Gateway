package com.code.aigateway.sdk.model;

import lombok.Data;

/**
 * 统一的工具选择配置
 */
@Data
public class UnifiedToolChoice {

    /** 选择类型：auto / none / required / specific */
    private String type;

    /** 指定的工具名称（type 为 specific 时使用） */
    private String toolName;
}
