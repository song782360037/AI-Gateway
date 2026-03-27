package com.code.aigateway.admin.model.rsp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提供商配置响应对象
 *
 * <p>用于后台返回提供商配置详情或列表数据。</p>
 * <p>出于安全考虑，仅返回掩码后的 API Key。</p>
 */
@Data
public class ProviderConfigRsp {

    /** 主键 ID */
    private Long id;

    /** 提供商编码，用于系统内唯一标识一个提供商实例 */
    private String providerCode;

    /** 提供商类型，例如 OPENAI、ANTHROPIC */
    private String providerType;

    /** 提供商展示名称，便于后台页面展示 */
    private String displayName;

    /** 是否启用 */
    private Boolean enabled;

    /** 提供商基础地址 */
    private String baseUrl;

    /** 掩码后的 API Key，避免敏感信息明文回显 */
    private String apiKeyMasked;

    /** 提供商 API 版本号 */
    private String apiVersion;

    /** 调用超时时间，单位秒 */
    private Integer timeoutSeconds;

    /** 提供商优先级，数值越大优先级越高 */
    private Integer priority;

    /** 扩展配置 JSON，用于存储提供商个性化参数 */
    private String extConfigJson;

    /** 乐观锁版本号 */
    private Long versionNo;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
