package com.code.aigateway.core.service;

import com.code.aigateway.core.capability.CapabilityChecker;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedStreamEvent;
import com.code.aigateway.core.model.UnifiedUsage;
import com.code.aigateway.core.model.StreamContext;
import com.code.aigateway.core.protocol.ProtocolAdapter;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 聊天网关核心服务
 * <p>
 * 协议无关的编排服务，负责：
 * <ul>
 *   <li>请求解析：委托 ProtocolAdapter 将原始请求转换为统一格式</li>
 *   <li>模型路由：根据模型别名路由到实际提供商</li>
 *   <li>能力检查：验证请求与目标模型的能力兼容性</li>
 *   <li>提供商调用：选择并调用对应的 ProviderClient</li>
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

    /**
     * 处理非流式聊天请求（含 usage 统计）
     * <p>
     * 执行流程：解析请求 -> 路由模型 -> 能力检查 -> 调用提供商 -> 编码响应
     * </p>
     */
    public Mono<?> chatWithStats(Object rawRequest, ProtocolAdapter adapter, RequestStatsContext context) {
        ParsedCall prepared = prepareCall(rawRequest, adapter, context);
        return prepared.providerClient().chat(prepared.unifiedRequest())
                .doOnNext(response -> requestStatsCollector.collectSuccess(context, response.getUsage()))
                .map(adapter::encodeResponse);
    }

    /**
     * 处理流式聊天请求
     * <p>
     * 执行流程与非流式类似，但返回编码后的流式响应。
     * 流式结束时会自动采集 usage 统计。
     * </p>
     */
    public Flux<?> streamChat(Object rawRequest, ProtocolAdapter adapter, RequestStatsContext context) {
        ParsedCall prepared = prepareCall(rawRequest, adapter, context);

        String responseId = "chatcmpl-" + UUID.randomUUID();
        long created = Instant.now().getEpochSecond();
        String model = prepared.routeResult().getTargetModel();
        AtomicReference<UnifiedUsage> finalUsageRef = new AtomicReference<>();
        StreamContext streamCtx = new StreamContext(responseId, created, model);

        return prepared.providerClient().streamChat(prepared.unifiedRequest())
                .doOnNext(event -> {
                    if ("done".equals(event.getType()) && event.getUsage() != null) {
                        finalUsageRef.set(event.getUsage());
                    }
                })
                .map(event -> adapter.encodeStreamEvent(event, streamCtx))
                .concatWith(adapter.terminalStreamEvents(streamCtx))
                .doOnComplete(() -> requestStatsCollector.collectStreamSuccess(context, finalUsageRef.get()))
                .doOnError(ex -> requestStatsCollector.collectError(context, ex));
    }

    /**
     * 公共的"解析-路由-校验"前置流程
     */
    private ParsedCall prepareCall(Object rawRequest, ProtocolAdapter adapter, RequestStatsContext context) {
        UnifiedRequest unifiedRequest = adapter.parse(rawRequest);
        RouteResult routeResult = modelRouter.route(unifiedRequest);
        capabilityChecker.validate(unifiedRequest, routeResult);

        if (context != null) {
            context.setRouteResult(routeResult);
        }

        applyRouteContext(unifiedRequest, routeResult);
        ProviderClient providerClient = providerClientFactory.getClient(routeResult.getProviderType());
        return new ParsedCall(unifiedRequest, routeResult, providerClient);
    }

    private void applyRouteContext(UnifiedRequest unifiedRequest, RouteResult routeResult) {
        unifiedRequest.setProvider(routeResult.getProviderType().name().toLowerCase());
        unifiedRequest.setModel(routeResult.getTargetModel());
        unifiedRequest.setExecutionContext(buildExecutionContext(routeResult));
    }

    private UnifiedRequest.ProviderExecutionContext buildExecutionContext(RouteResult routeResult) {
        UnifiedRequest.ProviderExecutionContext executionContext = new UnifiedRequest.ProviderExecutionContext();
        executionContext.setProviderName(routeResult.getProviderName());
        executionContext.setProviderBaseUrl(routeResult.getProviderBaseUrl());
        executionContext.setProviderVersion(routeResult.getProviderVersion());
        executionContext.setProviderTimeoutSeconds(routeResult.getProviderTimeoutSeconds());
        executionContext.setProviderApiKey(routeResult.getProviderApiKey());
        return executionContext;
    }

    /** 前置解析结果的值对象 */
    private record ParsedCall(UnifiedRequest unifiedRequest, RouteResult routeResult, ProviderClient providerClient) {
    }
}
