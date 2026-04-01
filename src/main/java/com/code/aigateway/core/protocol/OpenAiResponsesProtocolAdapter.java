package com.code.aigateway.core.protocol;

import com.code.aigateway.api.request.OpenAiResponsesRequest;
import com.code.aigateway.api.response.OpenAiErrorResponse;
import com.code.aigateway.api.response.OpenAiResponsesResponse;
import com.code.aigateway.core.encoder.OpenAiResponsesResponseEncoder;
import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.model.ResponseProtocol;
import com.code.aigateway.core.model.StreamContext;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedResponse;
import com.code.aigateway.core.model.UnifiedStreamEvent;
import com.code.aigateway.core.parser.OpenAiResponsesRequestParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * OpenAI Responses API 协议适配器
 * <p>
 * SSE 事件使用点分命名：response.output_text.delta、response.function_call_arguments.delta、response.completed
 * 错误格式复用 OpenAI 标准格式。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class OpenAiResponsesProtocolAdapter implements ProtocolAdapter {

    private final OpenAiResponsesRequestParser requestParser;
    private final OpenAiResponsesResponseEncoder responseEncoder;
    private final ObjectMapper objectMapper;

    @Override
    public ResponseProtocol getProtocol() {
        return ResponseProtocol.OPENAI_RESPONSES;
    }

    @Override
    public UnifiedRequest parse(Object rawRequest) {
        return requestParser.parse((OpenAiResponsesRequest) rawRequest);
    }

    @Override
    public Object encodeResponse(UnifiedResponse response) {
        return responseEncoder.encode(response);
    }

    /**
     * 将 UnifiedStreamEvent 编码为 Responses API 格式的 SSE 事件
     */
    @Override
    public ServerSentEvent<String> encodeStreamEvent(UnifiedStreamEvent event, StreamContext ctx) {
        if ("done".equals(event.getType())) {
            // response.completed 事件
            Map<String, Object> payload = Map.of(
                    "type", "response.completed",
                    "response", Map.of(
                            "id", ctx.getResponseId(),
                            "object", "response",
                            "status", mapStatus(event.getFinishReason()),
                            "model", ctx.getModel()
                    )
            );
            return ServerSentEvent.<String>builder()
                    .event("response.completed")
                    .data(toJson(payload))
                    .build();
        }

        if ("text_delta".equals(event.getType())) {
            Map<String, Object> payload = Map.of(
                    "type", "response.output_text.delta",
                    "delta", event.getTextDelta() != null ? event.getTextDelta() : ""
            );
            return ServerSentEvent.<String>builder()
                    .event("response.output_text.delta")
                    .data(toJson(payload))
                    .build();
        }

        if ("tool_call".equals(event.getType())) {
            // Responses API 函数调用开始事件
            Map<String, Object> output = new java.util.LinkedHashMap<>();
            output.put("type", "function_call");
            output.put("id", event.getToolCallId());
            output.put("call_id", event.getToolCallId());
            output.put("name", event.getToolName());
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("type", "response.output_item.added");
            payload.put("output", output);
            return ServerSentEvent.<String>builder()
                    .event("response.output_item.added")
                    .data(toJson(payload))
                    .build();
        }

        if ("tool_call_delta".equals(event.getType())) {
            Map<String, Object> payload = Map.of(
                    "type", "response.function_call_arguments.delta",
                    "delta", event.getArgumentsDelta() != null ? event.getArgumentsDelta() : ""
            );
            return ServerSentEvent.<String>builder()
                    .event("response.function_call_arguments.delta")
                    .data(toJson(payload))
                    .build();
        }

        // 其他事件类型暂不处理，返回 null 跳过
        return null;
    }

    @Override
    public Flux<ServerSentEvent<String>> terminalStreamEvents(StreamContext ctx) {
        // Responses API 不像 Chat Completions 有 [DONE] 标记
        return Flux.empty();
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
            default -> "server_error";
        };
    }

    private String mapStatus(String finishReason) {
        if (finishReason == null) return "completed";
        return switch (finishReason) {
            case "stop", "tool_calls" -> "completed";
            case "length" -> "incomplete";
            default -> "completed";
        };
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize sse chunk", e);
        }
    }
}
