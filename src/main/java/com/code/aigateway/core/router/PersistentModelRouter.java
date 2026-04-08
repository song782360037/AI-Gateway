package com.code.aigateway.core.router;

import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.model.ResponseProtocol;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.runtime.RoutingSnapshotHolder;
import com.code.aigateway.provider.ProviderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collections;
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

        // 3. 快照未命中，回退到 YAML 路由
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
     */
    private RouteResult selectCandidate(List<RouteCandidate> candidates, UnifiedRequest request) {
        String requestProtocol = request.getRequestProtocol();
        RouteCandidate selected = candidates.stream()
                .filter(c -> isProtocolSupported(c, requestProtocol))
                .findFirst()
                .orElse(null);

        if (selected == null) {
            throw new GatewayException(ErrorCode.PROVIDER_NOT_FOUND,
                    "no providers support protocol " + requestProtocol + " for model " + request.getModel());
        }

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
     * 从候选列表中按协议过滤，构建完整的 RouteResult 列表（用于故障转移）。
     */
    private List<RouteResult> buildRouteResults(List<RouteCandidate> candidates, UnifiedRequest request) {
        String requestProtocol = request.getRequestProtocol();
        List<RouteResult> filtered = candidates.stream()
                .filter(c -> isProtocolSupported(c, requestProtocol))
                .map(c -> RouteResult.builder()
                        .providerType(resolveProviderType(c.getProviderType(), request.getModel()))
                        .providerName(c.getProviderCode())
                        .targetModel(c.getTargetModel())
                        .providerBaseUrl(c.getProviderBaseUrl())
                        .providerTimeoutSeconds(c.getProviderTimeoutSeconds())
                        .providerApiKey(c.getProviderApiKey())
                        .build())
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

        // 3. 回退到 YAML 路由
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

    /**
     * 判断候选路由是否支持当前请求协议。
     * <p>supportedProtocols 为空时表示支持所有协议。</p>
     */
    private boolean isProtocolSupported(RouteCandidate candidate, String requestProtocol) {
        if (requestProtocol == null) {
            return true;
        }
        List<String> supported = candidate.getSupportedProtocols();
        if (supported == null || supported.isEmpty()) {
            return true;
        }
        // 归一化后比较：兼容 "openai-chat" vs "OPENAI_CHAT" 等格式差异
        String normalized = ResponseProtocol.normalize(requestProtocol);
        return supported.stream()
                .anyMatch(s -> ResponseProtocol.normalize(s).equals(normalized));
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
        String normalized = ResponseProtocol.normalize(requestProtocol);
        return supported.stream()
                .anyMatch(s -> ResponseProtocol.normalize(s).equals(normalized));
    }
}
