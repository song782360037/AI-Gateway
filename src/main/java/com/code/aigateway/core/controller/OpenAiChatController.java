package com.code.aigateway.core.controller;

import com.code.aigateway.api.request.OpenAiChatCompletionRequest;
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
 *
 * @author sst
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1")
public class OpenAiChatController {

    /** 聊天网关服务 */
    private final ChatGatewayService chatGatewayService;

    /**
     * 处理聊天完成请求
     * <p>
     * 根据 stream 参数决定返回流式或非流式响应：
     * <ul>
     *   <li>stream=true: 返回 SSE 流式响应</li>
     *   <li>stream=false: 返回普通 JSON 响应</li>
     * </ul>
     * </p>
     *
     * @param request OpenAI 格式的聊天完成请求
     * @param exchange 当前 WebFlux 请求上下文，用于透传统计上下文
     * @return 流式返回 SSE Flux 或非流式返回 JSON Mono
     */
    @PostMapping("/chat/completions")
    public Mono<ResponseEntity<?>> chatCompletions(@Valid @RequestBody OpenAiChatCompletionRequest request,
                                                   ServerWebExchange exchange) {
        RequestStatsContext context = exchange.getAttribute(RequestStatsContext.ATTRIBUTE_KEY);
        if (context != null) {
            context.setRequest(request);
        }

        // 流式请求：返回 SSE 格式
        if (Boolean.TRUE.equals(request.getStream())) {
            Flux<ServerSentEvent<String>> flux = chatGatewayService.streamChat(request, context);
            return Mono.just(ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(flux));
        }

        // 非流式请求：返回 JSON 格式
        return chatGatewayService.chat(request, context)
                .map(ResponseEntity::ok);
    }
}
