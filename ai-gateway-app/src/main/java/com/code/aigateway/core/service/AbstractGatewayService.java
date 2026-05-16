package com.code.aigateway.core.service;

import com.code.aigateway.core.capability.CapabilityChecker;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.protocol.ProtocolAdapter;
import com.code.aigateway.core.resilience.FailoverStrategy;
import com.code.aigateway.core.router.ModelRouter;
import com.code.aigateway.core.router.RouteResult;
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
        unifiedRequest.setProvider(routeResult.getProviderType().name().toLowerCase());
        unifiedRequest.setModel(routeResult.getTargetModel());
        if (unifiedRequest.getMetadata() == null) {
            unifiedRequest.setMetadata(new HashMap<>());
        }
        unifiedRequest.getMetadata().put("statsContext", context);
        unifiedRequest.setExecutionContext(buildExecutionContext(routeResult, correlationId));
    }

    protected void applyFinalRouteContext(RequestStatsContext context, RouteResult routeResult) {
        context.setRouteResult(routeResult);
        context.setFinalProviderCode(routeResult.getProviderName());
        context.setFinalProviderType(routeResult.getProviderType().name());
        context.setFinalTargetModel(routeResult.getTargetModel());
        activeRequestTracker.updateRoute(
                context.getCorrelationId(),
                routeResult.getProviderName(),
                routeResult.getTargetModel()
        );
    }

    protected UnifiedRequest.ProviderExecutionContext buildExecutionContext(RouteResult routeResult, String correlationId) {
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
