package com.code.aigateway.sdk.protocol;

import com.code.aigateway.sdk.error.ErrorCode;
import com.code.aigateway.sdk.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Anthropic Messages API 协议适配器
 * <p>
 * SSE 事件完整序列：message_start → content_block_start → content_block_delta(多个)
 * → content_block_stop → message_delta → message_stop。
 * </p>
 * <p>
 * Anthropic 协议特性：
 * <ul>
 *   <li>system 字段可以是 String 或 List（数组格式）</li>
 *   <li>assistant 消息中可能有 tool_use/thinking/redacted_thinking 内容块</li>
 *   <li>user 消息中可能有 tool_result/image 内容块</li>
 *   <li>tools 使用 input_schema 而非 parameters</li>
 *   <li>tool_choice 支持 auto/any/object 格式</li>
 *   <li>thinking 配置映射到 UnifiedReasoningConfig</li>
 * </ul>
 * </p>
 */
public class AnthropicProtocolAdapter extends AbstractProtocolAdapter {

    /** 请求解析器（包私有，负责所有 parse 相关逻辑） */
    private final AnthropicRequestParser requestParser;

    public AnthropicProtocolAdapter(ObjectMapper objectMapper) {
        super(objectMapper);
        this.requestParser = new AnthropicRequestParser(objectMapper);
    }

    @Override
    public ProtocolType getProtocolType() {
        return ProtocolType.ANTHROPIC;
    }

    @Override
    public boolean isSse() {
        return true;
    }

    // ===================== 请求解析（委托给 AnthropicRequestParser） =====================

    @Override
    public UnifiedRequest parse(Object rawRequest) {
        return requestParser.parse(rawRequest);
    }

    // ===================== 响应编码 =====================

    @Override
    @SuppressWarnings("unchecked")
    public Object encodeResponse(UnifiedResponse response) {
        Objects.requireNonNull(response, "response must not be null");

        List<Map<String, Object>> contentBlocks = new ArrayList<>();

        // thinking 必须在 text 之前（Anthropic 协议要求）
        List<UnifiedPart> thinkingParts = response.collectThinkingParts();
        for (UnifiedPart thinkingPart : thinkingParts) {
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("type", "thinking");
            block.put("thinking", thinkingPart.getText() != null ? thinkingPart.getText() : "");
            // 保留 signature 属性（扩展思维功能需要）
            if (thinkingPart.getAttributes() != null && thinkingPart.getAttributes().get("signature") != null) {
                block.put("signature", String.valueOf(thinkingPart.getAttributes().get("signature")));
            }
            contentBlocks.add(block);
        }

        // 文本内容
        String text = response.collectText();
        if (!text.isEmpty()) {
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("type", "text");
            block.put("text", text);
            contentBlocks.add(block);
        }

        // 工具调用：将 argumentsJson 解析为 Object
        List<UnifiedToolCall> toolCalls = response.collectToolCalls();
        for (UnifiedToolCall toolCall : toolCalls) {
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("type", "tool_use");
            block.put("id", toolCall.getId());
            block.put("name", toolCall.getToolName());
            block.put("input", parseArguments(toolCall.getArgumentsJson()));
            contentBlocks.add(block);
        }

        // 构建响应
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", response.getId());
        result.put("type", "message");
        result.put("role", "assistant");
        result.put("content", contentBlocks);
        result.put("model", response.getModel());
        result.put("stop_reason", mapStopReason(response.getFinishReason()));
        result.put("stop_sequence", null);

        if (response.getUsage() != null) {
            Map<String, Object> usage = new LinkedHashMap<>();
            Integer rawInput = rawInputTokens(response.getUsage());
            usage.put("input_tokens", rawInput != null ? rawInput : 0);
            usage.put("output_tokens", response.getUsage().getOutputTokens() != null ? response.getUsage().getOutputTokens() : 0);
            usage.put("cache_read_input_tokens", response.getUsage().getCachedInputTokens() != null ? response.getUsage().getCachedInputTokens() : 0);
            usage.put("cache_creation_input_tokens", response.getUsage().getCacheCreationInputTokens() != null ? response.getUsage().getCacheCreationInputTokens() : 0);
            result.put("usage", usage);
        }

        return result;
    }

    // ===================== 流式编码 =====================

    @Override
    public List<EncodedEvent> initialStreamEvents(StreamEncodeContext ctx) {
        // 延迟 message_start 的生成：等上游返回首个事件后再生成，
        // 以便在 message_start 的 usage 中包含真实的 input_tokens 和 cache_read_input_tokens
        return List.of();
    }

    @Override
    public List<EncodedEvent> encodeStreamEvent(UnifiedStreamEvent event, StreamEncodeContext ctx) {
        // 首个事件到达时，先生成 message_start（若尚未发送）
        List<EncodedEvent> events = new ArrayList<>();
        if (!ctx.isMessageStartSent()) {
            events.addAll(buildMessageStart(event.getUsage(), ctx));
            ctx.setMessageStartSent(true);
        }

        if (UnifiedStreamEvent.TYPE_USAGE_ONLY.equals(event.getType())) {
            // message_start 未发送时，仅触发 message_start
            // message_start 已发送、done 已处理但 message_delta 尚未携带真实 usage 时，
            // 补发 message_delta 携带完整 usage（解决 OpenAI Chat Completions 的 usage 延迟到达问题）
            if (ctx.isDoneProcessed() && !ctx.isOutputTokensSent() && event.getUsage() != null) {
                // delta 为空：stop_reason 已在之前的 done 事件的 message_delta 中发送过
                Map<String, Object> delta = new LinkedHashMap<>();
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("type", "message_delta");
                payload.put("delta", delta);
                payload.put("usage", buildUsageForDelta(event.getUsage(), ctx));
                events.add(EncodedEvent.named("message_delta", ctx.toJson(payload)));
                ctx.setOutputTokensSent(true);
            }
            return events;
        }
        if ("done".equals(event.getType())) {
            events.addAll(encodeDoneEvent(event, ctx));
            return events;
        }
        if ("text_delta".equals(event.getType())) {
            events.addAll(encodeTextDeltaEvent(event, ctx));
            return events;
        }
        if ("thinking_delta".equals(event.getType())) {
            events.addAll(encodeThinkingDeltaEvent(event, ctx));
            return events;
        }
        if ("tool_call".equals(event.getType())) {
            events.addAll(encodeToolCallEvent(event, ctx));
            return events;
        }
        if ("tool_call_delta".equals(event.getType())) {
            events.addAll(encodeToolCallDeltaEvent(event, ctx));
            return events;
        }
        return events;
    }

    @Override
    public List<EncodedEvent> terminalStreamEvents(StreamEncodeContext ctx) {
        return List.of(EncodedEvent.named("message_stop", ctx.toJson(Map.of("type", "message_stop"))));
    }

    // ===================== 错误编码 =====================

    @Override
    public Object buildError(String message, String errorType, String code, String param) {
        // Anthropic 格式：{type:"error", error:{type:"...", message:"..."}}
        Map<String, Object> errorDetail = new LinkedHashMap<>();
        errorDetail.put("type", errorType);
        errorDetail.put("message", message);
        return Map.of("type", "error", "error", errorDetail);
    }

    @Override
    public List<EncodedEvent> encodeStreamError(Throwable throwable, StreamEncodeContext ctx) {
        List<EncodedEvent> events = new ArrayList<>();
        // 如果 message_start 尚未发送，先发送它以确保协议事件序列完整
        if (!ctx.isMessageStartSent()) {
            events.addAll(buildMessageStart(null, ctx));
            ctx.setMessageStartSent(true);
        }
        events.addAll(super.encodeStreamError(throwable, ctx));
        return events;
    }

    @Override
    public String mapErrorType(ErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_REQUEST, MODEL_NOT_FOUND, CAPABILITY_NOT_SUPPORTED -> "invalid_request_error";
            case AUTH_FAILED, PROVIDER_AUTH_ERROR -> "authentication_error";
            case RATE_LIMITED, PROVIDER_RATE_LIMIT -> "rate_limit_error";
            case PROVIDER_BAD_REQUEST -> "invalid_request_error";
            case PROVIDER_RESOURCE_NOT_FOUND, PROVIDER_NOT_FOUND -> "not_found_error";
            case PROVIDER_TIMEOUT -> "api_error";
            case PROVIDER_CIRCUIT_OPEN, PROVIDER_DISABLED -> "api_error";
            case PROVIDER_ERROR, PROVIDER_SERVER_ERROR -> "api_error";
            default -> "api_error";
        };
    }

    // ===================== 流式编码内部方法 =====================

    /** done 事件：关闭打开的 content block，然后发送 message_delta（含完整 usage） */
    private List<EncodedEvent> encodeDoneEvent(UnifiedStreamEvent event, StreamEncodeContext ctx) {
        List<EncodedEvent> events = new ArrayList<>();

        // 如果有打开的 content block，先发送 content_block_stop
        int closedIndex = ctx.closeContentBlock();
        if (closedIndex >= 0) {
            events.add(buildContentBlockStop(closedIndex, ctx));
        }

        // message_delta 事件（stop_reason 在 delta 中，usage 在顶层）
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("stop_reason", mapStopReason(event.getFinishReason()));
        delta.put("stop_sequence", null);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "message_delta");
        payload.put("delta", delta);
        payload.put("usage", buildUsageForDelta(event.getUsage(), ctx));

        if (event.getUsage() != null && event.getUsage().getOutputTokens() != null) {
            ctx.setOutputTokensSent(true);
        }
        ctx.setDoneProcessed(true);

        events.add(EncodedEvent.named("message_delta", ctx.toJson(payload)));
        return events;
    }

    /**
     * 构建 message_delta 的 usage 对象（完整字段、累计值）。
     * <p>Anthropic 标准要求 message_delta.usage 包含 input_tokens、output_tokens、
     * cache_creation_input_tokens、cache_read_input_tokens（均为累计值）。</p>
     */
    private Map<String, Object> buildUsageForDelta(UnifiedUsage usage, StreamEncodeContext ctx) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (usage != null) {
            Integer rawInput = rawInputTokens(usage);
            result.put("input_tokens", rawInput != null ? rawInput : 0);
            result.put("output_tokens", usage.getOutputTokens() != null ? usage.getOutputTokens() : 0);
            result.put("cache_read_input_tokens", usage.getCachedInputTokens() != null ? usage.getCachedInputTokens() : 0);
            result.put("cache_creation_input_tokens", usage.getCacheCreationInputTokens() != null ? usage.getCacheCreationInputTokens() : 0);
        } else {
            // 兜底：上游未返回 usage 时输出完整字段（0 值）
            result.put("input_tokens", 0);
            result.put("output_tokens", 0);
            result.put("cache_read_input_tokens", 0);
            result.put("cache_creation_input_tokens", 0);
        }
        return result;
    }

    /** text_delta 事件：确保当前打开的是 text 块，否则关闭旧块并创建新的 text 块 */
    private List<EncodedEvent> encodeTextDeltaEvent(UnifiedStreamEvent event, StreamEncodeContext ctx) {
        List<EncodedEvent> events = new ArrayList<>();

        // 当前没有打开的 text 块，需要关闭旧块并创建新的 text 块
        if (!"text".equals(ctx.getOpenBlockType())) {
            int closedIndex = ctx.closeContentBlock();
            if (closedIndex >= 0) {
                events.add(buildContentBlockStop(closedIndex, ctx));
            }
            int blockSeq = ctx.allocateAndOpenContentBlock("text");
            events.add(buildTextContentBlockStart(blockSeq, ctx));
        }

        ctx.tryMarkFirstContentSent();

        // content_block_delta：text_delta
        int blockSeq = ctx.getOpenBlockIndex();
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("type", "text_delta");
        delta.put("text", event.getTextDelta() != null ? event.getTextDelta() : "");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "content_block_delta");
        payload.put("index", blockSeq);
        payload.put("delta", delta);

        events.add(EncodedEvent.named("content_block_delta", ctx.toJson(payload)));
        return events;
    }

    /** thinking_delta 事件：确保当前打开的是 thinking 块，否则关闭旧块并创建新的 thinking 块 */
    private List<EncodedEvent> encodeThinkingDeltaEvent(UnifiedStreamEvent event, StreamEncodeContext ctx) {
        List<EncodedEvent> events = new ArrayList<>();

        // 当前没有打开的 thinking 块，需要关闭旧块并创建新的 thinking 块
        if (!"thinking".equals(ctx.getOpenBlockType())) {
            int closedIndex = ctx.closeContentBlock();
            if (closedIndex >= 0) {
                events.add(buildContentBlockStop(closedIndex, ctx));
            }
            int blockSeq = ctx.allocateAndOpenContentBlock("thinking");
            events.add(buildThinkingContentBlockStart(blockSeq, ctx));
        }

        // content_block_delta：thinking_delta
        int blockSeq = ctx.getOpenBlockIndex();
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("type", "thinking_delta");
        delta.put("thinking", event.getThinkingDelta() != null ? event.getThinkingDelta() : "");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "content_block_delta");
        payload.put("index", blockSeq);
        payload.put("delta", delta);

        events.add(EncodedEvent.named("content_block_delta", ctx.toJson(payload)));
        return events;
    }

    /** tool_call 开始事件：先关闭当前打开的块，再发送 content_block_start（tool_use） */
    private List<EncodedEvent> encodeToolCallEvent(UnifiedStreamEvent event, StreamEncodeContext ctx) {
        List<EncodedEvent> events = new ArrayList<>();

        // 先关闭当前打开的 content block
        int closedIndex = ctx.closeContentBlock();
        if (closedIndex >= 0) {
            events.add(buildContentBlockStop(closedIndex, ctx));
        }

        // 分配新的 content block 序号
        int blockSeq = ctx.allocateAndOpenContentBlock("tool_use");

        Map<String, Object> contentBlock = new LinkedHashMap<>();
        contentBlock.put("type", "tool_use");
        contentBlock.put("id", event.getToolCallId());
        contentBlock.put("name", event.getToolName());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "content_block_start");
        payload.put("index", blockSeq);
        payload.put("content_block", contentBlock);

        events.add(EncodedEvent.named("content_block_start", ctx.toJson(payload)));
        return events;
    }

    /** tool_call_delta 事件：input_json_delta */
    private List<EncodedEvent> encodeToolCallDeltaEvent(UnifiedStreamEvent event, StreamEncodeContext ctx) {
        int blockSeq = ctx.getOpenBlockIndex();

        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("type", "input_json_delta");
        delta.put("partial_json", event.getArgumentsDelta() != null ? event.getArgumentsDelta() : "");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "content_block_delta");
        payload.put("index", blockSeq);
        payload.put("delta", delta);

        return List.of(EncodedEvent.named("content_block_delta", ctx.toJson(payload)));
    }

    // ===================== 流式编码辅助方法 =====================

    /**
     * 构建 message_start 事件。
     * <p>若首个事件携带了 usage（usage_only 事件），使用真实的 input_tokens 和 cache_read_input_tokens；
     * 否则从上下文中取值；若上下文也未设置，则不输出该字段（避免用 0 掩盖真实值）。</p>
     */
    private List<EncodedEvent> buildMessageStart(UnifiedUsage eventUsage, StreamEncodeContext ctx) {
        // 优先从事件 usage 取值，其次从上下文取值；若均未设置则保持 null，不在 message_start 中输出 0
        Integer inputTokens = eventUsage != null && eventUsage.getInputTokens() != null
                ? eventUsage.getInputTokens() : (ctx.getInputTokens() > 0 ? ctx.getInputTokens() : null);
        Integer cachedInputTokens = eventUsage != null ? eventUsage.getCachedInputTokens() : ctx.getCachedInputTokens();
        Integer cacheCreationInputTokens = eventUsage != null
                ? eventUsage.getCacheCreationInputTokens() : ctx.getCacheCreationInputTokens();

        // 将事件 usage 的值回写到 ctx，确保后续代码路径（如 encodeStreamError）能从 ctx 读取到真实值
        if (inputTokens != null && ctx.getInputTokens() <= 0) {
            ctx.setInputTokens(inputTokens);
        }
        if (cachedInputTokens != null && ctx.getCachedInputTokens() == null) {
            ctx.setCachedInputTokens(cachedInputTokens);
        }
        if (cacheCreationInputTokens != null && ctx.getCacheCreationInputTokens() == null) {
            ctx.setCacheCreationInputTokens(cacheCreationInputTokens);
        }

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("id", ctx.getResponseId());
        message.put("type", "message");
        message.put("role", "assistant");
        message.put("content", List.of());
        message.put("model", ctx.getModel());
        message.put("stop_reason", null);
        message.put("stop_sequence", null);

        Map<String, Object> usage = new LinkedHashMap<>();
        // inputTokens 已归一化（含缓存），协议输出需要还原为 Anthropic 原始值
        Integer rawInput = rawInputTokens(eventUsage, inputTokens);
        usage.put("input_tokens", rawInput != null ? rawInput : 0);
        usage.put("output_tokens", 0);
        usage.put("cache_read_input_tokens", cachedInputTokens != null ? cachedInputTokens : 0);
        usage.put("cache_creation_input_tokens", cacheCreationInputTokens != null ? cacheCreationInputTokens : 0);
        message.put("usage", usage);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "message_start");
        payload.put("message", message);

        return List.of(EncodedEvent.named("message_start", ctx.toJson(payload)));
    }

    /** 构建文本类型的 content_block_start 事件 */
    private EncodedEvent buildTextContentBlockStart(int index, StreamEncodeContext ctx) {
        Map<String, Object> contentBlock = new LinkedHashMap<>();
        contentBlock.put("type", "text");
        contentBlock.put("text", "");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "content_block_start");
        payload.put("index", index);
        payload.put("content_block", contentBlock);

        return EncodedEvent.named("content_block_start", ctx.toJson(payload));
    }

    /** 构建 thinking 类型的 content_block_start 事件 */
    private EncodedEvent buildThinkingContentBlockStart(int index, StreamEncodeContext ctx) {
        Map<String, Object> contentBlock = new LinkedHashMap<>();
        contentBlock.put("type", "thinking");
        contentBlock.put("thinking", "");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "content_block_start");
        payload.put("index", index);
        payload.put("content_block", contentBlock);

        return EncodedEvent.named("content_block_start", ctx.toJson(payload));
    }

    /** 构建 content_block_stop 事件 */
    private EncodedEvent buildContentBlockStop(int index, StreamEncodeContext ctx) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "content_block_stop");
        payload.put("index", index);
        return EncodedEvent.named("content_block_stop", ctx.toJson(payload));
    }

    // ===================== 响应编码辅助方法 =====================

    /** 映射 stop_reason：stop→end_turn, length→max_tokens, tool_calls→tool_use */
    private String mapStopReason(String finishReason) {
        if (finishReason == null) return "end_turn";
        return switch (finishReason) {
            case "stop" -> "end_turn";
            case "length" -> "max_tokens";
            case "tool_calls" -> "tool_use";
            default -> "end_turn";
        };
    }

    /**
     * 获取 Anthropic 原始 input_tokens（不含缓存部分）。
     * <p>Anthropic Provider 通过 rawInputTokens 字段传递原始值；
     * OpenAI/Gemini Provider 的 inputTokens 是总量（含缓存），
     * 需减去 cachedInputTokens + cacheCreationInputTokens 得到非缓存部分，
     * 否则 Anthropic 客户端会双重计算（total = input_tokens + cache_tokens）。</p>
     */
    private static Integer rawInputTokens(UnifiedUsage usage) {
        if (usage == null || usage.getInputTokens() == null) return null;
        if (usage.getRawInputTokens() != null) {
            return usage.getRawInputTokens();
        }
        int cached = nullToZero(usage.getCachedInputTokens()) + nullToZero(usage.getCacheCreationInputTokens());
        return cached > 0 ? Math.max(0, usage.getInputTokens() - cached) : usage.getInputTokens();
    }

    /**
     * 获取 Anthropic 原始 input_tokens（流式编码用）。
     * <p>同 {@link #rawInputTokens(UnifiedUsage)}，当 rawInputTokens 未设置时
     * 从 inputTokens 中减去缓存部分。</p>
     */
    private static Integer rawInputTokens(UnifiedUsage eventUsage, Integer inputTokens) {
        if (inputTokens == null) return null;
        if (eventUsage != null && eventUsage.getRawInputTokens() != null) {
            return eventUsage.getRawInputTokens();
        }
        if (eventUsage != null) {
            int cached = nullToZero(eventUsage.getCachedInputTokens()) + nullToZero(eventUsage.getCacheCreationInputTokens());
            if (cached > 0) {
                return Math.max(0, inputTokens - cached);
            }
        }
        return inputTokens;
    }

    private static int nullToZero(Integer value) {
        return value != null ? value : 0;
    }
}
