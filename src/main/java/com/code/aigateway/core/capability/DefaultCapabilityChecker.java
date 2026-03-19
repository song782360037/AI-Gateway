package com.code.aigateway.core.capability;

import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.router.RouteResult;
import com.code.aigateway.provider.ProviderType;
import org.springframework.stereotype.Component;

/**
 * 默认能力检查器
 */
@Component
public class DefaultCapabilityChecker implements CapabilityChecker {

    @Override
    public void validate(UnifiedRequest request, RouteResult routeResult) {
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            if (routeResult.getProviderType() == ProviderType.GEMINI) {
                throw new GatewayException(
                        ErrorCode.CAPABILITY_NOT_SUPPORTED,
                        "tools are not supported in MVP for provider: " + routeResult.getProviderType()
                );
            }
        }
    }
}
