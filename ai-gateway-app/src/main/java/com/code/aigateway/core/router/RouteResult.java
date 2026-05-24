package com.code.aigateway.core.router;

import com.code.aigateway.provider.ProviderType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 路由结果
 * <p>
 * 包含模型路由后的目标提供商和模型信息
 * </p>
 *
 * @author sst
 */
@Data
@Builder
public class RouteResult {

    /**
     * 目标提供商类型
     */
    private ProviderType providerType;

    /**
     * 提供商名称
     */
    private String providerName;

    /**
     * 目标模型名称（实际的模型名，非别名）
     */
    private String targetModel;

    /**
     * 提供商 API 基础地址
     */
    private String providerBaseUrl;

    /**
     * 提供商请求超时时间（秒）
     */
    private Integer providerTimeoutSeconds;

    /**
     * 运行时提供商 API Key。
     * <p>
     * 该字段仅在服务内部透传给 provider client，
     * 绝不能参与日志打印或序列化输出。
     * </p>
     */
    @JsonIgnore
    private String providerApiKey;

    /**
     * 自定义请求头（全局+提供商级别已合并）。
     * <p>
     * 提供商级别覆盖全局同名头，认证相关头不在此字段中。
     * 序列化时排除，防止敏感自定义头泄露到日志或 API 响应。
     * </p>
     */
    @JsonIgnore
    private Map<String, String> customHeaders;

    /**
     * thinking 参数兼容模式，从提供商配置透传而来。
     */
    @JsonIgnore
    private String thinkingCompatMode;

    /**
     * 该 Provider 所有可用的 API Key 列表（供 Provider 内部 Key 降级重试使用）。
     */
    @JsonIgnore
    private List<ProviderKeyEntry> providerKeyEntries;

    /**
     * Key 选择策略
     */
    @JsonIgnore
    private KeySelectionStrategy keySelectionStrategy;

    /**
     * 本次请求选中的 API Key 脱敏标识（前8后4格式），用于日志记录和统计采集。
     * 序列化时排除，避免泄露到 Redis/日志。
     */
    @JsonIgnore
    private String usedApiKeyPrefix;
}
