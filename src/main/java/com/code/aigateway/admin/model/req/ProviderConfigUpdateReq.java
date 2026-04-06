package com.code.aigateway.admin.model.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 更新提供商配置请求对象
 *
 * <p>用于接收后台更新提供商配置时提交的参数。</p>
 * <p>其中 apiKey 为可选字段，传空字符串或 null 表示不更新密钥。</p>
 */
@Data
public class ProviderConfigUpdateReq {

    /** 主键 ID，用于定位要更新的提供商配置 */
    @NotNull(message = "ID 不能为空")
    private Long id;

    /** 乐观锁版本号，用于并发更新控制 */
    @NotNull(message = "版本号不能为空")
    private Long versionNo;

    /** 提供商编码，用于系统内唯一标识一个提供商实例 */
    @NotBlank(message = "提供商编码不能为空")
    private String providerCode;

    /** 提供商类型，例如 OPENAI、ANTHROPIC */
    @NotBlank(message = "提供商类型不能为空")
    private String providerType;

    /** 提供商展示名称，便于后台页面展示 */
    private String displayName;

    /** 是否启用 */
    private Boolean enabled = true;

    /** 提供商基础地址，例如 OpenAI 兼容接口地址 */
    @NotBlank(message = "基础地址不能为空")
    private String baseUrl;

    /**
     * 提供商 API Key。
     *
     * <p>更新场景下该字段可为空，表示保持原有密钥不变。</p>
     */
    private String apiKey;

    /** 调用超时时间，单位秒，默认 60 秒 */
    private Integer timeoutSeconds = 60;

    /** 提供商优先级，数值越大优先级越高 */
    private Integer priority = 0;

    /**
     * 支持的下游协议列表。
     * <p>为空或 null 时表示支持所有协议。</p>
     */
    private List<String> supportedProtocols;
}
