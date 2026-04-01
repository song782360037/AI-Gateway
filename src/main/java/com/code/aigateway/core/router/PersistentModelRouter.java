package com.code.aigateway.core.router;

import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.runtime.RoutingSnapshotHolder;
import com.code.aigateway.provider.ProviderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于持久化快照的模型路由器
 *
 * <p>优先读取本地不可变快照完成路由，避免聊天热路径直接访问 MySQL / Redis。</p>
 * <p>当本地快照缺失或未命中时，自动降级到 YAML 路由器。</p>
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class PersistentModelRouter implements ModelRouter {

    /** 本地路由快照持有器 */
    private final RoutingSnapshotHolder routingSnapshotHolder;

    /** YAML 兜底路由器 */
    private final ConfigBasedModelRouter fallbackRouter;

    @Override
    public RouteResult route(UnifiedRequest request) {
        if (request.getModel() == null || request.getModel().isBlank()) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, "model is required");
        }

        RoutingConfigSnapshot snapshot = routingSnapshotHolder.get();
        if (snapshot == null) {
            log.warn("[持久化路由] 本地快照不存在，回退到 YAML 路由，model: {}", request.getModel());
            return fallbackRouter.route(request);
        }

        List<RouteCandidate> candidates = snapshot.getCandidates(request.getModel());
        if (candidates.isEmpty()) {
            log.info("[持久化路由] 快照未命中，回退到 YAML 路由，model: {}，快照版本: {}",
                    request.getModel(), snapshot.getVersion());
            return fallbackRouter.route(request);
        }

        RouteCandidate selected = candidates.get(0);
        ProviderType providerType = resolveProviderType(selected.getProviderType(), request.getModel());

        return RouteResult.builder()
                .providerType(providerType)
                .providerName(selected.getProviderCode())
                .targetModel(selected.getTargetModel())
                .providerBaseUrl(selected.getProviderBaseUrl())
                .providerVersion(selected.getProviderVersion())
                .providerTimeoutSeconds(selected.getProviderTimeoutSeconds())
                .providerApiKey(selected.getProviderApiKey())
                .build();
    }

    /**
     * 解析 provider 类型，并将非法枚举值转换为网关异常。
     */
    private ProviderType resolveProviderType(String providerType, String modelName) {
        try {
            return ProviderType.from(providerType);
        } catch (IllegalArgumentException ex) {
            throw new GatewayException(ErrorCode.PROVIDER_NOT_FOUND,
                    "unsupported provider type for model " + modelName + ": " + providerType);
        }
    }

    /**
     * 返回全部候选路由（用于故障转移）
     * <p>
     * 将快照中的所有候选转换为 RouteResult 列表，按优先级从高到低排序。
     * </p>
     */
    @Override
    public List<RouteResult> routeAll(UnifiedRequest request) {
        if (request.getModel() == null || request.getModel().isBlank()) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, "model is required");
        }

        RoutingConfigSnapshot snapshot = routingSnapshotHolder.get();
        if (snapshot == null) {
            return fallbackRouter.routeAll(request);
        }

        List<RouteCandidate> candidates = snapshot.getCandidates(request.getModel());
        if (candidates.isEmpty()) {
            return fallbackRouter.routeAll(request);
        }

        return candidates.stream()
                .map(c -> RouteResult.builder()
                        .providerType(resolveProviderType(c.getProviderType(), request.getModel()))
                        .providerName(c.getProviderCode())
                        .targetModel(c.getTargetModel())
                        .providerBaseUrl(c.getProviderBaseUrl())
                        .providerVersion(c.getProviderVersion())
                        .providerTimeoutSeconds(c.getProviderTimeoutSeconds())
                        .providerApiKey(c.getProviderApiKey())
                        .build())
                .toList();
    }
}
