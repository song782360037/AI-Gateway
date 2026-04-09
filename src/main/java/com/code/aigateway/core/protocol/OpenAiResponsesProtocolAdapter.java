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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

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
    public Flux<ServerSentEvent<String>> encodeStreamEvent(UnifiedStreamEvent event, StreamContext ctx) {
        return switch (event.getType()) {
            case "done" -> encodeDoneEvent(event, ctx);
            case "text_delta" -> encodeTextDelta(event, ctx);
            case "thinking_delta" -> encodeThinkingDelta(event, ctx);
            case "tool_call" -> encodeToolCall(event, ctx);
            case "tool_call_delta" -> encodeToolCallDelta(event, ctx);
            default -> Flux.empty();
        };
    }

    /** 编码流结束事件：关闭未关闭的块，发送 response.completed */
    private Flux<ServerSentEvent<String>> encodeDoneEvent(UnifiedStreamEvent event, StreamContext ctx) {
        Flux<ServerSentEvent<String>> closeItems = Flux.empty();
        if (ctx.isTextBlockOpen()) {
            closeItems = closeItems.concatWith(Flux.just(buildOutputItemDone(ctx.getOpenAiTextOutputItemIndex())));
            ctx.closeTextBlock();
        }
        if (ctx.isReasoningBlockOpen()) {
            closeItems = closeItems.concatWith(Flux.just(buildOutputItemDone(ctx.getOpenAiReasoningOutputItemIndex())));
            ctx.closeReasoningBlock();
        }
        Map<String, Object> payload = Map.of(
                "type", "response.completed",
                "response", Map.of(
                        "id", ctx.getResponseId(),
                        "object", "response",
                        "status", mapStatus(event.getFinishReason()),
                        "model", ctx.getModel()
                )
        );
        ServerSentEvent<String> completed = ServerSentEvent.<String>builder()
                .event("response.completed")
                .data(toJson(payload))
                .build();
        return closeItems.concatWith(Flux.just(completed));
    }

    /** 块增量编码配置，封装 text/reasoning 块的差异化参数 */
    private record BlockDeltaConfig(
            String itemType,
            String deltaType,
            String deltaContent,
            String itemIdPrefix,
            Map<String, Object> extraFields,
            BooleanSupplier openBlockCas,
            IntConsumer outputIndexWriter,
            Consumer<String> itemIdWriter,
            IntSupplier outputIndexReader,
            Supplier<String> itemIdReader) {
    }

    /** 编码文本增量：首次打开时发送 output_item.added，之后发送 output_text.delta */
    private Flux<ServerSentEvent<String>> encodeTextDelta(UnifiedStreamEvent event, StreamContext ctx) {
        return encodeBlockDelta(event, ctx, new BlockDeltaConfig(
                "message", "response.output_text.delta", event.getTextDelta(),
                "msg_", Map.of("role", "assistant"),
                ctx::tryOpenTextBlock, ctx::setOpenAiTextOutputItemIndex, ctx::setOpenAiTextItemId,
                ctx::getOpenAiTextOutputItemIndex, ctx::getOpenAiTextItemId));
    }

    /** 编码思考增量：首次打开时发送 reasoning 块的 output_item.added */
    private Flux<ServerSentEvent<String>> encodeThinkingDelta(UnifiedStreamEvent event, StreamContext ctx) {
        return encodeBlockDelta(event, ctx, new BlockDeltaConfig(
                "reasoning", "response.reasoning_summary_text.delta", event.getThinkingDelta(),
                "rs_", Map.of(),
                ctx::tryOpenReasoningBlock, ctx::setOpenAiReasoningOutputItemIndex, ctx::setOpenAiReasoningItemId,
                ctx::getOpenAiReasoningOutputItemIndex, ctx::getOpenAiReasoningItemId));
    }

    /**
     * 块增量编码模板：text_delta 和 thinking_delta 共享的结构。
     * 首次收到增量时发送 output_item.added 打开块，后续发送 delta 事件。
     */
    private Flux<ServerSentEvent<String>> encodeBlockDelta(
            UnifiedStreamEvent event, StreamContext ctx, BlockDeltaConfig cfg) {

        Flux<ServerSentEvent<String>> framing = Flux.empty();
        if (cfg.openBlockCas().getAsBoolean()) {
            int outputIndex = event.getOutputIndex() != null ? event.getOutputIndex() : ctx.nextOpenAiOutputItemIndex();
            cfg.outputIndexWriter().accept(outputIndex);
            String itemId = event.getItemId() != null && !event.getItemId().isBlank()
                    ? event.getItemId()
                    : cfg.itemIdPrefix() + ctx.getResponseId() + "_" + outputIndex;
            cfg.itemIdWriter().accept(itemId);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", cfg.itemType());
            item.put("id", itemId);
            item.putAll(cfg.extraFields());
            Map<String, Object> added = new LinkedHashMap<>();
            added.put("type", "response.output_item.added");
            added.put("output_index", outputIndex);
            added.put("item", item);
            framing = Flux.just(ServerSentEvent.<String>builder()
                    .event("response.output_item.added")
                    .data(toJson(added))
                    .build());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", cfg.deltaType());
        if (event.getOutputIndex() != null) {
            payload.put("output_index", event.getOutputIndex());
        } else if (cfg.outputIndexReader().getAsInt() >= 0) {
            payload.put("output_index", cfg.outputIndexReader().getAsInt());
        }
        String resolvedItemId = event.getItemId() != null && !event.getItemId().isBlank()
                ? event.getItemId()
                : cfg.itemIdReader().get();
        if (resolvedItemId != null && !resolvedItemId.isBlank()) {
            payload.put("item_id", resolvedItemId);
        }
        payload.put("delta", cfg.deltaContent() != null ? cfg.deltaContent() : "");
        return framing.concatWith(Flux.just(ServerSentEvent.<String>builder()
                .event(cfg.deltaType())
                .data(toJson(payload))
                .build()));
    }

    /** 编码工具调用开始事件 */
    private Flux<ServerSentEvent<String>> encodeToolCall(UnifiedStreamEvent event, StreamContext ctx) {
        int outputIndex = event.getOutputIndex() != null ? event.getOutputIndex() : ctx.nextOpenAiOutputItemIndex();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "function_call");
        if (event.getItemId() != null && !event.getItemId().isBlank()) {
            item.put("id", event.getItemId());
        }
        if (event.getToolCallId() != null && !event.getToolCallId().isBlank()) {
            item.put("call_id", event.getToolCallId());
        }
        if (event.getToolName() != null && !event.getToolName().isBlank()) {
            item.put("name", event.getToolName());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "response.output_item.added");
        payload.put("output_index", outputIndex);
        payload.put("item", item);
        return Flux.just(ServerSentEvent.<String>builder()
                .event("response.output_item.added")
                .data(toJson(payload))
                .build());
    }

    /** 编码工具调用参数增量 */
    private Flux<ServerSentEvent<String>> encodeToolCallDelta(UnifiedStreamEvent event, StreamContext ctx) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "response.function_call_arguments.delta");
        if (event.getOutputIndex() != null) {
            payload.put("output_index", event.getOutputIndex());
        }
        if (event.getItemId() != null && !event.getItemId().isBlank()) {
            payload.put("item_id", event.getItemId());
        }
        if (event.getToolCallId() != null && !event.getToolCallId().isBlank()) {
            payload.put("call_id", event.getToolCallId());
        }
        payload.put("delta", event.getArgumentsDelta() != null ? event.getArgumentsDelta() : "");
        return Flux.just(ServerSentEvent.<String>builder()
                .event("response.function_call_arguments.delta")
                .data(toJson(payload))
                .build());
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

    /** 构建 response.output_item.done 事件（关闭指定 output item） */
    private ServerSentEvent<String> buildOutputItemDone(int outputIndex) {
        Map<String, Object> payload = Map.of(
                "type", "response.output_item.done",
                "output_index", outputIndex >= 0 ? outputIndex : 0
        );
        return ServerSentEvent.<String>builder()
                .event("response.output_item.done")
                .data(toJson(payload))
                .build();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize sse chunk", e);
        }
    }
}
