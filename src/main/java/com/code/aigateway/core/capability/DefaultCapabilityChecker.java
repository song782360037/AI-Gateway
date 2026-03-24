package com.code.aigateway.core.capability;

import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.model.UnifiedMessage;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.router.RouteResult;
import org.springframework.stereotype.Component;

/**
 * 默认能力检查器
 * <p>
 * 提供基本的能力检查实现，验证请求特性是否被当前网关输出契约支持。
 * 当前实现采用“文本优先”范围，工具调用相关能力统一显式拒绝，
 * 避免请求侧看起来可用、响应侧却无法安全表达的问题。
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
     *   <li>tools / tool_choice</li>
     *   <li>assistant 历史 tool_calls</li>
     *   <li>tool 角色消息与 tool_call_id</li>
     * </ul>
     * </p>
     *
     * @param request      统一的请求模型
     * @param routeResult  路由结果
     * @throws GatewayException 当请求的能力不被目标提供商支持时抛出
     */
    @Override
    public void validate(UnifiedRequest request, RouteResult routeResult) {
        if (!containsToolingPayload(request)) {
            return;
        }
        throw new GatewayException(
                ErrorCode.CAPABILITY_NOT_SUPPORTED,
                "tools are not supported in current gateway response contract for provider: " + routeResult.getProviderType()
        );
    }

    /**
     * 判断请求中是否包含工具调用相关负载。
     * 由于当前阶段只支持文本型输出，因此只要请求进入工具调用语义，
     * 就需要在调用 provider 之前提前拒绝。
     *
     * @param request 统一请求
     * @return 是否包含工具调用负载
     */
    private boolean containsToolingPayload(UnifiedRequest request) {
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            return true;
        }
        if (request.getToolChoice() != null) {
            return true;
        }
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return false;
        }

        for (UnifiedMessage message : request.getMessages()) {
            if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
                return true;
            }
            if (message.getToolCallId() != null && !message.getToolCallId().isBlank()) {
                return true;
            }
            if ("tool".equals(message.getRole())) {
                return true;
            }
        }
        return false;
    }
}
