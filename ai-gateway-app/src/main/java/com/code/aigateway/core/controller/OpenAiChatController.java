package com.code.aigateway.core.controller;

import com.code.aigateway.api.request.OpenAiChatCompletionRequest;
import com.code.aigateway.sdk.model.ProtocolType;
import com.code.aigateway.core.protocol.OpenAiChatProtocolAdapter;
import com.code.aigateway.core.protocol.ProtocolResolver;
import com.code.aigateway.core.service.ChatGatewayService;
import com.code.aigateway.core.stats.RequestStatsContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * OpenAI 兼容的聊天控制器
 * <p>
 * 提供 OpenAI 格式的聊天完成 API 端点，兼容 OpenAI SDK 调用。
 * 支持流式和非流式两种响应模式。
 * </p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1")
public class OpenAiChatController {

    private final ChatGatewayService chatGatewayService;
    private final OpenAiChatProtocolAdapter protocolAdapter;

    /**
     * 处理聊天完成请求
     */
    @PostMapping("/chat/completions")
    public Mono<ResponseEntity<?>> chatCompletions(@Valid @RequestBody OpenAiChatCompletionRequest request,
                                                   ServerWebExchange exchange) {
        // 在 exchange 中标记协议类型，供 GlobalExceptionHandler 使用
        exchange.getAttributes().put(ProtocolResolver.PROTOCOL_ATTRIBUTE_KEY, ProtocolType.OPENAI_CHAT);

        RequestStatsContext context = exchange.getAttribute(RequestStatsContext.ATTRIBUTE_KEY);
        if (context != null) {
            context.setRequestInfo(request);
        }

        // 流式请求：返回 SSE 格式
        if (Boolean.TRUE.equals(request.getStream())) {
            Flux<ServerSentEvent<String>> flux = chatGatewayService.streamChat(request, protocolAdapter, context)
                    .map(e -> (ServerSentEvent<String>) e);
            return Mono.just(ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(flux));
        }

        // 非流式请求：返回 JSON 格式
        return chatGatewayService.chatWithStats(request, protocolAdapter, context)
                .map(ResponseEntity::ok);
    }
}
