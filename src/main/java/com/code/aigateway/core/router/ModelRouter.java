package com.code.aigateway.core.router;

import com.code.aigateway.core.model.UnifiedRequest;

/**
 * 模型路由器接口
 * <p>
 * 负责根据请求中的模型名称，路由到对应的提供商和实际模型。
 * 支持模型别名映射，将用户友好的模型名映射到实际的模型。
 * </p>
 *
 * @author sst
 */
public interface ModelRouter {

    /**
     * 根据请求中的模型名称进行路由
     *
     * @param request 统一的请求模型
     * @return 路由结果，包含目标提供商和模型信息
     */
    RouteResult route(UnifiedRequest request);
}
