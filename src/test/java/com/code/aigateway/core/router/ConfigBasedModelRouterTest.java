package com.code.aigateway.core.router;

import com.code.aigateway.config.GatewayProperties;
import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.provider.ProviderType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigBasedModelRouterTest {

    @Test
    void route_success_returnsProviderRuntimeContext() {
        ConfigBasedModelRouter router = new ConfigBasedModelRouter(buildProperties("openai", "gpt-5.4"));

        RouteResult routeResult = router.route(buildRequest("gpt-5"));

        assertEquals(ProviderType.OPENAI, routeResult.getProviderType());
        assertEquals("openai", routeResult.getProviderName());
        assertEquals("gpt-5.4", routeResult.getTargetModel());
        assertEquals("https://api.openai.com", routeResult.getProviderBaseUrl());
        assertEquals("v1", routeResult.getProviderVersion());
        assertEquals(30, routeResult.getProviderTimeoutSeconds());
    }

    @Test
    void route_unsupportedProviderAlias_throwsGatewayException() {
        ConfigBasedModelRouter router = new ConfigBasedModelRouter(buildProperties("unsupported", "gpt-5.4"));

        GatewayException exception = assertThrows(GatewayException.class, () -> router.route(buildRequest("gpt-5")));

        assertEquals(ErrorCode.PROVIDER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void route_blankTargetModel_throwsModelNotFound() {
        ConfigBasedModelRouter router = new ConfigBasedModelRouter(buildProperties("openai", "   "));

        GatewayException exception = assertThrows(GatewayException.class, () -> router.route(buildRequest("gpt-5")));

        assertEquals(ErrorCode.MODEL_NOT_FOUND, exception.getErrorCode());
    }

    private GatewayProperties buildProperties(String providerName, String targetModel) {
        GatewayProperties gatewayProperties = new GatewayProperties();

        GatewayProperties.ModelAliasProperties aliasProperties = new GatewayProperties.ModelAliasProperties();
        aliasProperties.setProvider(providerName);
        aliasProperties.setModel(targetModel);
        gatewayProperties.setModelAliases(Map.of("gpt-5", aliasProperties));

        GatewayProperties.ProviderProperties providerProperties = new GatewayProperties.ProviderProperties();
        providerProperties.setEnabled(true);
        providerProperties.setBaseUrl("https://api.openai.com");
        providerProperties.setVersion("v1");
        providerProperties.setTimeoutSeconds(30);
        gatewayProperties.setProviders(Map.of(
                "openai", providerProperties,
                "unsupported", providerProperties
        ));
        return gatewayProperties;
    }

    private UnifiedRequest buildRequest(String model) {
        UnifiedRequest request = new UnifiedRequest();
        request.setModel(model);
        return request;
    }
}
