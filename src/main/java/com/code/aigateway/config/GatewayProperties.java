package com.code.aigateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private AuthProperties auth;
    private Map<String, ModelAliasProperties> modelAliases;
    private Map<String, ProviderProperties> providers;

    @Data
    public static class AuthProperties {
        private boolean enabled = true;
        private List<String> apiKeys;
    }

    @Data
    public static class ModelAliasProperties {
        private String provider;
        private String model;
    }

    @Data
    public static class ProviderProperties {
        private boolean enabled;
        private String baseUrl;
        private String apiKey;
        private String version;
        private Integer timeoutSeconds = 60;
    }
}
