package com.code.aigateway.core.controller;

import com.code.aigateway.api.request.GeminiGenerateContentRequest;
import com.code.aigateway.core.model.ResponseProtocol;
import com.code.aigateway.core.protocol.GeminiProtocolAdapter;
import com.code.aigateway.core.protocol.ProtocolResolver;
import com.code.aigateway.core.service.ChatGatewayService;
import com.code.aigateway.core.stats.RequestStatsContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Google Gemini API 控制器
 * <p>
 * 提供 Gemini API 格式的端点：
 * <ul>
 *   <li>POST /v1beta/models/{model}:generateContent — 非流式</li>
 *   <li>POST /v1beta/models/{model}:streamGenerateContent — 流式（NDJSON）</li>
 * </ul>
 * </p>
 */
@RestController
@RequiredArgsConstructor
public class GeminiController {

    private final ChatGatewayService chatGatewayService;
    private final GeminiProtocolAdapter protocolAdapter;

    /**
     * 非流式 generateContent
     */
    @PostMapping("/v1beta/models/{model}:generateContent")
    public Mono<ResponseEntity<?>> generateContent(@PathVariable String model,
                                                    @Valid @RequestBody GeminiGenerateContentRequest request,
                                                    ServerWebExchange exchange) {
        exchange.getAttributes().put(ProtocolResolver.PROTOCOL_ATTRIBUTE_KEY, ResponseProtocol.GEMINI);

        // 从 URL 路径注入模型名称
        request.setModel(model);

        RequestStatsContext context = exchange.getAttribute(RequestStatsContext.ATTRIBUTE_KEY);
        if (context != null) {
            context.setRequestInfo(request);
        }

        return chatGatewayService.chatWithStats(request, protocolAdapter, context)
                .map(ResponseEntity::ok);
    }

    /**
     * 流式 streamGenerateContent
     * <p>
     * Gemini 流式返回 NDJSON（换行分隔的 JSON），不是 SSE。
     * Content-Type 设为 application/x-ndjson。
     * </p>
     */
    @PostMapping("/v1beta/models/{model}:streamGenerateContent")
    public Mono<ResponseEntity<?>> streamGenerateContent(@PathVariable String model,
                                                           @Valid @RequestBody GeminiGenerateContentRequest request,
                                                           ServerWebExchange exchange) {
        exchange.getAttributes().put(ProtocolResolver.PROTOCOL_ATTRIBUTE_KEY, ResponseProtocol.GEMINI);

        request.setModel(model);
        request.setStream(true);

        RequestStatsContext context = exchange.getAttribute(RequestStatsContext.ATTRIBUTE_KEY);
        if (context != null) {
            context.setRequestInfo(request);
        }

        // Gemini 流式不是 SSE，取 ServerSentEvent 的 data 部分作为 NDJSON 行
        Flux<String> flux = chatGatewayService.streamChat(request, protocolAdapter, context)
                .map(e -> {
                    if (e instanceof org.springframework.http.codec.ServerSentEvent<?> sse) {
                        Object d = sse.data();
                        return d != null ? d.toString() : null;
                    }
                    return e != null ? e.toString() : null;
                })
                .filter(data -> data != null && !data.isEmpty());

        return Mono.just(ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_NDJSON)
                .body(flux));
    }
}
