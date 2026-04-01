package com.code.aigateway.core.controller;

import com.code.aigateway.api.request.OpenAiResponsesRequest;
import com.code.aigateway.core.model.ResponseProtocol;
import com.code.aigateway.core.protocol.OpenAiResponsesProtocolAdapter;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * OpenAI Responses API 控制器
 * <p>
 * 提供 OpenAI Responses API 格式的端点（POST /v1/responses）。
 * </p>
 */
@RestController
@RequiredArgsConstructor
public class OpenAiResponsesController {

    private final ChatGatewayService chatGatewayService;
    private final OpenAiResponsesProtocolAdapter protocolAdapter;

    @PostMapping("/v1/responses")
    public Mono<ResponseEntity<?>> responses(@Valid @RequestBody OpenAiResponsesRequest request,
                                            ServerWebExchange exchange) {
        // 标记协议类型
        exchange.getAttributes().put(ProtocolResolver.PROTOCOL_ATTRIBUTE_KEY, ResponseProtocol.OPENAI_RESPONSES);

        RequestStatsContext context = exchange.getAttribute(RequestStatsContext.ATTRIBUTE_KEY);
        if (context != null) {
            context.setRequestInfo(request);
        }

        if (Boolean.TRUE.equals(request.getStream())) {
            Flux<ServerSentEvent<String>> flux = chatGatewayService.streamChat(request, protocolAdapter, context)
                    .map(e -> (ServerSentEvent<String>) e);
            return Mono.just(ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(flux));
        }

        return chatGatewayService.chatWithStats(request, protocolAdapter, context)
                .map(ResponseEntity::ok);
    }
}
