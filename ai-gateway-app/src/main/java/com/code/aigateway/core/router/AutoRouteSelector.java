package com.code.aigateway.core.router;

import com.code.aigateway.sdk.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.sdk.model.UnifiedRequest;
import com.code.aigateway.core.router.auto.AutoRequestProfile;
import com.code.aigateway.core.router.auto.AutoRouteRequestClassifier;
import com.code.aigateway.core.router.auto.AutoRouteScorer;
import com.code.aigateway.provider.ProviderType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Auto 智能路由选择器。
 */
@Component
public class AutoRouteSelector {

    private static final String AUTO_MODEL = "auto";
    private static final String AUTO_PREFIX = "auto:";
    private static final String DEFAULT_ROUTE_KEY = "default";

    private final AutoRouteRequestClassifier requestClassifier;
    private final AutoRouteScorer routeScorer;
    private final ProviderKeySelector providerKeySelector;

    public AutoRouteSelector(AutoRouteRequestClassifier requestClassifier,
                             AutoRouteScorer routeScorer,
                             ProviderKeySelector providerKeySelector) {
        this.requestClassifier = requestClassifier;
        this.routeScorer = routeScorer;
        this.providerKeySelector = providerKeySelector;
    }

    public boolean isAutoModel(String modelName) {
        if (modelName == null) {
            return false;
        }
        String normalized = modelName.trim().toLowerCase();
        return AUTO_MODEL.equals(normalized) || normalized.startsWith(AUTO_PREFIX);
    }

    public RouteResult select(RoutingConfigSnapshot snapshot, UnifiedRequest request) {
        List<RouteResult> results = selectAll(snapshot, request);
        if (results.isEmpty()) {
            throw new GatewayException(ErrorCode.PROVIDER_NOT_FOUND,
                    "no auto route candidate available for model " + request.getModel());
        }
        return results.get(0);
    }

    public List<RouteResult> selectAll(RoutingConfigSnapshot snapshot, UnifiedRequest request) {
        String routeKey = resolveRouteKey(request.getModel());
        RoutingConfigSnapshot.AutoRouteEntry entry = snapshot.getAutoRoute(routeKey);
        if (entry == null) {
            throw new GatewayException(ErrorCode.MODEL_NOT_FOUND, "auto route not configured: " + routeKey);
        }

        AutoRequestProfile profile = requestClassifier.classify(request);
        List<RouteCandidate> rankedCandidates = routeScorer.rank(entry.candidates(), profile, request.getRequestProtocol());
        if (rankedCandidates.isEmpty()) {
            throw new GatewayException(ErrorCode.PROVIDER_NOT_FOUND,
                    "no auto route candidates match request profile for model " + request.getModel());
        }
        return rankedCandidates.stream()
                .map(candidate -> buildRouteResult(candidate, request.getModel(), snapshot))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private String resolveRouteKey(String modelName) {
        String normalized = modelName.trim().toLowerCase();
        if (AUTO_MODEL.equals(normalized)) {
            return DEFAULT_ROUTE_KEY;
        }
        String routeKey = normalized.substring(AUTO_PREFIX.length()).trim();
        if (routeKey.isEmpty()) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, "auto route key is required");
        }
        return routeKey;
    }

    private RouteResult buildRouteResult(RouteCandidate candidate, String modelName, RoutingConfigSnapshot snapshot) {
        RoutingConfigSnapshot.ProviderEntry providerEntry = snapshot.getProviderMap().get(candidate.getProviderCode());
        if (providerEntry == null || providerEntry.apiKeys() == null || providerEntry.apiKeys().isEmpty()) {
            return null;
        }
        ProviderKeyEntry selectedKey = providerKeySelector.select(providerEntry.providerCode(), providerEntry.apiKeys(), providerEntry.keySelectionStrategy());
        if (selectedKey == null) {
            return null;
        }
        return RouteResult.builder()
                .providerType(resolveProviderType(candidate.getProviderType(), modelName))
                .providerName(candidate.getProviderCode())
                .targetModel(candidate.getTargetModel())
                .providerBaseUrl(candidate.getProviderBaseUrl())
                .providerTimeoutSeconds(candidate.getProviderTimeoutSeconds())
                .providerApiKey(selectedKey.apiKey())
                .customHeaders(candidate.getCustomHeaders())
                .providerKeyEntries(providerEntry.apiKeys())
                .keySelectionStrategy(providerEntry.keySelectionStrategy())
                .usedApiKeyPrefix(selectedKey.apiKeyPrefix())
                .build();
    }

    private ProviderType resolveProviderType(String providerType, String modelName) {
        try {
            return ProviderType.from(providerType);
        } catch (IllegalArgumentException ex) {
            throw new GatewayException(ErrorCode.PROVIDER_NOT_FOUND,
                    "unsupported provider type for model " + modelName + ": " + providerType);
        }
    }
}
