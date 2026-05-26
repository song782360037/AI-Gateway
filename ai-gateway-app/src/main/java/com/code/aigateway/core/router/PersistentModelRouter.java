package com.code.aigateway.core.router;

import com.code.aigateway.common.util.CustomHeaderUtils;
import com.code.aigateway.sdk.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.sdk.model.ProtocolType;
import com.code.aigateway.sdk.model.UnifiedRequest;
import com.code.aigateway.core.runtime.RoutingSnapshotHolder;
import com.code.aigateway.provider.ProviderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    /** Auto 智能路由选择器 */
    private final AutoRouteSelector autoRouteSelector;

    /** API Key 选择策略组件 */
    private final ProviderKeySelector providerKeySelector;

    @Override
    public RouteResult route(UnifiedRequest request) {
        if (request.getModel() == null || request.getModel().isBlank()) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, "model is required");
        }

        RoutingConfigSnapshot snapshot = routingSnapshotHolder.get();
        if (snapshot == null) {
            if (autoRouteSelector.isAutoModel(request.getModel())) {
                throw new GatewayException(ErrorCode.MODEL_NOT_FOUND, "auto route snapshot not initialized");
            }
            log.warn("[持久化路由] 本地快照不存在，回退到 YAML 路由，model: {}", request.getModel());
            return fallbackRouter.route(request);
        }

        // 1. 精确匹配（O(1) HashMap 查找）
        List<RouteCandidate> candidates = snapshot.getCandidates(request.getModel());
        if (!candidates.isEmpty()) {
            return selectCandidate(candidates, request);
        }

        // 2. 模式匹配（GLOB 优先于 REGEX，遍历预编译列表）
        candidates = matchPatternRoutes(snapshot.getPatternRoutes(), request.getModel());
        if (!candidates.isEmpty()) {
            return selectCandidate(candidates, request);
        }

        // 3. Auto 智能路由必须在 YAML fallback 和透传前处理
        if (autoRouteSelector.isAutoModel(request.getModel())) {
            return autoRouteSelector.select(snapshot, request);
        }

        // 4. 快照未命中，回退到 YAML 路由
        log.info("[持久化路由] 快照未命中，回退到 YAML 路由，model: {}，快照版本: {}",
                request.getModel(), snapshot.getVersion());
        try {
            return fallbackRouter.route(request);
        } catch (GatewayException ex) {
            // 仅当 YAML 也找不到模型别名时，才走透传分支
            if (ex.getErrorCode() == ErrorCode.MODEL_NOT_FOUND) {
                List<RouteResult> passthrough = buildPassthroughCandidates(snapshot, request.getModel(), request.getRequestProtocol());
                if (passthrough.isEmpty()) {
                    throw new GatewayException(ErrorCode.PROVIDER_NOT_FOUND,
                            "no providers support protocol " + request.getRequestProtocol() + " for model " + request.getModel());
                }
                return passthrough.get(0);
            }
            throw ex;
        }
    }

    /**
     * 从模式匹配规则列表中查找命中的候选路由。
     *
     * <p>按 GLOB 优先于 REGEX 的顺序遍历，找到第一个匹配的规则即返回其候选列表。
     * 同一 aliasName 的所有候选已在快照构建时按 provider 优先级排序。</p>
     */
    private List<RouteCandidate> matchPatternRoutes(List<RoutingConfigSnapshot.PatternRoute> patternRoutes,
                                                     String modelName) {
        List<RouteCandidate> regexMatches = null;

        for (RoutingConfigSnapshot.PatternRoute pr : patternRoutes) {
            if (pr.compiledPattern().matcher(modelName).matches()) {
                // GLOB 优先，找到 GLOB 匹配立即返回
                if (pr.matchType() == MatchType.GLOB) {
                    log.debug("[持久化路由] GLOB 匹配命中，pattern: {}，model: {}", pr.originalPattern(), modelName);
                    return pr.candidates();
                }
                // REGEX 匹配先暂存，确认没有 GLOB 匹配后再使用
                if (regexMatches == null) {
                    regexMatches = pr.candidates();
                    log.debug("[持久化路由] REGEX 匹配命中，pattern: {}，model: {}", pr.originalPattern(), modelName);
                }
            }
        }

        // 没有 GLOB 匹配才用 REGEX
        return regexMatches != null ? regexMatches : Collections.emptyList();
    }

    /**
     * 从候选列表中按协议过滤并选出首选候选，构建 RouteResult。
     * Key 选择在此处完成：从 ProviderEntry 中获取可用 Key 列表，按策略选中一个。
     * 当候选的 Provider 无可用 Key 时，自动尝试下一个候选。
     */
    private RouteResult selectCandidate(List<RouteCandidate> candidates, UnifiedRequest request) {
        String requestProtocol = request.getRequestProtocol();
        RoutingConfigSnapshot snapshot = routingSnapshotHolder.get();

        for (RouteCandidate c : candidates) {
            if (!c.supportsProtocol(requestProtocol)) {
                continue;
            }
            RoutingConfigSnapshot.ProviderEntry providerEntry = snapshot.getProviderMap().get(c.getProviderCode());
            ProviderKeyEntry selectedKey = selectKey(providerEntry);
            if (selectedKey == null) {
                log.warn("[持久化路由] Provider {} 无可用 Key，尝试下一个候选", c.getProviderCode());
                continue;
            }
            return RouteResult.builder()
                    .providerType(resolveProviderType(c.getProviderType(), request.getModel()))
                    .providerName(c.getProviderCode())
                    .targetModel(c.getTargetModel())
                    .providerBaseUrl(c.getProviderBaseUrl())
                    .providerTimeoutSeconds(c.getProviderTimeoutSeconds())
                    .providerApiKey(selectedKey.apiKey())
                    .customHeaders(c.getCustomHeaders())
                    .thinkingCompatMode(c.getThinkingCompatMode())
                    .providerKeyEntries(providerEntry.apiKeys())
                    .keySelectionStrategy(providerEntry.keySelectionStrategy())
                    .usedApiKeyPrefix(selectedKey.apiKeyPrefix())
                    .providerKeyId(selectedKey.id())
                    .build();
        }

        throw new GatewayException(ErrorCode.PROVIDER_NOT_FOUND,
                "no providers with available keys support protocol " + requestProtocol + " for model " + request.getModel());
    }

    /**
     * 从候选列表中按协议过滤，构建完整的 RouteResult 列表（用于故障转移）。
     */
    private List<RouteResult> buildRouteResults(List<RouteCandidate> candidates, UnifiedRequest request) {
        String requestProtocol = request.getRequestProtocol();
        RoutingConfigSnapshot snapshot = routingSnapshotHolder.get();

        List<RouteResult> filtered = candidates.stream()
                .filter(c -> c.supportsProtocol(requestProtocol))
                .map(c -> {
                    RoutingConfigSnapshot.ProviderEntry providerEntry = snapshot.getProviderMap().get(c.getProviderCode());
                    ProviderKeyEntry selectedKey = selectKey(providerEntry);
                    if (selectedKey == null) {
                        log.warn("[持久化路由] Provider {} 无可用 Key，从故障转移列表中跳过",
                                c.getProviderCode());
                        return null;
                    }
                    return RouteResult.builder()
                            .providerType(resolveProviderType(c.getProviderType(), request.getModel()))
                            .providerName(c.getProviderCode())
                            .targetModel(c.getTargetModel())
                            .providerBaseUrl(c.getProviderBaseUrl())
                            .providerTimeoutSeconds(c.getProviderTimeoutSeconds())
                            .providerApiKey(selectedKey.apiKey())
                            .customHeaders(c.getCustomHeaders())
                            .thinkingCompatMode(c.getThinkingCompatMode())
                            .providerKeyEntries(providerEntry.apiKeys())
                            .keySelectionStrategy(providerEntry.keySelectionStrategy())
                            .usedApiKeyPrefix(selectedKey.apiKeyPrefix())
                            .providerKeyId(selectedKey.id())
                            .build();
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        if (filtered.isEmpty()) {
            throw new GatewayException(ErrorCode.PROVIDER_NOT_FOUND,
                    "no providers support protocol " + requestProtocol + " for model " + request.getModel());
        }
        return filtered;
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
            if (autoRouteSelector.isAutoModel(request.getModel())) {
                throw new GatewayException(ErrorCode.MODEL_NOT_FOUND, "auto route snapshot not initialized");
            }
            return fallbackRouter.routeAll(request);
        }

        // 1. 精确匹配
        List<RouteCandidate> candidates = snapshot.getCandidates(request.getModel());
        if (!candidates.isEmpty()) {
            return buildRouteResults(candidates, request);
        }

        // 2. 模式匹配
        candidates = matchPatternRoutes(snapshot.getPatternRoutes(), request.getModel());
        if (!candidates.isEmpty()) {
            return buildRouteResults(candidates, request);
        }

        // 3. Auto 智能路由必须在 YAML fallback 和透传前处理
        if (autoRouteSelector.isAutoModel(request.getModel())) {
            return autoRouteSelector.selectAll(snapshot, request);
        }

        // 4. 回退到 YAML 路由
        try {
            return fallbackRouter.routeAll(request);
        } catch (GatewayException ex) {
            // 仅当 YAML 也找不到模型别名时，才走透传分支
            if (ex.getErrorCode() == ErrorCode.MODEL_NOT_FOUND) {
                return buildPassthroughCandidates(snapshot, request.getModel(), request.getRequestProtocol());
            }
            throw ex;
        }
    }

    /**
     * 构建透传候选列表：使用快照中全部已启用且支持当前请求协议的 Provider，
     * targetModel 保持原始模型名。
     */
    private List<RouteResult> buildPassthroughCandidates(RoutingConfigSnapshot snapshot,
                                                          String originalModel,
                                                          String requestProtocol) {
        List<RouteResult> passthroughCandidates = snapshot.getAllProvidersByPriority().stream()
                .filter(entry -> isProviderProtocolSupported(entry, requestProtocol))
                .map(entry -> {
                    ProviderKeyEntry selectedKey = selectKey(entry);
                    if (selectedKey == null) {
                        return null;
                    }
                    return RouteResult.builder()
                            .providerType(resolveProviderType(entry.providerType(), originalModel))
                            .providerName(entry.providerCode())
                            .targetModel(originalModel)
                            .providerBaseUrl(entry.baseUrl())
                            .providerApiKey(selectedKey.apiKey())
                            .providerTimeoutSeconds(entry.timeoutSeconds())
                            .customHeaders(CustomHeaderUtils.mergeCustomHeaders(snapshot.getGlobalCustomHeaders(), entry.customHeaders(), "透传路由"))
                            .thinkingCompatMode(entry.thinkingCompatMode())
                            .providerKeyEntries(entry.apiKeys())
                            .keySelectionStrategy(entry.keySelectionStrategy())
                            .usedApiKeyPrefix(selectedKey.apiKeyPrefix())
                            .providerKeyId(selectedKey.id())
                            .build();
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        log.info("[持久化路由] 无路由规则，走透传分支，model: {}，候选数: {}",
                originalModel, passthroughCandidates.size());
        return passthroughCandidates;
    }

    /**
     * 判断 ProviderEntry 是否支持当前请求协议。
     */
    private boolean isProviderProtocolSupported(RoutingConfigSnapshot.ProviderEntry entry, String requestProtocol) {
        if (requestProtocol == null) {
            return true;
        }
        List<String> supported = entry.supportedProtocols();
        if (supported == null || supported.isEmpty()) {
            return true;
        }
        String normalized = ProtocolType.normalize(requestProtocol);
        return supported.stream()
                .anyMatch(s -> ProtocolType.normalize(s).equals(normalized));
    }

    /**
     * 从 ProviderEntry 中按策略选择一个 API Key。
     */
    private ProviderKeyEntry selectKey(RoutingConfigSnapshot.ProviderEntry entry) {
        if (entry == null || entry.apiKeys() == null || entry.apiKeys().isEmpty()) {
            log.warn("[持久化路由] Provider {} 无可用 API Key", entry != null ? entry.providerCode() : "null");
            return null;
        }
        return providerKeySelector.select(entry.providerCode(), entry.apiKeys(), entry.keySelectionStrategy());
    }
}
