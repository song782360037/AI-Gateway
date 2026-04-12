package com.code.aigateway.core.service;

import com.code.aigateway.core.capability.CapabilityChecker;
import com.code.aigateway.core.model.StreamContext;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedStreamEvent;
import com.code.aigateway.core.model.UnifiedUsage;
import com.code.aigateway.core.protocol.ProtocolAdapter;
import com.code.aigateway.core.resilience.FailoverStrategy;
import com.code.aigateway.core.router.ModelRouter;
import com.code.aigateway.core.router.RouteResult;
import com.code.aigateway.core.stats.RequestStatsCollector;
import com.code.aigateway.core.stats.RequestStatsContext;
import com.code.aigateway.provider.ProviderClient;
import com.code.aigateway.provider.ProviderClientFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 聊天网关核心服务
 * <p>
 * 协议无关的编排服务，负责：
 * <ul>
 *   <li>请求解析：委托 ProtocolAdapter 将原始请求转换为统一格式</li>
 *   <li>模型路由：根据模型别名获取全部候选路由</li>
 *   <li>能力检查：验证请求与目标模型的能力兼容性</li>
 *   <li>故障转移：通过 FailoverStrategy 依次尝试候选 Provider</li>
 *   <li>响应编码：委托 ProtocolAdapter 编码为协议特定格式</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
public class ChatGatewayService {

    /** 模型路由器 */
    private final ModelRouter modelRouter;

    /** 能力检查器 */
    private final CapabilityChecker capabilityChecker;

    /** 提供商客户端工厂 */
    private final ProviderClientFactory providerClientFactory;

    /** 请求统计采集器 */
    private final RequestStatsCollector requestStatsCollector;

    /** 故障转移策略 */
    private final FailoverStrategy failoverStrategy;

    /**
     * 处理非流式聊天请求（含 usage 统计 + 故障转移）
     */
    public Mono<?> chatWithStats(Object rawRequest, ProtocolAdapter adapter, RequestStatsContext context) {
        UnifiedRequest unifiedRequest = adapter.parse(rawRequest);
        List<RouteResult> candidates = resolveCandidates(unifiedRequest, context);
        String correlationId = context != null ? context.getCorrelationId() : null;

        return failoverStrategy.executeWithFailover(candidates, routeResult -> {
            applyRouteContext(unifiedRequest, routeResult, correlationId, context);
            if (context != null) {
                applyFinalRouteContext(context, routeResult);
            }
            ProviderClient client = providerClientFactory.getClient(routeResult.getProviderType());
            return client.chat(unifiedRequest);
        }, correlationId, context)
                .doOnNext(response -> requestStatsCollector.collectSuccess(context, response.getUsage()))
                .doOnError(ex -> requestStatsCollector.collectError(context, ex))
                .map(adapter::encodeResponse);
    }

    /**
     * 处理流式聊天请求（含 usage 统计 + 故障转移）
     */
    public Flux<?> streamChat(Object rawRequest, ProtocolAdapter adapter, RequestStatsContext context) {
        UnifiedRequest unifiedRequest = adapter.parse(rawRequest);
        List<RouteResult> candidates = resolveCandidates(unifiedRequest, context);
        String correlationId = context != null ? context.getCorrelationId() : null;

        String responseId = "chatcmpl-" + UUID.randomUUID();
        long created = Instant.now().getEpochSecond();
        AtomicReference<UnifiedUsage> finalUsageRef = new AtomicReference<>();
        AtomicBoolean terminalEventSeen = new AtomicBoolean(false);

        // StreamContext 必须在流链外创建，确保 firstContentSent 标志跨事件共享
        String streamModel = context != null && context.getRouteResult() != null
                ? context.getRouteResult().getTargetModel() : "";
        StreamContext streamCtx = new StreamContext(responseId, created, streamModel);

        AtomicReference<Throwable> streamErrorRef = new AtomicReference<>();

        return Flux.concat(
                    // 流起始事件（如 Anthropic 的 message_start）
                    adapter.initialStreamEvents(streamCtx),
                    // 主事件流：每个 UnifiedStreamEvent 可能产生 0~N 个 SSE 事件
                    failoverStrategy.executeStreamWithFailover(candidates, routeResult -> {
                        applyRouteContext(unifiedRequest, routeResult, correlationId, context);
                        if (context != null) {
                            applyFinalRouteContext(context, routeResult);
                        }
                        // failover 后同步更新 StreamContext 中的实际模型名
                        streamCtx.setModel(routeResult.getTargetModel());
                        ProviderClient client = providerClientFactory.getClient(routeResult.getProviderType());
                        return client.streamChat(unifiedRequest);
                    }, correlationId, context)
                            .doOnNext(event -> {
                                // 记录 provider 已输出终止事件，避免正常结束后因连接关闭被误判为取消。
                                if (isTerminalStreamEvent(event)) {
                                    terminalEventSeen.set(true);
                                }
                                // "done" 事件携带 finish_reason 和可能的 usage。
                                // "usage_only" 事件来自 OpenAI 的 usage-only chunk（choices:[]）。
                                if ((isTerminalStreamEvent(event) || "usage_only".equals(event.getType()))
                                        && event.getUsage() != null) {
                                    finalUsageRef.set(event.getUsage());
                                }
                            })
                            .concatMap(event -> adapter.encodeStreamEvent(event, streamCtx)),
                    // 流终止事件（如 OpenAI 的 [DONE]、Anthropic 的 message_stop）
                    adapter.terminalStreamEvents(streamCtx)
                )
                .doOnError(ex -> requestStatsCollector.collectError(context, ex))
                .onErrorResume(ex -> {
                    streamErrorRef.set(ex);
                    return adapter.encodeStreamError(ex, streamCtx);
                })
                .doOnComplete(() -> {
                    if (streamErrorRef.get() == null) {
                        requestStatsCollector.collectStreamSuccess(context, finalUsageRef.get());
                    }
                })
                .doOnCancel(() -> {
                    if (streamErrorRef.get() == null && !terminalEventSeen.get()) {
                        requestStatsCollector.collectStreamCancelled(context, finalUsageRef.get());
                    }
                });
    }

    /**
     * 解析请求并获取全部候选路由，同时校验能力
     */
    private List<RouteResult> resolveCandidates(UnifiedRequest unifiedRequest, RequestStatsContext context) {
        List<RouteResult> candidates = modelRouter.routeAll(unifiedRequest);
        if (context != null) {
            context.setCandidateCount(candidates.size());
            context.setTerminalStage("ROUTING");
        }

        // 对首选候选做能力校验
        if (!candidates.isEmpty()) {
            capabilityChecker.validate(unifiedRequest, candidates.get(0));
            if (context != null) {
                context.setRouteResult(candidates.get(0));
                applyFinalRouteContext(context, candidates.get(0));
            }
        }

        return candidates;
    }

    /**
     * 将路由信息写入统一请求的执行上下文
     */
    private void applyRouteContext(UnifiedRequest unifiedRequest, RouteResult routeResult, String correlationId, RequestStatsContext context) {
        unifiedRequest.setProvider(routeResult.getProviderType().name().toLowerCase());
        unifiedRequest.setModel(routeResult.getTargetModel());
        unifiedRequest.setStatsContext(context);
        unifiedRequest.setExecutionContext(buildExecutionContext(routeResult, correlationId));
    }

    /**
     * 将最终命中的路由摘要写入统计上下文，供日志与详情页复用。
     */
    private void applyFinalRouteContext(RequestStatsContext context, RouteResult routeResult) {
        context.setRouteResult(routeResult);
        context.setFinalProviderCode(routeResult.getProviderName());
        context.setFinalProviderType(routeResult.getProviderType().name());
        context.setFinalTargetModel(routeResult.getTargetModel());
    }

    /**
     * 判断是否为 provider 的正常终止事件。
     */
    private boolean isTerminalStreamEvent(UnifiedStreamEvent event) {
        return "done".equals(event.getType());
    }

    private UnifiedRequest.ProviderExecutionContext buildExecutionContext(RouteResult routeResult, String correlationId) {
        UnifiedRequest.ProviderExecutionContext ctx = new UnifiedRequest.ProviderExecutionContext();
        ctx.setProviderName(routeResult.getProviderName());
        ctx.setProviderBaseUrl(routeResult.getProviderBaseUrl());
        ctx.setProviderTimeoutSeconds(routeResult.getProviderTimeoutSeconds());
        ctx.setProviderApiKey(routeResult.getProviderApiKey());
        ctx.setCorrelationId(correlationId);
        return ctx;
    }
}
