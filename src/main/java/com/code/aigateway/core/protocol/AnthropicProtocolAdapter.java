package com.code.aigateway.core.protocol;

import com.code.aigateway.api.request.AnthropicMessagesRequest;
import com.code.aigateway.api.response.AnthropicErrorResponse;
import com.code.aigateway.core.encoder.AnthropicResponseEncoder;
import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.model.ResponseProtocol;
import com.code.aigateway.core.model.StreamContext;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedResponse;
import com.code.aigateway.core.model.UnifiedStreamEvent;
import com.code.aigateway.core.parser.AnthropicRequestParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Anthropic Messages API 协议适配器
 * <p>
 * SSE 事件使用 6 种命名事件：message_start, content_block_start, content_block_delta,
 * content_block_stop, message_delta, message_stop。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class AnthropicProtocolAdapter implements ProtocolAdapter {

    private final AnthropicRequestParser requestParser;
    private final AnthropicResponseEncoder responseEncoder;
    private final ObjectMapper objectMapper;

    @Override
    public ResponseProtocol getProtocol() {
        return ResponseProtocol.ANTHROPIC;
    }

    @Override
    public UnifiedRequest parse(Object rawRequest) {
        return requestParser.parse((AnthropicMessagesRequest) rawRequest);
    }

    @Override
    public Object encodeResponse(UnifiedResponse response) {
        return responseEncoder.encode(response);
    }

    /**
     * 将 UnifiedStreamEvent 编码为 Anthropic SSE 事件
     * <p>
     * 一个 UnifiedStreamEvent 可能产生多个 Anthropic SSE 事件。
     * 例如首个 text_delta 会先产生 content_block_start，然后 content_block_delta。
     * </p>
     */
    @Override
    public ServerSentEvent<String> encodeStreamEvent(UnifiedStreamEvent event, StreamContext ctx) {
        // 简化实现：将 UnifiedStreamEvent 映射为 Anthropic SSE 事件
        // 完整实现需要维护 content_block 状态机

        if ("done".equals(event.getType())) {
            // message_delta 事件（包含 stop_reason 和 usage）
            Map<String, Object> delta = new LinkedHashMap<>();
            delta.put("stop_reason", mapStopReason(event.getFinishReason()));
            if (event.getUsage() != null) {
                Map<String, Object> usage = new LinkedHashMap<>();
                usage.put("output_tokens", event.getUsage().getOutputTokens());
                delta.put("usage", usage);
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "message_delta");
            payload.put("delta", delta);
            return ServerSentEvent.<String>builder()
                    .event("message_delta")
                    .data(toJson(payload))
                    .build();
        }

        if ("text_delta".equals(event.getType())) {
            Map<String, Object> delta = new LinkedHashMap<>();
            delta.put("type", "text_delta");
            delta.put("text", event.getTextDelta() != null ? event.getTextDelta() : "");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "content_block_delta");
            payload.put("index", 0);
            payload.put("delta", delta);
            return ServerSentEvent.<String>builder()
                    .event("content_block_delta")
                    .data(toJson(payload))
                    .build();
        }

        if ("tool_call_delta".equals(event.getType())) {
            Map<String, Object> delta = new LinkedHashMap<>();
            delta.put("type", "input_json_delta");
            delta.put("partial_json", event.getArgumentsDelta() != null ? event.getArgumentsDelta() : "");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "content_block_delta");
            payload.put("index", event.getOutputIndex() != null ? event.getOutputIndex() : 0);
            payload.put("delta", delta);
            return ServerSentEvent.<String>builder()
                    .event("content_block_delta")
                    .data(toJson(payload))
                    .build();
        }

        return null;
    }

    @Override
    public Flux<ServerSentEvent<String>> terminalStreamEvents(StreamContext ctx) {
        // message_stop 终止事件
        Map<String, Object> payload = Map.of("type", "message_stop");
        return Flux.just(ServerSentEvent.<String>builder()
                .event("message_stop")
                .data(toJson(payload))
                .build());
    }

    @Override
    public boolean isSse() {
        return true;
    }

    @Override
    public Object buildError(String message, String errorType, String code, String param) {
        return new AnthropicErrorResponse("error", new AnthropicErrorResponse.ErrorDetail(errorType, message));
    }

    @Override
    public String mapErrorType(ErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_REQUEST, MODEL_NOT_FOUND, CAPABILITY_NOT_SUPPORTED -> "invalid_request_error";
            case AUTH_FAILED -> "authentication_error";
            case PROVIDER_RATE_LIMIT -> "rate_limit_error";
            default -> "api_error";
        };
    }

    private String mapStopReason(String finishReason) {
        if (finishReason == null) return "end_turn";
        return switch (finishReason) {
            case "stop" -> "end_turn";
            case "length" -> "max_tokens";
            case "tool_calls" -> "tool_use";
            default -> "end_turn";
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
