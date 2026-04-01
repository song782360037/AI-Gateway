package com.code.aigateway.core.controller;

import com.code.aigateway.api.response.OpenAiChatCompletionResponse;
import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.error.GlobalExceptionHandler;
import com.code.aigateway.core.protocol.OpenAiChatProtocolAdapter;
import com.code.aigateway.core.service.ChatGatewayService;
import com.code.aigateway.core.stats.RequestStatsCollector;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class OpenAiChatControllerErrorMappingTest {

    private ChatGatewayService chatGatewayService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        chatGatewayService = Mockito.mock(ChatGatewayService.class);
        OpenAiChatProtocolAdapter protocolAdapter = new OpenAiChatProtocolAdapter(
                Mockito.mock(com.code.aigateway.core.parser.OpenAiChatRequestParser.class),
                Mockito.mock(com.code.aigateway.core.encoder.OpenAiChatResponseEncoder.class),
                new ObjectMapper()
        );
        OpenAiChatController controller = new OpenAiChatController(chatGatewayService, protocolAdapter);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler(Mockito.mock(RequestStatsCollector.class), java.util.List.of(protocolAdapter)))
                .build();
    }

    @Test
    void chatCompletions_validationFailure_returnsOpenAi400Error() {
        webTestClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "",
                          "messages": [
                            {"role": "user", "content": "hi"}
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.type").isEqualTo("invalid_request_error")
                .jsonPath("$.error.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.error.message").exists()
                .jsonPath("$.error.param").exists();
    }

    @Test
    void chatCompletions_bodyDecodeFailure_returnsOpenAi400Error() {
        webTestClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "gpt-4o-mini",
                          "messages": "invalid"
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.type").isEqualTo("invalid_request_error")
                .jsonPath("$.error.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.error.message").exists();
    }

    @Test
    void chatCompletions_gatewayTimeoutFromService_returnsOpenAi504Error() {
        Mono<?> errorMono = Mono.error(new GatewayException(ErrorCode.PROVIDER_TIMEOUT, "provider timeout"));
        Mockito.doReturn(errorMono).when(chatGatewayService).chatWithStats(Mockito.any(), Mockito.any(), Mockito.any());

        webTestClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validRequestJson())
                .exchange()
                .expectStatus().isEqualTo(504)
                .expectBody()
                .jsonPath("$.error.type").isEqualTo("server_error")
                .jsonPath("$.error.code").isEqualTo("PROVIDER_TIMEOUT")
                .jsonPath("$.error.message").isEqualTo("provider timeout");
    }

    @Test
    void chatCompletions_unexpectedExceptionFromService_returnsOpenAi500Error() {
        Mono<?> errorMono = Mono.error(new IllegalStateException("boom"));
        Mockito.doReturn(errorMono).when(chatGatewayService).chatWithStats(Mockito.any(), Mockito.any(), Mockito.any());

        webTestClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validRequestJson())
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.error.type").isEqualTo("server_error")
                .jsonPath("$.error.code").isEqualTo("INTERNAL_ERROR")
                .jsonPath("$.error.message").isEqualTo("internal server error");
    }

    @Test
    void chatCompletions_successResponse_passesThroughController() {
        OpenAiChatCompletionResponse response = OpenAiChatCompletionResponse.builder()
                .id("chatcmpl-test")
                .object("chat.completion")
                .created(1L)
                .model("gpt-4o-mini")
                .build();
        @SuppressWarnings("unchecked")
        Mono<OpenAiChatCompletionResponse> typedResponse = Mono.just(response);
        Mockito.doReturn(typedResponse).when(chatGatewayService).chatWithStats(Mockito.any(), Mockito.any(), Mockito.any());

        webTestClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validRequestJson())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("chatcmpl-test")
                .jsonPath("$.model").isEqualTo("gpt-4o-mini");
    }

    private String validRequestJson() {
        return """
                {
                  "model": "gpt-4o-mini",
                  "messages": [
                    {"role": "user", "content": "你好"}
                  ]
                }
                """;
    }
}
