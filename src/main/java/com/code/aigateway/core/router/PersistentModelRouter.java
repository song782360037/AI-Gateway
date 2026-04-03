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
 * <p>当快照和 YAML 都未命中路由规则时，走透传分支：按 Provider 优先级透传原始模型名。</p>
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
            try {
                return fallbackRouter.route(request);
            } catch (GatewayException ex) {
                // 仅当 YAML 也找不到模型别名时，才走透传分支
                if (ex.getErrorCode() == ErrorCode.MODEL_NOT_FOUND) {
                    return buildPassthroughCandidates(snapshot, request.getModel()).get(0);
                }
                throw ex;
            }
        }

        RouteCandidate selected = candidates.get(0);
        ProviderType providerType = resolveProviderType(selected.getProviderType(), request.getModel());

        return RouteResult.builder()
                .providerType(providerType)
                .providerName(selected.getProviderCode())
                .targetModel(selected.getTargetModel())
                .providerBaseUrl(selected.getProviderBaseUrl())
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
     * 当快照和 YAML 都未命中路由规则时，走透传分支。
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
            try {
                return fallbackRouter.routeAll(request);
            } catch (GatewayException ex) {
                // 仅当 YAML 也找不到模型别名时，才走透传分支
                if (ex.getErrorCode() == ErrorCode.MODEL_NOT_FOUND) {
                    return buildPassthroughCandidates(snapshot, request.getModel());
                }
                throw ex;
            }
        }

        return candidates.stream()
                .map(c -> RouteResult.builder()
                        .providerType(resolveProviderType(c.getProviderType(), request.getModel()))
                        .providerName(c.getProviderCode())
                        .targetModel(c.getTargetModel())
                        .providerBaseUrl(c.getProviderBaseUrl())
                        .providerTimeoutSeconds(c.getProviderTimeoutSeconds())
                        .providerApiKey(c.getProviderApiKey())
                        .build())
                .toList();
    }

    /**
     * 构建透传候选列表：使用快照中全部已启用的 Provider，targetModel 保持原始模型名。
     * <p>
     * 当请求模型在快照和 YAML 中均未命中路由规则时调用。
     * 候选按 Provider 优先级降序排列，复用现有故障转移与熔断机制。
     * </p>
     *
     * @param snapshot    当前路由配置快照
     * @param originalModel 原始请求模型名
     * @return 透传候选列表，按 Provider 优先级排序
     */
    private List<RouteResult> buildPassthroughCandidates(RoutingConfigSnapshot snapshot, String originalModel) {
        List<RouteResult> passthroughCandidates = snapshot.getAllProvidersByPriority().stream()
                .map(entry -> RouteResult.builder()
                        .providerType(resolveProviderType(entry.providerType(), originalModel))
                        .providerName(entry.providerCode())
                        .targetModel(originalModel)
                        .providerBaseUrl(entry.baseUrl())
                        .providerApiKey(entry.apiKey())
                        .providerTimeoutSeconds(entry.timeoutSeconds())
                        .build())
                .toList();

        log.info("[持久化路由] 无路由规则，走透传分支，model: {}，候选数: {}",
                originalModel, passthroughCandidates.size());
        return passthroughCandidates;
    }
}
