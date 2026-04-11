package com.code.aigateway.core.protocol;

import com.code.aigateway.api.request.OpenAiChatCompletionRequest;
import com.code.aigateway.api.response.OpenAiChatCompletionChunkResponse;
import com.code.aigateway.api.response.OpenAiErrorResponse;
import com.code.aigateway.core.encoder.OpenAiChatResponseEncoder;
import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.model.ResponseProtocol;
import com.code.aigateway.core.model.StreamContext;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedResponse;
import com.code.aigateway.core.model.UnifiedStreamEvent;
import com.code.aigateway.core.parser.OpenAiChatRequestParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * OpenAI Chat Completions 协议适配器
 * <p>
 * 组合现有的 OpenAiChatRequestParser 和 OpenAiChatResponseEncoder，
 * 并将 ChatGatewayService 中 OpenAI 特有的 SSE 编码和错误编码逻辑提取至此。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class OpenAiChatProtocolAdapter implements ProtocolAdapter {

    private final OpenAiChatRequestParser requestParser;
    private final OpenAiChatResponseEncoder responseEncoder;
    private final ObjectMapper objectMapper;

    @Override
    public ResponseProtocol getProtocol() {
        return ResponseProtocol.OPENAI_CHAT;
    }

    @Override
    public UnifiedRequest parse(Object rawRequest) {
        return requestParser.parse((OpenAiChatCompletionRequest) rawRequest);
    }

    @Override
    public Object encodeResponse(UnifiedResponse response) {
        return responseEncoder.encode(response);
    }

    /**
     * 将 UnifiedStreamEvent 编码为 OpenAI Chat Completions 格式的 SSE chunk。
     * <p>
     * 按照 OpenAI 协议，role 字段仅在首个 content chunk 中出现。
     * 支持 text_delta、tool_call、tool_call_delta、done 四种事件类型。
     * </p>
     */
    @Override
    public Flux<ServerSentEvent<String>> encodeStreamEvent(UnifiedStreamEvent event, StreamContext ctx) {
        // 完成事件
        if ("done".equals(event.getType())) {
            OpenAiChatCompletionChunkResponse chunk = OpenAiChatCompletionChunkResponse.builder()
                    .id(ctx.getResponseId())
                    .object("chat.completion.chunk")
                    .created(ctx.getCreated())
                    .model(ctx.getModel())
                    .choices(List.of(
                            OpenAiChatCompletionChunkResponse.Choice.builder()
                                    .index(event.getOutputIndex() != null ? event.getOutputIndex() : 0)
                                    .delta(OpenAiChatCompletionChunkResponse.Delta.builder().build())
                                    .finishReason(event.getFinishReason() == null ? "stop" : event.getFinishReason())
                                    .build()
                    ))
                    .build();
            return Flux.just(ServerSentEvent.builder(toJson(chunk)).build());
        }

        // 工具调用开始事件（含 id 和 name）
        // 首个事件需携带 role: "assistant"，符合 OpenAI SSE 协议
        if ("tool_call".equals(event.getType())) {
            OpenAiChatCompletionChunkResponse.Delta.DeltaBuilder deltaBuilder = OpenAiChatCompletionChunkResponse.Delta.builder()
                    .toolCalls(List.of(OpenAiChatCompletionChunkResponse.ToolCallDelta.builder()
                            .index(event.getOutputIndex() != null ? event.getOutputIndex() : 0)
                            .id(event.getToolCallId())
                            .type("function")
                            .function(OpenAiChatCompletionChunkResponse.FunctionCallDelta.builder()
                                    .name(event.getToolName())
                                    .arguments("")
                                    .build())
                            .build()));
            // 首个事件添加 role 字段
            if (ctx.tryMarkFirstContentSent()) {
                deltaBuilder.role("assistant");
            }

            OpenAiChatCompletionChunkResponse chunk = OpenAiChatCompletionChunkResponse.builder()
                    .id(ctx.getResponseId())
                    .object("chat.completion.chunk")
                    .created(ctx.getCreated())
                    .model(ctx.getModel())
                    .choices(List.of(
                            OpenAiChatCompletionChunkResponse.Choice.builder()
                                    .index(0)
                                    .delta(deltaBuilder.build())
                                    .finishReason(null)
                                    .build()
                    ))
                    .build();
            return Flux.just(ServerSentEvent.builder(toJson(chunk)).build());
        }

        // 工具调用参数增量事件
        if ("tool_call_delta".equals(event.getType())) {
            OpenAiChatCompletionChunkResponse.ToolCallDelta tcDelta = OpenAiChatCompletionChunkResponse.ToolCallDelta.builder()
                    .index(event.getOutputIndex() != null ? event.getOutputIndex() : 0)
                    .function(OpenAiChatCompletionChunkResponse.FunctionCallDelta.builder()
                            .arguments(event.getArgumentsDelta())
                            .build())
                    .build();

            OpenAiChatCompletionChunkResponse chunk = OpenAiChatCompletionChunkResponse.builder()
                    .id(ctx.getResponseId())
                    .object("chat.completion.chunk")
                    .created(ctx.getCreated())
                    .model(ctx.getModel())
                    .choices(List.of(
                            OpenAiChatCompletionChunkResponse.Choice.builder()
                                    .index(0)
                                    .delta(OpenAiChatCompletionChunkResponse.Delta.builder()
                                            .toolCalls(List.of(tcDelta))
                                            .build())
                                    .finishReason(null)
                                    .build()
                    ))
                    .build();
            return Flux.just(ServerSentEvent.builder(toJson(chunk)).build());
        }

        // thinking_delta 事件：OpenAI Chat 不原生支持 thinking，静默丢弃避免产生空 content
        if ("thinking_delta".equals(event.getType())) {
            return Flux.empty();
        }

        // 文本增量事件
        // 首个 content chunk 携带 role 字段，符合 OpenAI SSE 协议
        OpenAiChatCompletionChunkResponse.Delta.DeltaBuilder deltaBuilder = OpenAiChatCompletionChunkResponse.Delta.builder()
                .content(event.getTextDelta() == null ? "" : event.getTextDelta());
        if (ctx.tryMarkFirstContentSent()) {
            deltaBuilder.role("assistant");
        }

        OpenAiChatCompletionChunkResponse chunk = OpenAiChatCompletionChunkResponse.builder()
                .id(ctx.getResponseId())
                .object("chat.completion.chunk")
                .created(ctx.getCreated())
                .model(ctx.getModel())
                .choices(List.of(
                        OpenAiChatCompletionChunkResponse.Choice.builder()
                                .index(event.getOutputIndex() != null ? event.getOutputIndex() : 0)
                                .delta(deltaBuilder.build())
                                .finishReason(null)
                                .build()
                ))
                .build();
        return Flux.just(ServerSentEvent.builder(toJson(chunk)).build());
    }

    @Override
    public Flux<ServerSentEvent<String>> encodeStreamError(Throwable throwable, StreamContext ctx) {
        ErrorCode errorCode = throwable instanceof GatewayException ge
                ? ge.getErrorCode()
                : ErrorCode.INTERNAL_ERROR;
        String message = throwable.getMessage() == null || throwable.getMessage().isBlank()
                ? "internal server error"
                : throwable.getMessage();
        String param = throwable instanceof GatewayException ge ? ge.getParam() : null;
        ServerSentEvent<String> errorEvent = ServerSentEvent.<String>builder(
                toJson(buildError(message, mapErrorType(errorCode), errorCode.name(), param)))
                .build();
        return Flux.just(errorEvent).concatWith(terminalStreamEvents(ctx));
    }

    @Override
    public Flux<ServerSentEvent<String>> terminalStreamEvents(StreamContext ctx) {
        // OpenAI Chat Completions 以 [DONE] 结束流
        return Flux.just(ServerSentEvent.builder("[DONE]").build());
    }

    @Override
    public boolean isSse() {
        return true;
    }

    @Override
    public Object buildError(String message, String errorType, String code, String param) {
        return new OpenAiErrorResponse(
                new OpenAiErrorResponse.Error(message, errorType, code, param)
        );
    }

    @Override
    public String mapErrorType(ErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_REQUEST, MODEL_NOT_FOUND, CAPABILITY_NOT_SUPPORTED -> "invalid_request_error";
            case AUTH_FAILED -> "authentication_error";
            case PROVIDER_RATE_LIMIT -> "rate_limit_error";
            case PROVIDER_AUTH_ERROR -> "authentication_error";
            case PROVIDER_BAD_REQUEST -> "invalid_request_error";
            case PROVIDER_RESOURCE_NOT_FOUND -> "invalid_request_error";
            default -> "server_error";
        };
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize sse chunk", e);
        }
    }
}
