package com.code.aigateway.admin.model.dataobject;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提供商配置数据对象
 */
@Data
public class ProviderConfigDO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 提供商业务编码
     */
    private String providerCode;

    /**
     * 提供商类型
     */
    private String providerType;

    /**
     * 展示名称
     */
    private String displayName;

    /**
     * 是否启用，映射 MySQL bit(1)
     */
    private Boolean enabled;

    /**
     * 提供商基础地址
     */
    private String baseUrl;

    /**
     * 加密后的 API Key 密文
     */
    private String apiKeyCiphertext;

    /**
     * API Key 加密向量
     */
    private String apiKeyIv;

    /**
     * API 版本
     */
    private String apiVersion;

    /**
     * 请求超时时间，单位秒
     */
    private Integer timeoutSeconds;

    /**
     * 优先级，值越大优先级越高
     */
    private Integer priority;

    /**
     * 扩展配置 JSON
     */
    private String extConfigJson;

    /**
     * 乐观锁版本号
     */
    private Long versionNo;

    /**
     * 创建人
     */
    private String creator;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    private String updater;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记，映射 MySQL bit(1)
     */
    private Boolean deleted;
}
