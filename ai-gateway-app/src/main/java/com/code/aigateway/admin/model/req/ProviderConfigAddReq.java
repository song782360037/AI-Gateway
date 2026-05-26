package com.code.aigateway.admin.model.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 新增提供商配置请求对象
 *
 * <p>用于接收后台新增提供商配置时提交的参数。</p>
 */
@Data
public class ProviderConfigAddReq {

    /** 提供商编码，用于系统内唯一标识一个提供商实例 */
    @NotBlank(message = "提供商编码不能为空")
    private String providerCode;

    /** 提供商类型，例如 OPENAI、ANTHROPIC */
    @NotBlank(message = "提供商类型不能为空")
    private String providerType;

    /** 提供商展示名称，便于后台页面展示 */
    private String displayName;

    /** 是否启用，默认启用 */
    private Boolean enabled = true;

    /** 提供商基础地址，例如 OpenAI 兼容接口地址 */
    @NotBlank(message = "基础地址不能为空")
    private String baseUrl;

    /** Key 选择策略：ROUND_ROBIN / RANDOM / FALLBACK，默认 ROUND_ROBIN */
    @Pattern(regexp = "ROUND_ROBIN|RANDOM|FALLBACK", message = "Key 选择策略只能为 ROUND_ROBIN、RANDOM 或 FALLBACK")
    private String keySelectionStrategy = "ROUND_ROBIN";

    /** 调用超时时间，单位秒，默认 60 秒 */
    private Integer timeoutSeconds = 60;

    /** 提供商优先级，数值越大优先级越高 */
    private Integer priority = 0;

    /**
     * 支持的下游协议列表。
     * <p>为空或 null 时表示支持所有协议。</p>
     */
    private List<String> supportedProtocols;

    /** 提供商级别自定义请求头（键值对），覆盖全局同名头 */
    private Map<String, String> customHeaders;

    /** thinking 参数兼容模式：full=完整官方参数（默认），simplified=仅输出 type 字段（适用于 MiMo 等第三方 API） */
    @Pattern(regexp = "full|simplified", message = "thinking 兼容模式只能为 full 或 simplified")
    private String thinkingCompatMode = "full";

    /** 新增时一并添加的 API Key 列表（仅在新增接口有效） */
    @Valid
    @Size(max = 20, message = "单次最多添加 20 个 API Key")
    private List<ProviderApiKeyAddReq> apiKeys;
}
