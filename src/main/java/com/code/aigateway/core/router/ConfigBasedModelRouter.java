package com.code.aigateway.core.router;

import com.code.aigateway.config.GatewayProperties;
import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.provider.ProviderType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 根据配置文件路由
 */
@Component
@RequiredArgsConstructor
public class ConfigBasedModelRouter implements ModelRouter {

    private final GatewayProperties gatewayProperties;

    @Override
    public RouteResult route(UnifiedRequest request) {
        if (request.getModel() == null || request.getModel().isBlank()) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, "model is required");
        }

        GatewayProperties.ModelAliasProperties alias =
                gatewayProperties.getModelAliases() == null ? null : gatewayProperties.getModelAliases().get(request.getModel());

        if (alias == null) {
            throw new GatewayException(ErrorCode.MODEL_NOT_FOUND,
                    "model alias not found: " + request.getModel());
        }

        GatewayProperties.ProviderProperties providerProperties =
                gatewayProperties.getProviders() == null ? null : gatewayProperties.getProviders().get(alias.getProvider());

        if (providerProperties == null) {
            throw new GatewayException(ErrorCode.PROVIDER_NOT_FOUND,
                    "provider config not found: " + alias.getProvider());
        }

        if (!providerProperties.isEnabled()) {
            throw new GatewayException(ErrorCode.PROVIDER_DISABLED,
                    "provider is disabled: " + alias.getProvider());
        }

        return RouteResult.builder()
                .providerType(ProviderType.from(alias.getProvider()))
                .targetModel(alias.getModel())
                .providerBaseUrl(providerProperties.getBaseUrl())
                .build();
    }
}
