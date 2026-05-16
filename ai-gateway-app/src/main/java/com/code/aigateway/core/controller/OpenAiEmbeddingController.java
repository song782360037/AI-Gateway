package com.code.aigateway.core.controller;

import com.code.aigateway.api.request.OpenAiEmbeddingRequest;
import com.code.aigateway.core.protocol.OpenAiEmbeddingProtocolAdapter;
import com.code.aigateway.core.protocol.ProtocolResolver;
import com.code.aigateway.core.service.EmbeddingGatewayService;
import com.code.aigateway.core.stats.RequestStatsContext;
import com.code.aigateway.sdk.model.ProtocolType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * OpenAI Embeddings 控制器
 * <p>
 * 提供 OpenAI 格式的 Embeddings API 端点。
 * 纯同步 JSON 响应，不支持流式。
 * </p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1")
public class OpenAiEmbeddingController {

    private final EmbeddingGatewayService embeddingGatewayService;
    private final OpenAiEmbeddingProtocolAdapter protocolAdapter;

    @PostMapping("/embeddings")
    public Mono<ResponseEntity<?>> embeddings(@Valid @RequestBody OpenAiEmbeddingRequest request,
                                               ServerWebExchange exchange) {
        exchange.getAttributes().put(ProtocolResolver.PROTOCOL_ATTRIBUTE_KEY, ProtocolType.OPENAI_EMBEDDING);

        RequestStatsContext context = exchange.getAttribute(RequestStatsContext.ATTRIBUTE_KEY);
        if (context != null) {
            context.setRequestInfo(request);
        }

        return embeddingGatewayService.embeddingWithStats(request, protocolAdapter, context)
                .map(ResponseEntity::ok);
    }
}
