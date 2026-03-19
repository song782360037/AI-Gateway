package com.code.aigateway.core.service;

import com.code.aigateway.api.request.OpenAiChatCompletionRequest;
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatGatewayService {

    private final OpenAiChatRequestParser requestParser;
    private final ModelRouter modelRouter;
    private final CapabilityChecker capabilityChecker;
    private final ProviderClientFactory providerClientFactory;
    private final OpenAiChatResponseEncoder responseEncoder;

    public Mono<OpenAiChatCompletionResponse> chat(OpenAiChatCompletionRequest request) {
        // 解析请求
        UnifiedRequest unifiedRequest = requestParser.parse(request);
        // 路由
        RouteResult routeResult = modelRouter.route(unifiedRequest);
        // 能力检查
        capabilityChecker.validate(unifiedRequest, routeResult);

        // 设置请求参数
        unifiedRequest.setProvider(routeResult.getProviderType().name().toLowerCase());
        // 设置模型名称
        unifiedRequest.setModel(routeResult.getTargetModel());

        // 获取客户端
        ProviderClient providerClient = providerClientFactory.getClient(routeResult.getProviderType());
        // 调用客户端
        return providerClient.chat(unifiedRequest)
                .map(responseEncoder::encode);
    }

    public Flux<ServerSentEvent<String>> streamChat(OpenAiChatCompletionRequest request) {
        UnifiedRequest unifiedRequest = requestParser.parse(request);
        RouteResult routeResult = modelRouter.route(unifiedRequest);
        capabilityChecker.validate(unifiedRequest, routeResult);

        unifiedRequest.setProvider(routeResult.getProviderType().name().toLowerCase());
        unifiedRequest.setModel(routeResult.getTargetModel());

        ProviderClient providerClient = providerClientFactory.getClient(routeResult.getProviderType());

        String responseId = "chatcmpl-" + UUID.randomUUID();
        long created = Instant.now().getEpochSecond();
        String model = routeResult.getTargetModel();

        return providerClient.streamChat(unifiedRequest)
                .map(event -> toSse(event, responseId, created, model))
                .concatWith(Flux.just(ServerSentEvent.builder("[DONE]").build()));
    }

    private ServerSentEvent<String> toSse(UnifiedStreamEvent event, String responseId, long created, String model) {
        if ("done".equals(event.getType())) {
            String json = """
                    {
                      "id":"%s",
                      "object":"chat.completion.chunk",
                      "created":%d,
                      "model":"%s",
                      "choices":[
                        {
                          "index":0,
                          "delta":{},
                          "finish_reason":"%s"
                        }
                      ]
                    }
                    """.formatted(responseId, created, model,
                    event.getFinishReason() == null ? "stop" : event.getFinishReason());
            return ServerSentEvent.builder(json.replaceAll("\\s+", " ")).build();
        }

        String content = event.getTextDelta() == null ? "" : escapeJson(event.getTextDelta());
        String json = """
                {
                  "id":"%s",
                  "object":"chat.completion.chunk",
                  "created":%d,
                  "model":"%s",
                  "choices":[
                    {
                      "index":0,
                      "delta":{
                        "content":"%s"
                      },
                      "finish_reason":null
                    }
                  ]
                }
                """.formatted(responseId, created, model, content);

        return ServerSentEvent.builder(json.replaceAll("\\s+", " ")).build();
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
