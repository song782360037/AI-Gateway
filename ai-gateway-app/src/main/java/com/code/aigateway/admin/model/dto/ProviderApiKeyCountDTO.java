package com.code.aigateway.admin.model.dto;

import lombok.Data;

/**
 * Provider API Key 数量统计 DTO（按 providerCode 分组）
 */
@Data
public class ProviderApiKeyCountDTO {
    private String providerCode;
    private Integer cnt;
}
