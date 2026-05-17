package com.code.aigateway.core.controller;

import com.code.aigateway.api.request.AnthropicMessagesRequest;
import com.code.aigateway.core.protocol.AnthropicProtocolAdapter;
import com.code.aigateway.core.protocol.ProtocolResolver;
import com.code.aigateway.core.service.CountTokensService;
import com.code.aigateway.sdk.model.ProtocolType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Anthropic count_tokens 控制器
 * <p>
 * 提供 POST /v1/messages/count_tokens 端点，支持跨上游 Provider 的 token 计数。
 * Anthropic 上游精确计数，其他上游本地估算。
 * </p>
 */
@RestController
@RequiredArgsConstructor
public class AnthropicCountTokensController {

    private final CountTokensService countTokensService;
    private final AnthropicProtocolAdapter protocolAdapter;

    @PostMapping("/v1/messages/count_tokens")
    public Mono<ResponseEntity<?>> countTokens(
            @Valid @RequestBody AnthropicMessagesRequest request,
            ServerWebExchange exchange) {
        exchange.getAttributes().put(ProtocolResolver.PROTOCOL_ATTRIBUTE_KEY, ProtocolType.ANTHROPIC);
        return countTokensService.countTokens(request, protocolAdapter)
                .map(ResponseEntity::ok);
    }
}
