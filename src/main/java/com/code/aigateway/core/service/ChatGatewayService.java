package com.code.aigateway.core.service;

import com.code.aigateway.api.request.OpenAiChatCompletionRequest;
import com.code.aigateway.api.response.OpenAiChatCompletionChunkResponse;
import com.code.aigateway.api.response.OpenAiChatCompletionResponse;
import com.code.aigateway.core.capability.CapabilityChecker;
import com.code.aigateway.core.encoder.OpenAiChatResponseEncoder;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedStreamEvent;
import com.code.aigateway.core.model.UnifiedUsage;
import com.code.aigateway.core.parser.OpenAiChatRequestParser;
import com.code.aigateway.core.router.ModelRouter;
import com.code.aigateway.core.router.RouteResult;
import com.code.aigateway.core.stats.RequestStatsCollector;
import com.code.aigateway.core.stats.RequestStatsContext;
import com.code.aigateway.provider.ProviderClient;
import com.code.aigateway.provider.ProviderClientFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
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
 * 负责处理聊天请求的核心业务逻辑，包括：
 * <ul>
 *   <li>请求解析：将 OpenAI 格式请求转换为统一格式</li>
 *   <li>模型路由：根据模型别名路由到实际提供商</li>
 *   <li>能力检查：验证请求与目标模型的能力兼容性</li>
 *   <li>响应编码：将统一响应转换为 OpenAI 格式</li>
 * </ul>
 * </p>
 *
 * @author sst
 */
@Service
@RequiredArgsConstructor
public class ChatGatewayService {

    /** OpenAI 请求解析器 */
    private final OpenAiChatRequestParser requestParser;

    /** 模型路由器 */
    private final ModelRouter modelRouter;

    /** 能力检查器 */
    private final CapabilityChecker capabilityChecker;

    /** 提供商客户端工厂 */
    private final ProviderClientFactory providerClientFactory;

    /** OpenAI 响应编码器 */
    private final OpenAiChatResponseEncoder responseEncoder;

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper;

    /** 请求统计采集器 */
    private final RequestStatsCollector requestStatsCollector;

    /**
     * 处理非流式聊天请求
     * <p>
     * 执行流程：解析请求 -> 路由模型 -> 能力检查 -> 调用提供商 -> 编码响应
     * </p>
     *
     * @param request OpenAI 格式的聊天请求
     * @param context 当前请求统计上下文
     * @return 包含 OpenAI 格式响应的 Mono
     */
    public Mono<OpenAiChatCompletionResponse> chat(OpenAiChatCompletionRequest request, RequestStatsContext context) {
        UnifiedRequest unifiedRequest = requestParser.parse(request);
        RouteResult routeResult = modelRouter.route(unifiedRequest);
        capabilityChecker.validate(unifiedRequest, routeResult);

        if (context != null) {
            context.setRouteResult(routeResult);
        }

        unifiedRequest.setProvider(routeResult.getProviderType().name().toLowerCase());
        unifiedRequest.setModel(routeResult.getTargetModel());
        unifiedRequest.setExecutionContext(buildExecutionContext(routeResult));

        ProviderClient providerClient = providerClientFactory.getClient(routeResult.getProviderType());

        return providerClient.chat(unifiedRequest)
                .map(responseEncoder::encode)
                .doOnNext(response -> requestStatsCollector.collectSuccess(context, response));
    }

    /**
     * 处理流式聊天请求
     * <p>
     * 执行流程与非流式类似，但返回 SSE 流式响应
     * </p>
     *
     * @param request OpenAI 格式的聊天请求
     * @param context 当前请求统计上下文
     * @return 包含 SSE 事件的 Flux 流
     */
    public Flux<ServerSentEvent<String>> streamChat(OpenAiChatCompletionRequest request, RequestStatsContext context) {
        UnifiedRequest unifiedRequest = requestParser.parse(request);
        RouteResult routeResult = modelRouter.route(unifiedRequest);
        capabilityChecker.validate(unifiedRequest, routeResult);

        if (context != null) {
            context.setRouteResult(routeResult);
        }

        unifiedRequest.setProvider(routeResult.getProviderType().name().toLowerCase());
        unifiedRequest.setModel(routeResult.getTargetModel());
        unifiedRequest.setExecutionContext(buildExecutionContext(routeResult));

        ProviderClient providerClient = providerClientFactory.getClient(routeResult.getProviderType());
        String responseId = "chatcmpl-" + UUID.randomUUID();
        long created = Instant.now().getEpochSecond();
        String model = routeResult.getTargetModel();
        AtomicReference<UnifiedUsage> finalUsageRef = new AtomicReference<>();
        // 标记是否为首个 content chunk，用于在 SSE 首包中携带 role 字段
        AtomicBoolean firstContentSent = new AtomicBoolean(false);

        return providerClient.streamChat(unifiedRequest)
                .doOnNext(event -> {
                    if ("done".equals(event.getType()) && event.getUsage() != null) {
                        finalUsageRef.set(event.getUsage());
                    }
                })
                .map(event -> toSse(event, responseId, created, model, firstContentSent))
                .concatWith(Flux.just(ServerSentEvent.builder("[DONE]").build()))
                .doOnComplete(() -> requestStatsCollector.collectStreamSuccess(context, finalUsageRef.get()))
                .doOnError(ex -> requestStatsCollector.collectError(context, ex));
    }

    /**
     * 将统一流式事件转换为 OpenAI 格式的 SSE 事件
     * <p>
     * 按照 OpenAI 协议，role 字段仅在首个 content chunk 中出现。
     * </p>
     */
    private ServerSentEvent<String> toSse(UnifiedStreamEvent event, String responseId, long created,
                                          String model, AtomicBoolean firstContentSent) {
        if ("done".equals(event.getType())) {
            OpenAiChatCompletionChunkResponse chunkResponse = OpenAiChatCompletionChunkResponse.builder()
                    .id(responseId)
                    .object("chat.completion.chunk")
                    .created(created)
                    .model(model)
                    .choices(List.of(
                            OpenAiChatCompletionChunkResponse.Choice.builder()
                                    .index(resolveOutputIndex(event))
                                    .delta(OpenAiChatCompletionChunkResponse.Delta.builder().build())
                                    .finishReason(event.getFinishReason() == null ? "stop" : event.getFinishReason())
                                    .build()
                    ))
                    .build();
            return ServerSentEvent.builder(toJson(chunkResponse)).build();
        }

        // 仅首个 content chunk 携带 role 字段，符合 OpenAI SSE 协议
        OpenAiChatCompletionChunkResponse.Delta.DeltaBuilder deltaBuilder = OpenAiChatCompletionChunkResponse.Delta.builder()
                .content(event.getTextDelta() == null ? "" : event.getTextDelta());
        if (firstContentSent.compareAndSet(false, true)) {
            deltaBuilder.role("assistant");
        }

        OpenAiChatCompletionChunkResponse chunkResponse = OpenAiChatCompletionChunkResponse.builder()
                .id(responseId)
                .object("chat.completion.chunk")
                .created(created)
                .model(model)
                .choices(List.of(
                        OpenAiChatCompletionChunkResponse.Choice.builder()
                                .index(resolveOutputIndex(event))
                                .delta(deltaBuilder.build())
                                .finishReason(null)
                                .build()
                ))
                .build();
        return ServerSentEvent.builder(toJson(chunkResponse)).build();
    }

    /**
     * 构建 Provider 运行时上下文
     */
    private UnifiedRequest.ProviderExecutionContext buildExecutionContext(RouteResult routeResult) {
        UnifiedRequest.ProviderExecutionContext executionContext = new UnifiedRequest.ProviderExecutionContext();
        executionContext.setProviderName(routeResult.getProviderName());
        executionContext.setProviderBaseUrl(routeResult.getProviderBaseUrl());
        executionContext.setProviderVersion(routeResult.getProviderVersion());
        executionContext.setProviderTimeoutSeconds(routeResult.getProviderTimeoutSeconds());
        executionContext.setProviderApiKey(routeResult.getProviderApiKey());
        return executionContext;
    }

    /**
     * 解析输出索引
     */
    private int resolveOutputIndex(UnifiedStreamEvent event) {
        return event.getOutputIndex() == null ? 0 : event.getOutputIndex();
    }

    /**
     * 将对象序列化为 JSON 字符串
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize sse chunk", e);
        }
    }
}
