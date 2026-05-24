package com.code.aigateway.admin.model.rsp;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 提供商配置响应对象
 *
 * <p>用于后台返回提供商配置详情或列表数据。</p>
 * <p>API Key 已拆分至独立子表管理，此处仅返回 Key 选择策略和可用 Key 数量。</p>
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

    /** Key 选择策略：ROUND_ROBIN / RANDOM / FALLBACK */
    private String keySelectionStrategy;

    /** 启用中的 API Key 数量 */
    private Integer apiKeyCount;

    /** 调用超时时间，单位秒 */
    private Integer timeoutSeconds;

    /** 提供商优先级，数值越大优先级越高 */
    private Integer priority;

    /** 支持的下游协议列表，空表示支持所有 */
    private List<String> supportedProtocols;

    /** 提供商级别自定义请求头（键值对） */
    private Map<String, String> customHeaders;

    /** thinking 参数兼容模式：full / simplified */
    private String thinkingCompatMode;

    /** 乐观锁版本号 */
    private Long versionNo;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
