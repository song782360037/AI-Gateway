package com.code.aigateway.core.router;

import com.code.aigateway.provider.ProviderType;
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
     * 目标模型名称（实际的模型名，非别名）
     */
    private String targetModel;

    /**
     * 提供商 API 基础地址
     */
    private String providerBaseUrl;
}
