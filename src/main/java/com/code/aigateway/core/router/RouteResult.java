package com.code.aigateway.core.router;

import com.code.aigateway.provider.ProviderType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RouteResult {
    private ProviderType providerType;
    private String targetModel;
    private String providerBaseUrl;
}
