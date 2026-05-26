package com.code.aigateway.core.service;

import com.code.aigateway.core.GatewayMetadataKeys;
import com.code.aigateway.core.capability.CapabilityChecker;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.protocol.ProtocolAdapter;
import com.code.aigateway.core.resilience.FailoverStrategy;
import com.code.aigateway.core.router.ModelRouter;
import com.code.aigateway.core.router.RouteResult;
import com.code.aigateway.core.stats.TraceDetails;
import com.code.aigateway.core.stats.ActiveRequestTracker;
import com.code.aigateway.core.stats.RequestStatsCollector;
import com.code.aigateway.core.stats.RequestStatsContext;
import com.code.aigateway.provider.ProviderClient;
import com.code.aigateway.provider.ProviderClientFactory;
import com.code.aigateway.sdk.error.ErrorCode;
import com.code.aigateway.sdk.model.UnifiedRequest;
import com.code.aigateway.sdk.model.UnifiedResponse;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.function.BiFunction;

/**
 * 网关服务公共基类，提取路由、Failover、统计等非流式编排逻辑。
 */
public abstract class AbstractGatewayService {

    protected final ModelRouter modelRouter;
    protected final CapabilityChecker capabilityChecker;
    protected final ProviderClientFactory providerClientFactory;
    protected final RequestStatsCollector requestStatsCollector;
    protected final FailoverStrategy failoverStrategy;
    protected final ActiveRequestTracker activeRequestTracker;

    protected AbstractGatewayService(ModelRouter modelRouter, CapabilityChecker capabilityChecker, ProviderClientFactory providerClientFactory, RequestStatsCollector requestStatsCollector, FailoverStrategy failoverStrategy, ActiveRequestTracker activeRequestTracker) {
        this.modelRouter = modelRouter;
        this.capabilityChecker = capabilityChecker;
        this.providerClientFactory = providerClientFactory;
        this.requestStatsCollector = requestStatsCollector;
        this.failoverStrategy = failoverStrategy;
        this.activeRequestTracker = activeRequestTracker;
    }

    /**
     * 非流式请求编排模板：解析 → 预处理 → 路由 → Failover → Provider 调用 → 统计 → 响应编码。
     *
     * @param rawRequest   原始协议请求对象
     * @param adapter      协议适配器
     * @param context      统计上下文（可 null）
     * @param providerCall Provider 调用函数，如 ProviderClient::embedding
     */
    protected Mono<?> executeNonStreaming(Object rawRequest, ProtocolAdapter adapter,
                                          RequestStatsContext context,
                                          BiFunction<ProviderClient, UnifiedRequest, Mono<UnifiedResponse>> providerCall) {
        UnifiedRequest unifiedRequest = adapter.parse(rawRequest);
        onPreRoute(unifiedRequest, context);
        applyActiveRequestInfo(unifiedRequest, context);
        List<RouteResult> candidates = resolveCandidates(unifiedRequest, context);
        String correlationId = context != null ? context.getCorrelationId() : null;

        return failoverStrategy.executeWithFailover(candidates, routeResult -> {
            applyRouteContext(unifiedRequest, routeResult, correlationId, context);
            if (context != null) {
                applyFinalRouteContext(context, routeResult);
            }
            ProviderClient client = providerClientFactory.getClient(routeResult.getProviderType());
            return providerCall.apply(client, unifiedRequest);
        }, correlationId, context)
                .doOnNext(response -> requestStatsCollector.collectSuccess(context, response.getUsage()))
                .doOnError(ex -> requestStatsCollector.collectError(context, ex))
                .map(adapter::encodeResponse);
    }

    /**
     * 路由前预处理钩子，子类可覆写以插入自定义逻辑（如 ChatGatewayService 的 thinking 配置提取）。
     */
    protected void onPreRoute(UnifiedRequest request, RequestStatsContext context) {
        // 默认无操作
    }

    protected void applyActiveRequestInfo(UnifiedRequest unifiedRequest, RequestStatsContext context) {
        if (context == null || unifiedRequest == null) {
            return;
        }
        activeRequestTracker.updateRequestInfo(
                context.getCorrelationId(),
                unifiedRequest.getModel(),
                unifiedRequest.getStream()
        );
    }

    protected List<RouteResult> resolveCandidates(UnifiedRequest unifiedRequest, RequestStatsContext context) {
        List<RouteResult> candidates = modelRouter.routeAll(unifiedRequest);
        if (context != null) {
            context.setCandidateCount(candidates.size());
            context.setTerminalStage("ROUTING");
        }
        if (candidates.isEmpty()) {
            throw new GatewayException(ErrorCode.MODEL_NOT_FOUND,
                    "No available provider for model: " + unifiedRequest.getModel());
        }
        capabilityChecker.validate(unifiedRequest, candidates.get(0));
        if (context != null) {
            context.setRouteResult(candidates.get(0));
            applyFinalRouteContext(context, candidates.get(0));
        }
        return candidates;
    }

    protected void applyRouteContext(UnifiedRequest unifiedRequest, RouteResult routeResult,
                                     String correlationId, RequestStatsContext context) {
        applyBasicRouteContext(unifiedRequest, routeResult, correlationId);
        // 传递多 Key 信息到 metadata，供 ProviderClient Key 降级重试使用
        unifiedRequest.getMetadata().put(GatewayMetadataKeys.PROVIDER_KEY_ENTRIES, routeResult.getProviderKeyEntries());
        unifiedRequest.getMetadata().put(GatewayMetadataKeys.KEY_SELECTION_STRATEGY, routeResult.getKeySelectionStrategy());
        unifiedRequest.getMetadata().put(GatewayMetadataKeys.USED_API_KEY_PREFIX, routeResult.getUsedApiKeyPrefix());
        unifiedRequest.getMetadata().put(GatewayMetadataKeys.THINKING_COMPAT_MODE, routeResult.getThinkingCompatMode());
        unifiedRequest.getMetadata().put(GatewayMetadataKeys.STATS_CONTEXT, context);
    }

    /**
     * 注入基础路由上下文（provider、model、executionContext）。
     * <p>CountTokensService 等非 Chat 服务也可复用，无需依赖 RequestStatsContext。</p>
     */
    public static void applyBasicRouteContext(UnifiedRequest req, RouteResult routeResult,
                                              String correlationId) {
        req.setProvider(routeResult.getProviderType().name().toLowerCase());
        req.setModel(routeResult.getTargetModel());
        if (req.getMetadata() == null) {
            req.setMetadata(new HashMap<>());
        }
        req.setExecutionContext(buildExecutionContext(routeResult, correlationId));
    }

    protected void applyFinalRouteContext(RequestStatsContext context, RouteResult routeResult) {
        context.setRouteResult(routeResult);
        context.setFinalProviderCode(routeResult.getProviderName());
        context.setFinalProviderType(routeResult.getProviderType().name());
        context.setFinalTargetModel(routeResult.getTargetModel());
        context.setProviderApiKeyMasked(routeResult.getUsedApiKeyPrefix());
        context.setProviderKeyId(routeResult.getProviderKeyId());
        // 记录 Key 选择策略和原因到 trace details
        TraceDetails details = getOrCreateTraceDetails(context);
        details.setKeySelectionStrategy(routeResult.getKeySelectionStrategy() != null
                ? routeResult.getKeySelectionStrategy().name() : null);
        details.setKeySelectionReason(buildKeySelectionReason(routeResult));
        activeRequestTracker.updateRoute(
                context.getCorrelationId(),
                routeResult.getProviderName(),
                routeResult.getTargetModel()
        );
    }

    /**
     * 获取或创建 trace details 对象，同时同步到 JSON 字段
     */
    private TraceDetails getOrCreateTraceDetails(RequestStatsContext context) {
        TraceDetails details = context.getTraceDetails();
        if (details == null) {
            details = new TraceDetails();
            context.setTraceDetails(details);
        }
        return details;
    }

    /**
     * 构建 Key 选择原因说明（英文，避免 DB 编码和国际化问题）
     */
    private String buildKeySelectionReason(RouteResult routeResult) {
        if (routeResult.getKeySelectionStrategy() == null || routeResult.getUsedApiKeyPrefix() == null) {
            return null;
        }
        return switch (routeResult.getKeySelectionStrategy()) {
            case ROUND_ROBIN -> "Round-robin selection";
            case RANDOM -> "Weighted random selection";
            case FALLBACK -> "Fallback by sort order";
        };
    }

    protected static UnifiedRequest.ProviderExecutionContext buildExecutionContext(RouteResult routeResult, String correlationId) {
        UnifiedRequest.ProviderExecutionContext ctx = new UnifiedRequest.ProviderExecutionContext();
        ctx.setProviderName(routeResult.getProviderName());
        ctx.setProviderBaseUrl(routeResult.getProviderBaseUrl());
        ctx.setProviderTimeoutSeconds(routeResult.getProviderTimeoutSeconds());
        ctx.setProviderApiKey(routeResult.getProviderApiKey());
        ctx.setCorrelationId(correlationId);
        ctx.setCustomHeaders(routeResult.getCustomHeaders());
        return ctx;
    }
}
