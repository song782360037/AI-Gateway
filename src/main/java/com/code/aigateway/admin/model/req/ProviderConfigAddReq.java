package com.code.aigateway.admin.model.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

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

    /** 提供商 API Key，新增时必须传入 */
    @NotBlank(message = "API Key 不能为空")
    private String apiKey;

    /** 提供商 API 版本号，部分提供商可选 */
    private String apiVersion;

    /** 调用超时时间，单位秒，默认 60 秒 */
    private Integer timeoutSeconds = 60;

    /** 提供商优先级，数值越大优先级越高 */
    private Integer priority = 0;

    /** 扩展配置 JSON，用于存储提供商个性化参数 */
    private String extConfigJson;
}
