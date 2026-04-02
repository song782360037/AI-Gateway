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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages API 协议适配器
 * <p>
 * SSE 事件完整序列：message_start → content_block_start → content_block_delta(多个)
 * → content_block_stop → message_delta → message_stop。
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
     * 发送流起始事件（message_start）
     * <p>
     * 包含初始消息结构和 input_tokens 用量。
     * </p>
     */
    @Override
    public Flux<ServerSentEvent<String>> initialStreamEvents(StreamContext ctx) {
        // 构建 Anthropic message_start 事件
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("id", ctx.getResponseId());
        message.put("type", "message");
        message.put("role", "assistant");
        message.put("content", List.of());
        message.put("model", ctx.getModel());
        message.put("stop_reason", null);
        message.put("stop_sequence", null);

        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("input_tokens", ctx.getInputTokens());
        usage.put("output_tokens", 0);
        message.put("usage", usage);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "message_start");
        payload.put("message", message);

        return Flux.just(ServerSentEvent.<String>builder()
                .event("message_start")
                .data(toJson(payload))
                .build());
    }

    /**
     * 将 UnifiedStreamEvent 编码为 Anthropic SSE 事件流
     * <p>
     * 一个 UnifiedStreamEvent 可能产生多个 Anthropic SSE 事件：
     * <ul>
     *   <li>首个 text_delta → content_block_start + content_block_delta</li>
     *   <li>后续 text_delta → content_block_delta</li>
     *   <li>done → content_block_stop（如有打开的块）+ message_delta</li>
     * </ul>
     * </p>
     */
    @Override
    public Flux<ServerSentEvent<String>> encodeStreamEvent(UnifiedStreamEvent event, StreamContext ctx) {
        if ("done".equals(event.getType())) {
            return encodeDoneEvent(event, ctx);
        }
        if ("text_delta".equals(event.getType())) {
            return encodeTextDeltaEvent(event, ctx);
        }
        if ("thinking_delta".equals(event.getType())) {
            return encodeThinkingDeltaEvent(event, ctx);
        }
        if ("tool_call".equals(event.getType())) {
            return encodeToolCallEvent(event, ctx);
        }
        if ("tool_call_delta".equals(event.getType())) {
            return encodeToolCallDeltaEvent(event, ctx);
        }
        return Flux.empty();
    }

    /**
     * done 事件：关闭打开的 content block，然后发送 message_delta
     */
    private Flux<ServerSentEvent<String>> encodeDoneEvent(UnifiedStreamEvent event, StreamContext ctx) {
        List<ServerSentEvent<String>> events = new ArrayList<>();

        // 如果有打开的 content block，先发送 content_block_stop（使用正确的索引）
        int closedIndex = ctx.closeContentBlock();
        if (closedIndex >= 0) {
            events.add(buildContentBlockStop(closedIndex));
        }

        // message_delta 事件（stop_reason 在 delta 中，usage 在顶层）
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("stop_reason", mapStopReason(event.getFinishReason()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "message_delta");
        payload.put("delta", delta);
        if (event.getUsage() != null) {
            Map<String, Object> usage = new LinkedHashMap<>();
            usage.put("output_tokens", event.getUsage().getOutputTokens());
            payload.put("usage", usage);
        }
        events.add(ServerSentEvent.<String>builder()
                .event("message_delta")
                .data(toJson(payload))
                .build());

        return Flux.fromIterable(events);
    }

    /**
     * text_delta 事件：首个 delta 先发送 content_block_start（文本块 index=0）
     */
    private Flux<ServerSentEvent<String>> encodeTextDeltaEvent(UnifiedStreamEvent event, StreamContext ctx) {
        List<ServerSentEvent<String>> events = new ArrayList<>();

        // 首个 text_delta：先发送 content_block_start（文本块）
        if (ctx.tryMarkFirstContentSent()) {
            int blockSeq = ctx.allocateAndOpenContentBlock();
            events.add(buildTextContentBlockStart(blockSeq));
        }

        // content_block_delta：使用当前打开的块索引
        int blockSeq = ctx.getOpenBlockIndex();
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("type", "text_delta");
        delta.put("text", event.getTextDelta() != null ? event.getTextDelta() : "");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "content_block_delta");
        payload.put("index", blockSeq);
        payload.put("delta", delta);
        events.add(ServerSentEvent.<String>builder()
                .event("content_block_delta")
                .data(toJson(payload))
                .build());

        return Flux.fromIterable(events);
    }

    /**
     * thinking_delta 事件：首个思考 delta 先发送 thinking 类型的 content_block_start。
     */
    private Flux<ServerSentEvent<String>> encodeThinkingDeltaEvent(UnifiedStreamEvent event, StreamContext ctx) {
        List<ServerSentEvent<String>> events = new ArrayList<>();

        if (ctx.tryMarkFirstContentSent()) {
            int blockSeq = ctx.allocateAndOpenContentBlock();
            events.add(buildThinkingContentBlockStart(blockSeq));
        }

        int blockSeq = ctx.getOpenBlockIndex();
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("type", "thinking_delta");
        delta.put("thinking", event.getThinkingDelta() != null ? event.getThinkingDelta() : "");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "content_block_delta");
        payload.put("index", blockSeq);
        payload.put("delta", delta);
        events.add(ServerSentEvent.<String>builder()
                .event("content_block_delta")
                .data(toJson(payload))
                .build());

        return Flux.fromIterable(events);
    }

    /**
     * tool_call 开始事件：先关闭当前打开的块（使用正确索引），再发送 content_block_start（tool_use）
     */
    private Flux<ServerSentEvent<String>> encodeToolCallEvent(UnifiedStreamEvent event, StreamContext ctx) {
        List<ServerSentEvent<String>> events = new ArrayList<>();

        // 先关闭当前打开的 content block（可能是文本块或前一个工具块）
        int closedIndex = ctx.closeContentBlock();
        if (closedIndex >= 0) {
            events.add(buildContentBlockStop(closedIndex));
        }

        // 分配新的 content block 序号（独立于 Provider 的 outputIndex）
        int blockSeq = ctx.allocateAndOpenContentBlock();
        Map<String, Object> contentBlock = new LinkedHashMap<>();
        contentBlock.put("type", "tool_use");
        contentBlock.put("id", event.getToolCallId());
        contentBlock.put("name", event.getToolName());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "content_block_start");
        payload.put("index", blockSeq);
        payload.put("content_block", contentBlock);
        events.add(ServerSentEvent.<String>builder()
                .event("content_block_start")
                .data(toJson(payload))
                .build());

        return Flux.fromIterable(events);
    }

    /**
     * tool_call_delta 事件：input_json_delta
     */
    private Flux<ServerSentEvent<String>> encodeToolCallDeltaEvent(UnifiedStreamEvent event, StreamContext ctx) {
        // 使用当前打开的块索引（由 encodeToolCallEvent 分配）
        int blockSeq = ctx.getOpenBlockIndex();

        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("type", "input_json_delta");
        delta.put("partial_json", event.getArgumentsDelta() != null ? event.getArgumentsDelta() : "");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "content_block_delta");
        payload.put("index", blockSeq);
        payload.put("delta", delta);
        return Flux.just(ServerSentEvent.<String>builder()
                .event("content_block_delta")
                .data(toJson(payload))
                .build());
    }

    @Override
    public Flux<ServerSentEvent<String>> terminalStreamEvents(StreamContext ctx) {
        return Flux.just(ServerSentEvent.<String>builder()
                .event("message_stop")
                .data(toJson(Map.of("type", "message_stop")))
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

    // ===================== 辅助方法 =====================

    /** 构建文本类型的 content_block_start 事件 */
    private ServerSentEvent<String> buildTextContentBlockStart(int index) {
        Map<String, Object> contentBlock = new LinkedHashMap<>();
        contentBlock.put("type", "text");
        contentBlock.put("text", "");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "content_block_start");
        payload.put("index", index);
        payload.put("content_block", contentBlock);
        return ServerSentEvent.<String>builder()
                .event("content_block_start")
                .data(toJson(payload))
                .build();
    }

    /** 构建 thinking 类型的 content_block_start 事件 */
    private ServerSentEvent<String> buildThinkingContentBlockStart(int index) {
        Map<String, Object> contentBlock = new LinkedHashMap<>();
        contentBlock.put("type", "thinking");
        contentBlock.put("thinking", "");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "content_block_start");
        payload.put("index", index);
        payload.put("content_block", contentBlock);
        return ServerSentEvent.<String>builder()
                .event("content_block_start")
                .data(toJson(payload))
                .build();
    }

    /** 构建 content_block_stop 事件 */
    private ServerSentEvent<String> buildContentBlockStop(int index) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "content_block_stop");
        payload.put("index", index);
        return ServerSentEvent.<String>builder()
                .event("content_block_stop")
                .data(toJson(payload))
                .build();
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
