package com.code.aigateway.core.capability;

import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.router.RouteResult;
import org.springframework.stereotype.Component;

/**
 * 默认能力检查器
 * <p>
 * 提供基本的能力检查实现，验证请求特性是否被当前网关输出契约支持。
 * tools / tool_choice 等功能由各 ProviderClient 负责转发，
 * 若上游不支持则由 provider 侧返回错误。
 * </p>
 *
 * @author sst
 */
@Component
public class DefaultCapabilityChecker implements CapabilityChecker {

    /**
     * 验证请求能力
     * <p>
     * 当前阶段 tools、tool_choice、tool 角色消息等
     * 已由 ProviderClient 负责转发，无需在网关层拦截。
     * 如后续需要针对特定 provider 做能力限制，可在此扩展。
     * </p>
     *
     * @param request      统一的请求模型
     * @param routeResult  路由结果
     */
    @Override
    public void validate(UnifiedRequest request, RouteResult routeResult) {
        // tools 相关能力已由各 ProviderClient 支持并转发
    }
}
