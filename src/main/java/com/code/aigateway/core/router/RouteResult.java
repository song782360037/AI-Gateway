package com.code.aigateway.core.router;

import com.code.aigateway.provider.ProviderType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;

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
     * 提供商版本号
     */
    private String providerVersion;

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
}
