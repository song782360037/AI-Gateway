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
     * 请求超时时间，单位秒
     */
    private Integer timeoutSeconds;

    /**
     * 优先级，值越大优先级越高
     */
    private Integer priority;

    /**
     * 支持的下游协议（逗号分隔，如 OPENAI_CHAT,ANTHROPIC；NULL=全部支持）
     */
    private String supportedProtocols;

    /**
     * 提供商级别自定义请求头（JSON 键值对），覆盖全局同名头
     */
    private String customHeaders;

    /**
     * thinking 参数兼容模式
     * <ul>
     *   <li>"full" — 输出完整官方 thinking 参数（budget_tokens、summary、output_config 等），适用于原生 Anthropic API</li>
     *   <li>"simplified" — 仅输出 {"type":"enabled"} 或 {"type":"disabled"}，适用于第三方 Anthropic 兼容 API（如 MiMo）</li>
     * </ul>
     */
    private String thinkingCompatMode;

    /**
     * 规范化 thinking 兼容模式值。
     * <p>仅接受 "full" 和 "simplified"，其他值或 null 默认为 "full"。</p>
     *
     * @param mode 原始值
     * @return 规范化后的值（"full" 或 "simplified"）
     */
    public static String normalizeThinkingCompatMode(String mode) {
        if ("simplified".equalsIgnoreCase(mode)) {
            return "simplified";
        }
        return "full";
    }

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
