package com.code.aigateway.core.router;

import com.code.aigateway.core.model.UnifiedRequest;

import java.util.List;

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
     * 根据请求中的模型名称进行路由（返回首选候选）
     *
     * @param request 统一的请求模型
     * @return 路由结果，包含目标提供商和模型信息
     */
    RouteResult route(UnifiedRequest request);

    /**
     * 返回所有可用候选路由（用于故障转移）
     * <p>
     * 默认实现返回单元素列表（仅首选候选）。
     * 持久化路由器可覆盖此方法返回全部按优先级排序的候选。
     * </p>
     *
     * @param request 统一的请求模型
     * @return 候选路由列表，按优先级从高到低排序
     */
    default List<RouteResult> routeAll(UnifiedRequest request) {
        return List.of(route(request));
    }
}
