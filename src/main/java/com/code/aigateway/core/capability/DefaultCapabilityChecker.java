package com.code.aigateway.core.capability;

import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.router.RouteResult;
import com.code.aigateway.provider.ProviderType;
import org.springframework.stereotype.Component;

/**
 * 默认能力检查器
 * <p>
 * 提供基本的能力检查实现，验证请求特性是否被目标提供商支持。
 * 当前实现主要检查工具调用能力。
 * </p>
 *
 * @author sst
 */
@Component
public class DefaultCapabilityChecker implements CapabilityChecker {

    /**
     * 验证请求能力
     * <p>
     * 当前检查项：
     * <ul>
     *   <li>工具调用：Gemini 提供商在 MVP 阶段暂不支持工具调用</li>
     * </ul>
     * </p>
     *
     * @param request      统一的请求模型
     * @param routeResult  路由结果
     * @throws GatewayException 当请求的能力不被目标提供商支持时抛出
     */
    @Override
    public void validate(UnifiedRequest request, RouteResult routeResult) {
        // 检查工具调用能力
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            // Gemini 在 MVP 阶段暂不支持工具调用
            if (routeResult.getProviderType() == ProviderType.GEMINI) {
                throw new GatewayException(
                        ErrorCode.CAPABILITY_NOT_SUPPORTED,
                        "tools are not supported in MVP for provider: " + routeResult.getProviderType()
                );
            }
        }
    }
}
