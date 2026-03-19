package com.code.aigateway.provider;

import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Provider client factory.
 */
@Component
@RequiredArgsConstructor
public class ProviderClientFactory {

    private final List<ProviderClient> providerClients;

    public ProviderClient getClient(ProviderType providerType) {
        return providerClients.stream()
                .filter(client -> client.getProviderType() == providerType)
                .findFirst()
                .orElseThrow(() -> new GatewayException(
                        ErrorCode.PROVIDER_NOT_FOUND,
                        "provider client not found: " + providerType
                ));
    }
}
