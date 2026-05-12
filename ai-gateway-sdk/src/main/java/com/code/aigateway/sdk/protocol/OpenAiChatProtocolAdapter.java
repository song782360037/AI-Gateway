package com.code.aigateway.sdk.protocol;

import com.code.aigateway.sdk.error.ErrorCode;
import com.code.aigateway.sdk.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * OpenAI Chat Completions 协议适配器
 * <p>
 * 支持 OpenAI Chat Completions API 的请求解析和响应编码。
 * 流式格式为 SSE，终止符为 [DONE]。
 * </p>
 */
public class OpenAiChatProtocolAdapter extends AbstractProtocolAdapter {

    /** 请求解析器（包私有，负责所有 parse 相关逻辑） */
    private final OpenAiChatRequestParser requestParser;

    public OpenAiChatProtocolAdapter(ObjectMapper objectMapper) {
        super(objectMapper);
        this.requestParser = new OpenAiChatRequestParser(objectMapper);
    }

    @Override
    public ProtocolType getProtocolType() {
        return ProtocolType.OPENAI_CHAT;
    }

    @Override
    public boolean isSse() {
        return true;
    }

    // ===================== 请求解析（委托给 OpenAiChatRequestParser） =====================

    @Override
    public UnifiedRequest parse(Object rawRequest) {
        return requestParser.parse(rawRequest);
    }

    // ===================== 响应编码 =====================

    @Override
    public Object encodeResponse(UnifiedResponse response) {
        Objects.requireNonNull(response, "response must not be null");

        String content = response.collectText();
        List<Map<String, Object>> toolCalls = encodeToolCalls(response.collectToolCalls());

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", content);
        if (!toolCalls.isEmpty()) {
            message.put("tool_calls", toolCalls);
        }

        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", response.getFinishReason());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", response.getId());
        result.put("object", "chat.completion");
        result.put("created", response.getCreated() != null ? response.getCreated() : System.currentTimeMillis() / 1000);
        result.put("model", response.getModel());
        result.put("choices", List.of(choice));
        if (response.getUsage() != null) {
            result.put("usage", encodeUsage(response.getUsage()));
        }
        return result;
    }

    // ===================== 流式编码 =====================

    @Override
    public List<EncodedEvent> encodeStreamEvent(UnifiedStreamEvent event, StreamEncodeContext ctx) {
        if ("done".equals(event.getType())) {
            return encodeDoneEvent(event, ctx);
        }
        if ("tool_call".equals(event.getType())) {
            return encodeToolCallEvent(event, ctx);
        }
        if ("tool_call_delta".equals(event.getType())) {
            return encodeToolCallDeltaEvent(event, ctx);
        }
        if ("thinking_delta".equals(event.getType())) {
            // OpenAI Chat 不支持 thinking，静默丢弃
            return List.of();
        }
        if ("text_delta".equals(event.getType())) {
            return encodeTextDeltaEvent(event, ctx);
        }
        return List.of();
    }

    @Override
    public List<EncodedEvent> terminalStreamEvents(StreamEncodeContext ctx) {
        return List.of(EncodedEvent.data("[DONE]"));
    }

    @Override
    public Object buildError(String message, String errorType, String code, String param) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("message", message);
        error.put("type", errorType);
        error.put("code", code);
        if (param != null) {
            error.put("param", param);
        }
        return Map.of("error", error);
    }

    @Override
    public String mapErrorType(ErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_REQUEST, MODEL_NOT_FOUND, CAPABILITY_NOT_SUPPORTED -> "invalid_request_error";
            case AUTH_FAILED, PROVIDER_AUTH_ERROR -> "authentication_error";
            case RATE_LIMITED, PROVIDER_RATE_LIMIT -> "rate_limit_error";
            case PROVIDER_BAD_REQUEST -> "invalid_request_error";
            case PROVIDER_RESOURCE_NOT_FOUND, PROVIDER_NOT_FOUND -> "invalid_request_error";
            case PROVIDER_TIMEOUT -> "server_error";
            case PROVIDER_CIRCUIT_OPEN, PROVIDER_DISABLED -> "server_error";
            case PROVIDER_ERROR, PROVIDER_SERVER_ERROR -> "server_error";
            default -> "server_error";
        };
    }

    // ===================== 流式编码内部方法 =====================

    private List<EncodedEvent> encodeTextDeltaEvent(UnifiedStreamEvent event, StreamEncodeContext ctx) {
        Map<String, Object> delta = new LinkedHashMap<>();
        if (ctx.tryMarkFirstContentSent()) {
            delta.put("role", "assistant");
        }
        delta.put("content", event.getTextDelta() != null ? event.getTextDelta() : "");
        return List.of(EncodedEvent.data(ctx.toJson(buildChunk(delta, null, event.getOutputIndex(), ctx))));
    }

    private List<EncodedEvent> encodeDoneEvent(UnifiedStreamEvent event, StreamEncodeContext ctx) {
        Map<String, Object> delta = new LinkedHashMap<>();
        Map<String, Object> chunk = buildChunk(delta,
                event.getFinishReason() == null ? "stop" : event.getFinishReason(),
                event.getOutputIndex(), ctx);
        // 若 done 事件携带 usage，编码到最终 chunk 中（支持 stream_options.include_usage 场景）
        if (event.getUsage() != null) {
            chunk.put("usage", encodeUsage(event.getUsage()));
        }
        return List.of(EncodedEvent.data(ctx.toJson(chunk)));
    }

    private List<EncodedEvent> encodeToolCallEvent(UnifiedStreamEvent event, StreamEncodeContext ctx) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", event.getToolName() != null ? event.getToolName() : "");
        function.put("arguments", "");

        Map<String, Object> tc = new LinkedHashMap<>();
        tc.put("index", event.getOutputIndex() != null ? event.getOutputIndex() : 0);
        tc.put("id", event.getToolCallId());
        tc.put("type", "function");
        tc.put("function", function);

        Map<String, Object> delta = new LinkedHashMap<>();
        if (ctx.tryMarkFirstContentSent()) {
            delta.put("role", "assistant");
        }
        delta.put("tool_calls", List.of(tc));

        return List.of(EncodedEvent.data(ctx.toJson(buildChunk(delta, null, event.getOutputIndex(), ctx))));
    }

    private List<EncodedEvent> encodeToolCallDeltaEvent(UnifiedStreamEvent event, StreamEncodeContext ctx) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("arguments", event.getArgumentsDelta() != null ? event.getArgumentsDelta() : "");

        Map<String, Object> tc = new LinkedHashMap<>();
        tc.put("index", event.getOutputIndex() != null ? event.getOutputIndex() : 0);
        tc.put("function", function);

        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("tool_calls", List.of(tc));

        return List.of(EncodedEvent.data(ctx.toJson(buildChunk(delta, null, event.getOutputIndex(), ctx))));
    }

    /** 构建 SSE chunk 结构 */
    private Map<String, Object> buildChunk(Map<String, Object> delta, String finishReason, Integer outputIndex, StreamEncodeContext ctx) {
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", outputIndex != null ? outputIndex : 0);
        choice.put("delta", delta);
        if (finishReason != null) {
            choice.put("finish_reason", finishReason);
        }

        Map<String, Object> chunk = new LinkedHashMap<>();
        chunk.put("id", ctx.getResponseId());
        chunk.put("object", "chat.completion.chunk");
        chunk.put("created", ctx.getCreated());
        chunk.put("model", ctx.getModel());
        chunk.put("choices", List.of(choice));
        return chunk;
    }

    // ===================== 响应编码辅助方法 =====================

    private List<Map<String, Object>> encodeToolCalls(List<UnifiedToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) return List.of();
        return toolCalls.stream().map(tc -> {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tc.getToolName());
            function.put("arguments", tc.getArgumentsJson() != null ? tc.getArgumentsJson() : "{}");

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", tc.getId());
            item.put("type", tc.getType() != null ? tc.getType() : "function");
            item.put("function", function);
            return item;
        }).toList();
    }

    private Map<String, Object> encodeUsage(UnifiedUsage usage) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("prompt_tokens", usage.getInputTokens());
        result.put("completion_tokens", usage.getOutputTokens());
        result.put("total_tokens", usage.getTotalTokens());
        // 编码缓存命中 Token：OpenAI Chat Completions 格式为 prompt_tokens_details.cached_tokens
        if (usage.getCachedInputTokens() != null) {
            result.put("prompt_tokens_details", Map.of("cached_tokens", usage.getCachedInputTokens()));
        }
        return result;
    }
}
