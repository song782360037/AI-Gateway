package com.code.aigateway.admin.model.dataobject;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提供商 API Key 数据对象
 */
@Data
public class ProviderApiKeyDO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 所属提供商编码（对应 provider_config.provider_code）
     */
    private String providerCode;

    /**
     * 加密后的 API Key 密文（AES-256-GCM，Base64 编码）
     */
    private String apiKeyCiphertext;

    /**
     * AES-GCM IV 向量（Base64 编码）
     */
    private String apiKeyIv;

    /**
     * API Key 脱敏标识（前8后4格式），列表展示用
     */
    private String apiKeyPrefix;

    /**
     * 备注（如"生产主Key"、"备用Key"）
     */
    private String remark;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 权重（用于加权随机策略，值越大被选中概率越高）
     */
    private Integer weight;

    /**
     * 排序号（用于 FALLBACK 策略，值越小越优先）
     */
    private Integer sortOrder;

    /**
     * 乐观锁版本号
     */
    private Long versionNo;

    /**
     * 创建者
     */
    private String creator;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新者
     */
    private String updater;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记
     */
    private Boolean deleted;
}
