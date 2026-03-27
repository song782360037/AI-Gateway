package com.code.aigateway.core.service;

import com.code.aigateway.api.request.OpenAiChatCompletionRequest;
import com.code.aigateway.api.response.OpenAiChatCompletionChunkResponse;
import com.code.aigateway.api.response.OpenAiChatCompletionResponse;
import com.code.aigateway.core.capability.CapabilityChecker;
import com.code.aigateway.core.encoder.OpenAiChatResponseEncoder;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedStreamEvent;
import com.code.aigateway.core.parser.OpenAiChatRequestParser;
import com.code.aigateway.core.router.ModelRouter;
import com.code.aigateway.core.router.RouteResult;
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

    /**
     * 处理非流式聊天请求
     * <p>
     * 执行流程：解析请求 -> 路由模型 -> 能力检查 -> 调用提供商 -> 编码响应
     * </p>
     *
     * @param request OpenAI 格式的聊天请求
     * @return 包含 OpenAI 格式响应的 Mono
     */
    public Mono<OpenAiChatCompletionResponse> chat(OpenAiChatCompletionRequest request) {
        // 1. 解析请求：将 OpenAI 格式转换为统一格式
        UnifiedRequest unifiedRequest = requestParser.parse(request);

        // 2. 路由：根据模型别名确定目标提供商和模型
        RouteResult routeResult = modelRouter.route(unifiedRequest);

        // 3. 能力检查：验证请求参数与目标模型的能力兼容性
        capabilityChecker.validate(unifiedRequest, routeResult);

        // 4. 设置请求参数
        unifiedRequest.setProvider(routeResult.getProviderType().name().toLowerCase());
        unifiedRequest.setModel(routeResult.getTargetModel());
        unifiedRequest.setExecutionContext(buildExecutionContext(routeResult));

        // 5. 获取提供商客户端并调用
        ProviderClient providerClient = providerClientFactory.getClient(routeResult.getProviderType());

        // 6. 调用客户端并将统一响应编码为 OpenAI 格式
        return providerClient.chat(unifiedRequest)
                .map(responseEncoder::encode);
    }

    /**
     * 处理流式聊天请求
     * <p>
     * 执行流程与非流式类似，但返回 SSE 流式响应
     * </p>
     *
     * @param request OpenAI 格式的聊天请求
     * @return 包含 SSE 事件的 Flux 流
     */
    public Flux<ServerSentEvent<String>> streamChat(OpenAiChatCompletionRequest request) {
        // 1. 解析请求
        UnifiedRequest unifiedRequest = requestParser.parse(request);

        // 2. 路由
        RouteResult routeResult = modelRouter.route(unifiedRequest);

        // 3. 能力检查
        capabilityChecker.validate(unifiedRequest, routeResult);

        // 4. 设置请求参数
        unifiedRequest.setProvider(routeResult.getProviderType().name().toLowerCase());
        unifiedRequest.setModel(routeResult.getTargetModel());
        unifiedRequest.setExecutionContext(buildExecutionContext(routeResult));

        // 5. 获取提供商客户端
        ProviderClient providerClient = providerClientFactory.getClient(routeResult.getProviderType());

        // 6. 生成响应 ID 和时间戳
        String responseId = "chatcmpl-" + UUID.randomUUID();
        long created = Instant.now().getEpochSecond();
        String model = routeResult.getTargetModel();

        // 7. 调用流式接口并转换为 SSE 格式，最后追加 [DONE] 事件
        return providerClient.streamChat(unifiedRequest)
                .map(event -> toSse(event, responseId, created, model))
                .concatWith(Flux.just(ServerSentEvent.builder("[DONE]").build()));
    }

    /**
     * 将统一流式事件转换为 OpenAI 格式的 SSE 事件
     *
     * @param event      统一流式事件
     * @param responseId 响应 ID
     * @param created    创建时间戳
     * @param model      模型名称
     * @return SSE 事件
     */
    private ServerSentEvent<String> toSse(UnifiedStreamEvent event, String responseId, long created, String model) {
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

        OpenAiChatCompletionChunkResponse chunkResponse = OpenAiChatCompletionChunkResponse.builder()
                .id(responseId)
                .object("chat.completion.chunk")
                .created(created)
                .model(model)
                .choices(List.of(
                        OpenAiChatCompletionChunkResponse.Choice.builder()
                                .index(resolveOutputIndex(event))
                                .delta(OpenAiChatCompletionChunkResponse.Delta.builder()
                                        .role("assistant")
                                        .content(event.getTextDelta() == null ? "" : event.getTextDelta())
                                        .build())
                                .finishReason(null)
                                .build()
                ))
                .build();
        return ServerSentEvent.builder(toJson(chunkResponse)).build();
    }

    /**
     * 构建 Provider 运行时上下文
     *
     * @param routeResult 路由结果
     * @return Provider 运行时上下文
     */
    private UnifiedRequest.ProviderExecutionContext buildExecutionContext(RouteResult routeResult) {
        UnifiedRequest.ProviderExecutionContext executionContext = new UnifiedRequest.ProviderExecutionContext();
        executionContext.setProviderName(routeResult.getProviderName());
        executionContext.setProviderBaseUrl(routeResult.getProviderBaseUrl());
        executionContext.setProviderVersion(routeResult.getProviderVersion());
        executionContext.setProviderTimeoutSeconds(routeResult.getProviderTimeoutSeconds());
        // 透传运行时 API Key，供 provider client 直接使用，避免回查 YAML 配置
        executionContext.setProviderApiKey(routeResult.getProviderApiKey());
        return executionContext;
    }

    /**
     * 解析输出索引
     *
     * @param event 统一流式事件
     * @return 输出索引
     */
    private int resolveOutputIndex(UnifiedStreamEvent event) {
        return event.getOutputIndex() == null ? 0 : event.getOutputIndex();
    }

    /**
     * 将对象序列化为 JSON 字符串
     *
     * @param value 待序列化对象
     * @return JSON 字符串
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize sse chunk", e);
        }
    }
}
