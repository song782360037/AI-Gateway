package com.code.aigateway.core.capability;

import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.router.RouteResult;
import org.springframework.stereotype.Component;

/**
 * 默认能力检查器
 * <p>
 * 当前阶段 tools、tool_choice、thinking、reasoning 等能力优先交由
 * Parser 与 ProviderClient 做语义归一和协议映射。
 * 这里仅保留最轻量的兜底入口，避免在主链路提前拦截可转换场景。
 * </p>
 *
 * @author sst
 */
@Component
public class DefaultCapabilityChecker implements CapabilityChecker {

    /**
     * 验证请求能力
     * <p>
     * reasoning / thinking 跨协议场景允许继续透传，由目标 ProviderClient
     * 在出站阶段完成字段映射，不在这里直接 reject。
     * </p>
     *
     * @param request      统一的请求模型
     * @param routeResult  路由结果
     */
    @Override
    public void validate(UnifiedRequest request, RouteResult routeResult) {
        // 当前不对 reasoning / thinking 做前置拦截，避免误伤可转换链路。
    }
}
