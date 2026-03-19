package com.code.aigateway.core.controller;

import com.code.aigateway.api.request.OpenAiChatCompletionRequest;
import com.code.aigateway.core.service.ChatGatewayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1")
public class OpenAiChatController {

    private final ChatGatewayService chatGatewayService;

    @PostMapping("/chat/completions")
    public Mono<ResponseEntity<?>> chatCompletions(@Valid @RequestBody OpenAiChatCompletionRequest request) {
        if (Boolean.TRUE.equals(request.getStream())) {
            Flux<ServerSentEvent<String>> flux = chatGatewayService.streamChat(request);
            return Mono.just(ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(flux));
        }

        return chatGatewayService.chat(request)
                .map(ResponseEntity::ok);
    }
}
