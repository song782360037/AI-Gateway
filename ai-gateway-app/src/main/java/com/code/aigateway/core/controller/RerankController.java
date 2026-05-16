package com.code.aigateway.core.controller;

import com.code.aigateway.api.request.RerankRequest;
import com.code.aigateway.core.protocol.ProtocolResolver;
import com.code.aigateway.core.protocol.RerankProtocolAdapter;
import com.code.aigateway.core.service.RerankGatewayService;
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
 * Rerank 控制器
 * <p>
 * 提供 POST /v1/rerank 标准格式的 Reranking API 端点。
 * 纯同步 JSON 响应，不支持流式。
 * </p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1")
public class RerankController {

    private final RerankGatewayService rerankGatewayService;
    private final RerankProtocolAdapter protocolAdapter;

    @PostMapping("/rerank")
    public Mono<ResponseEntity<?>> rerank(@Valid @RequestBody RerankRequest request,
                                           ServerWebExchange exchange) {
        exchange.getAttributes().put(ProtocolResolver.PROTOCOL_ATTRIBUTE_KEY, ProtocolType.RERANK);

        RequestStatsContext context = exchange.getAttribute(RequestStatsContext.ATTRIBUTE_KEY);
        if (context != null) {
            context.setRequestInfo(request);
        }

        return rerankGatewayService.rerankWithStats(request, protocolAdapter, context)
                .map(ResponseEntity::ok);
    }
}
