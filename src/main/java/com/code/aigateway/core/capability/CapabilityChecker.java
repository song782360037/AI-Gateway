package com.code.aigateway.core.capability;

import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.router.RouteResult;

/**
 * 能力检查器接口
 * <p>
 * 负责验证请求中的功能是否被目标提供商和模型支持。
 * 在请求被路由后、调用提供商之前执行验证。
 * </p>
 *
 * @author sst
 */
public interface CapabilityChecker {

    /**
     * 验证请求能力
     * <p>
     * 检查请求中使用的特性（如工具调用、多模态等）
     * 是否被目标提供商支持。如果不支持则抛出异常。
     * </p>
     *
     * @param request      统一的请求模型
     * @param routeResult  路由结果，包含目标提供商信息
     * @throws com.code.aigateway.core.error.GatewayException 当能力不被支持时抛出
     */
    void validate(UnifiedRequest request, RouteResult routeResult);
}
