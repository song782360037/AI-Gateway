package com.code.aigateway.core.service;

import com.code.aigateway.core.capability.CapabilityChecker;
import com.code.aigateway.core.model.StreamContext;
import com.code.aigateway.sdk.model.UnifiedReasoningConfig;
import com.code.aigateway.sdk.model.UnifiedRequest;
import com.code.aigateway.sdk.model.UnifiedStreamEvent;
import com.code.aigateway.sdk.model.UnifiedUsage;
import com.code.aigateway.core.protocol.ProtocolAdapter;
import com.code.aigateway.core.resilience.FailoverStrategy;
import com.code.aigateway.core.router.ModelRouter;
import com.code.aigateway.core.router.RouteResult;
import com.code.aigateway.core.stats.ActiveRequestTracker;
import com.code.aigateway.core.stats.RequestStatsCollector;
import com.code.aigateway.core.stats.RequestStatsContext;
import com.code.aigateway.provider.AbstractProviderClient;
import com.code.aigateway.provider.ProviderClient;
import com.code.aigateway.provider.ProviderClientFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class ChatGatewayService extends AbstractGatewayService {

    private final ObjectMapper objectMapper;

    public ChatGatewayService(ModelRouter modelRouter, CapabilityChecker capabilityChecker,
                               ProviderClientFactory providerClientFactory,
                               RequestStatsCollector requestStatsCollector,
                               FailoverStrategy failoverStrategy,
                               ActiveRequestTracker activeRequestTracker,
                               ObjectMapper objectMapper) {
        super(modelRouter, capabilityChecker, providerClientFactory,
              requestStatsCollector, failoverStrategy, activeRequestTracker);
        this.objectMapper = objectMapper;
    }

    /**
     * 处理非流式聊天请求（含 usage 统计 + 故障转移）
     */
    public Mono<?> chatWithStats(Object rawRequest, ProtocolAdapter adapter, RequestStatsContext context) {
        return executeNonStreaming(rawRequest, adapter, context, ProviderClient::chat);
    }

    /**
     * 处理流式聊天请求（含 usage 统计 + 故障转移）
     */
    public Flux<?> streamChat(Object rawRequest, ProtocolAdapter adapter, RequestStatsContext context) {
        UnifiedRequest unifiedRequest = adapter.parse(rawRequest);
        onPreRoute(unifiedRequest, context);
        applyActiveRequestInfo(unifiedRequest, context);
        List<RouteResult> candidates = resolveCandidates(unifiedRequest, context);
        String correlationId = context != null ? context.getCorrelationId() : null;

        String responseId = "chatcmpl-" + UUID.randomUUID();
        long created = Instant.now().getEpochSecond();
        AtomicReference<UnifiedUsage> finalUsageRef = new AtomicReference<>();
        AtomicBoolean terminalEventSeen = new AtomicBoolean(false);

        // StreamContext 必须在流链外创建，确保首 token 延迟统计标志跨事件共享
        String streamModel = context != null && context.getRouteResult() != null
                ? context.getRouteResult().getTargetModel() : "";
        StreamContext streamCtx = new StreamContext(responseId, created, streamModel, objectMapper);

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
                    return Flux.concat(
                            adapter.encodeStreamError(ex, streamCtx),
                            adapter.terminalStreamEvents(streamCtx)
                    );
                })
                .doOnComplete(() -> {
                // 记录首token响应时间（仅流式请求支持，非流式不经过 StreamContext）
                if (context != null && streamCtx.getFirstTokenLatencyMs() >= 0) {
                    context.setFirstTokenLatencyMs(streamCtx.getFirstTokenLatencyMs());
                }
                    if (streamErrorRef.get() == null) {
                        requestStatsCollector.collectStreamSuccess(context, finalUsageRef.get());
                    }
                })
                .doOnCancel(() -> {
                    // 记录首token响应时间（仅流式请求支持）
                    if (context != null && streamCtx.getFirstTokenLatencyMs() >= 0) {
                        context.setFirstTokenLatencyMs(streamCtx.getFirstTokenLatencyMs());
                    }
                    if (streamErrorRef.get() == null && !terminalEventSeen.get()) {
                        requestStatsCollector.collectStreamCancelled(context, finalUsageRef.get());
                    }
                });
    }

    // ==================== Chat 特有逻辑 ====================

    @Override
    protected void onPreRoute(UnifiedRequest unifiedRequest, RequestStatsContext context) {
        applyThinkingConfig(unifiedRequest, context);
    }

    @Override
    protected void applyRouteContext(UnifiedRequest unifiedRequest, RouteResult routeResult,
                                     String correlationId, RequestStatsContext context) {
        super.applyRouteContext(unifiedRequest, routeResult, correlationId, context);
        // 将 thinking 兼容模式写入 metadata，供 Provider Client 读取
        // 不写入 SDK 的 ProviderExecutionContext，保持 SDK 官方语义
        if (routeResult.getThinkingCompatMode() != null) {
            unifiedRequest.getMetadata().put(AbstractProviderClient.META_THINKING_COMPAT_MODE, routeResult.getThinkingCompatMode());
        }
    }

    /**
     * 提取请求中的思考配置信息，写入统计上下文
     */
    private void applyThinkingConfig(UnifiedRequest unifiedRequest, RequestStatsContext context) {
        if (context == null || unifiedRequest == null || unifiedRequest.getGenerationConfig() == null) {
            return;
        }
        UnifiedReasoningConfig reasoning = unifiedRequest.getGenerationConfig().getReasoning();
        if (reasoning == null) {
            return;
        }
        // 记录客户端是否开启了思考（保留 null 语义：null 表示未知/未配置，false 表示明确关闭）
        context.setThinkingEnabled(reasoning.getEnabled());
        // 记录思考深度：优先记录 budgetTokens（直接存数值），其次记录 effort（直接存如 high/medium/low）
        if (reasoning.getBudgetTokens() != null) {
            context.setThinkingDepth(String.valueOf(reasoning.getBudgetTokens()));
        } else if (reasoning.getEffort() != null && !reasoning.getEffort().isBlank()) {
            context.setThinkingDepth(reasoning.getEffort());
        }
    }

    /**
     * 判断是否为 provider 的正常终止事件。
     */
    private boolean isTerminalStreamEvent(UnifiedStreamEvent event) {
        return "done".equals(event.getType());
    }
}
