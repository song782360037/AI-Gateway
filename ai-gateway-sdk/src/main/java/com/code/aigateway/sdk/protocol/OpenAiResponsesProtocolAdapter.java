package com.code.aigateway.sdk.protocol;

import com.code.aigateway.sdk.error.ErrorCode;
import com.code.aigateway.sdk.error.ProtocolException;
import com.code.aigateway.sdk.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * OpenAI Responses API 协议适配器
 * <p>
 * OpenAI Responses 协议特性：
 * <ul>
 *   <li>SSE 使用点分命名事件（response.output_text.delta 等）</li>
 *   <li>无 [DONE] 终止符</li>
 *   <li>请求格式：instructions → systemPrompt, input[] → messages</li>
 *   <li>支持三种 input item 类型：message / function_call / function_call_output</li>
 *   <li>工具定义支持嵌套（tools[].function）和扁平格式</li>
 *   <li>reasoning 配置映射到 UnifiedReasoningConfig</li>
 * </ul>
 * </p>
 */
public class OpenAiResponsesProtocolAdapter extends AbstractProtocolAdapter {

    public OpenAiResponsesProtocolAdapter(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public ProtocolType getProtocolType() {
        return ProtocolType.OPENAI_RESPONSES;
    }

    @Override
    public boolean isSse() {
        return true;
    }

    // ===================== 请求解析 =====================

    @Override
    @SuppressWarnings("unchecked")
    public UnifiedRequest parse(Object rawRequest) {
        Objects.requireNonNull(rawRequest, "rawRequest must not be null");
        Map<String, Object> req = toMap(rawRequest, "rawRequest");

        UnifiedRequest unified = new UnifiedRequest();
        unified.setRequestProtocol("openai-responses");
        unified.setResponseProtocol("openai-responses");
        unified.setModel(requireString(req, "model", "model is required"));
        unified.setStream(Boolean.TRUE.equals(req.get("stream")));
        unified.setMetadata((Map<String, Object>) req.get("metadata"));

        // instructions → systemPrompt
        if (req.get("instructions") instanceof String instructions && !instructions.isBlank()) {
            unified.setSystemPrompt(instructions);
        }

        // 生成配置
        unified.setGenerationConfig(parseGenerationConfig(req));

        // 解析输入消息
        unified.setMessages(parseInput(req.get("input")));

        // 解析工具
        List<UnifiedTool> tools = parseTools(req.get("tools"));
        unified.setTools(tools);
        unified.setToolChoice(parseToolChoice(req.get("tool_choice")));

        return unified;
    }

    // ===================== 响应编码 =====================

    @Override
    @SuppressWarnings("unchecked")
    public Object encodeResponse(UnifiedResponse response) {
        Objects.requireNonNull(response, "response must not be null");

        List<Map<String, Object>> outputItems = new ArrayList<>();

        // thinking 编码为 reasoning output item（summary_text）
        List<UnifiedPart> thinkingParts = response.collectThinkingParts();
        if (!thinkingParts.isEmpty()) {
            Map<String, Object> reasoningItem = new LinkedHashMap<>();
            reasoningItem.put("type", "reasoning");
            reasoningItem.put("id", "rs_" + response.getId());

            List<Map<String, Object>> summaries = new ArrayList<>();
            for (UnifiedPart tp : thinkingParts) {
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("type", "summary_text");
                summary.put("text", tp.getText() != null ? tp.getText() : "");
                summaries.add(summary);
            }
            reasoningItem.put("summary", summaries);
            outputItems.add(reasoningItem);
        }

        // 文本内容编码为 output_text item
        String text = response.collectText();
        if (text != null && !text.isEmpty()) {
            Map<String, Object> messageItem = new LinkedHashMap<>();
            messageItem.put("type", "message");
            messageItem.put("id", "msg_" + response.getId());
            messageItem.put("role", "assistant");
            messageItem.put("status", "completed");

            Map<String, Object> content = new LinkedHashMap<>();
            content.put("type", "output_text");
            content.put("text", text);
            messageItem.put("content", List.of(content));

            outputItems.add(messageItem);
        }

        // 工具调用编码为 function_call item
        List<UnifiedToolCall> toolCalls = response.collectToolCalls();
        for (UnifiedToolCall tc : toolCalls) {
            Map<String, Object> fcItem = new LinkedHashMap<>();
            fcItem.put("type", "function_call");
            fcItem.put("id", tc.getId());
            fcItem.put("call_id", tc.getId());
            fcItem.put("name", tc.getToolName());
            fcItem.put("arguments", tc.getArgumentsJson() != null ? tc.getArgumentsJson() : "{}");
            fcItem.put("status", "completed");
            outputItems.add(fcItem);
        }

        // 构建响应
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", response.getId());
        result.put("object", "response");
        result.put("created_at", response.getCreated() != null ? response.getCreated() : System.currentTimeMillis() / 1000);
        result.put("model", response.getModel());
        result.put("status", "completed");
        result.put("output", outputItems);

        if (response.getUsage() != null) {
            result.put("usage", encodeUsage(response.getUsage()));
        }

        return result;
    }

    // ===================== 流式编码 =====================

    @Override
    public List<EncodedEvent> encodeStreamEvent(UnifiedStreamEvent event, StreamEncodeContext ctx) {
        if ("done".equals(event.getType())) {
            return encodeDoneEvent(ctx);
        }
        if ("text_delta".equals(event.getType())) {
            return encodeTextDeltaEvent(event, ctx);
        }
        if ("thinking_delta".equals(event.getType())) {
            return encodeThinkingDeltaEvent(event, ctx);
        }
        if ("tool_call".equals(event.getType())) {
            return encodeToolCallStreamEvent(event, ctx);
        }
        if ("tool_call_delta".equals(event.getType())) {
            return encodeToolCallDeltaStreamEvent(event, ctx);
        }
        return List.of();
    }

    @Override
    public List<EncodedEvent> terminalStreamEvents(StreamEncodeContext ctx) {
        // OpenAI Responses 无终止事件
        return List.of();
    }

    // ===================== 错误编码 =====================

    @Override
    public Object buildError(String message, String errorType, String code, String param) {
        // OpenAI 格式：{error:{message, type, code, param}}
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

    /** text_delta：首次发送 response.output_item.added(message 类型)，之后 response.output_text.delta */
    private List<EncodedEvent> encodeTextDeltaEvent(UnifiedStreamEvent event, StreamEncodeContext ctx) {
        List<EncodedEvent> events = new ArrayList<>();
        StreamEncodeContext.ResponsesStreamState state = ctx.responses();

        // 首次文本输出：发送 output_item.added（嵌套 item 结构）
        if (state.tryOpenTextBlock()) {
            int idx = state.nextOutputItemIndex();
            String itemId = "msg_" + ctx.getResponseId();
            state.setTextOutputItemIndex(idx);
            state.setTextItemId(itemId);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "message");
            item.put("id", itemId);
            item.put("role", "assistant");
            item.put("status", "in_progress");
            item.put("content", List.of());

            events.add(EncodedEvent.named("response.output_item.added",
                    ctx.toJson(buildItemAddedPayload("response.output_item.added", item, idx))));
        }

        // 文本增量（使用 delta 字段 + item_id，与旧格式一致）
        int outputIndex = state.getTextOutputItemIndex();
        String itemId = state.getTextItemId();

        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("type", "response.output_text.delta");
        delta.put("output_index", outputIndex);
        delta.put("item_id", itemId);
        delta.put("delta", event.getTextDelta() != null ? event.getTextDelta() : "");

        events.add(EncodedEvent.named("response.output_text.delta", ctx.toJson(delta)));

        return events;
    }

    /** thinking_delta：response.output_item.added(reasoning 类型) + response.reasoning_summary_text.delta */
    private List<EncodedEvent> encodeThinkingDeltaEvent(UnifiedStreamEvent event, StreamEncodeContext ctx) {
        List<EncodedEvent> events = new ArrayList<>();
        StreamEncodeContext.ResponsesStreamState state = ctx.responses();

        // 首次 reasoning 输出
        if (state.tryOpenReasoningBlock()) {
            int idx = state.nextOutputItemIndex();
            String itemId = "rs_" + ctx.getResponseId();
            state.setReasoningOutputItemIndex(idx);
            state.setReasoningItemId(itemId);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "reasoning");
            item.put("id", itemId);
            item.put("status", "in_progress");
            item.put("summary", List.of());

            events.add(EncodedEvent.named("response.output_item.added",
                    ctx.toJson(buildItemAddedPayload("response.output_item.added", item, idx))));
        }

        // reasoning 文本增量
        int outputIndex = state.getReasoningOutputItemIndex();
        String itemId = state.getReasoningItemId();

        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("type", "response.reasoning_summary_text.delta");
        delta.put("output_index", outputIndex);
        delta.put("item_id", itemId);
        delta.put("delta", event.getThinkingDelta() != null ? event.getThinkingDelta() : "");

        events.add(EncodedEvent.named("response.reasoning_summary_text.delta", ctx.toJson(delta)));
        return events;
    }

    /** tool_call：response.output_item.added(function_call 类型) */
    private List<EncodedEvent> encodeToolCallStreamEvent(UnifiedStreamEvent event, StreamEncodeContext ctx) {
        // 关闭可能打开的 text/reasoning 块
        List<EncodedEvent> events = closeOpenBlocks(ctx);

        StreamEncodeContext.ResponsesStreamState state = ctx.responses();
        int idx = state.nextOutputItemIndex();

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "function_call");
        item.put("id", event.getToolCallId());
        item.put("call_id", event.getToolCallId());
        item.put("name", event.getToolName() != null ? event.getToolName() : "");
        item.put("arguments", "");
        item.put("status", "in_progress");

        events.add(EncodedEvent.named("response.output_item.added",
                ctx.toJson(buildItemAddedPayload("response.output_item.added", item, idx))));

        return events;
    }

    /** tool_call_delta：response.function_call_arguments.delta */
    private List<EncodedEvent> encodeToolCallDeltaStreamEvent(UnifiedStreamEvent event, StreamEncodeContext ctx) {
        StreamEncodeContext.ResponsesStreamState state = ctx.responses();
        int idx = state.getLastOutputItemIndex();

        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("type", "response.function_call_arguments.delta");
        delta.put("output_index", idx);
        if (event.getItemId() != null && !event.getItemId().isBlank()) {
            delta.put("item_id", event.getItemId());
        }
        if (event.getToolCallId() != null && !event.getToolCallId().isBlank()) {
            delta.put("call_id", event.getToolCallId());
        }
        delta.put("delta", event.getArgumentsDelta() != null ? event.getArgumentsDelta() : "");

        return List.of(EncodedEvent.named("response.function_call_arguments.delta", ctx.toJson(delta)));
    }

    /** done：关闭未关闭的 text/reasoning 块 + response.completed */
    private List<EncodedEvent> encodeDoneEvent(StreamEncodeContext ctx) {
        List<EncodedEvent> events = closeOpenBlocks(ctx);

        // response.completed 事件
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", ctx.getResponseId());
        response.put("object", "response");
        response.put("status", "completed");
        response.put("model", ctx.getModel());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "response.completed");
        payload.put("response", response);

        events.add(EncodedEvent.named("response.completed", ctx.toJson(payload)));
        return events;
    }

    /** 关闭未关闭的 text/reasoning 块 */
    private List<EncodedEvent> closeOpenBlocks(StreamEncodeContext ctx) {
        List<EncodedEvent> events = new ArrayList<>();
        StreamEncodeContext.ResponsesStreamState state = ctx.responses();

        // 关闭 text 块
        if (state.isTextBlockOpen()) {
            int idx = state.getTextOutputItemIndex();
            String itemId = state.getTextItemId();
            state.closeTextBlock();
            events.add(buildOutputItemDone(idx, itemId, ctx));
        }

        // 关闭 reasoning 块
        if (state.isReasoningBlockOpen()) {
            int idx = state.getReasoningOutputItemIndex();
            String itemId = state.getReasoningItemId();
            state.closeReasoningBlock();
            events.add(buildOutputItemDone(idx, itemId, ctx));
        }

        return events;
    }

    /** 构建 response.output_item.done 事件 */
    private EncodedEvent buildOutputItemDone(int index, String itemId, StreamEncodeContext ctx) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "response.output_item.done");
        payload.put("output_index", index >= 0 ? index : 0);
        return EncodedEvent.named("response.output_item.done", ctx.toJson(payload));
    }

    /** 构建 output_item.added 的嵌套 payload */
    private Map<String, Object> buildItemAddedPayload(String type, Map<String, Object> item, int outputIndex) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("output_index", outputIndex);
        payload.put("item", item);
        return payload;
    }

    /** 覆盖默认的 encodeStreamError，使用命名事件 "error" */
    @Override
    public List<EncodedEvent> encodeStreamError(Throwable throwable, StreamEncodeContext context) {
        ErrorCode errorCode = throwable instanceof ProtocolException pe
                ? pe.getErrorCode()
                : ErrorCode.INTERNAL_ERROR;
        String message = throwable.getMessage() == null || throwable.getMessage().isBlank()
                ? "internal server error"
                : throwable.getMessage();
        String param = throwable instanceof ProtocolException pe ? pe.getParam() : null;
        Object errorBody = buildError(message, mapErrorType(errorCode), errorCode.name(), param);
        return List.of(EncodedEvent.named("error", context.toJson(errorBody)));
    }

    // ===================== 请求解析内部方法 =====================

    /** 解析 input 为消息列表 */
    @SuppressWarnings("unchecked")
    private List<UnifiedMessage> parseInput(Object input) {
        if (input == null) return List.of();

        // input 是字符串时作为单条 user 消息
        if (input instanceof String str) {
            UnifiedMessage msg = new UnifiedMessage();
            msg.setRole("user");
            msg.setParts(List.of(textPart(str)));
            return List.of(msg);
        }

        // input 是数组时，逐个解析 item
        if (input instanceof List<?> list) {
            List<UnifiedMessage> result = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                String paramPath = "input[" + i + "]";

                if (item instanceof String str) {
                    UnifiedMessage msg = new UnifiedMessage();
                    msg.setRole("user");
                    msg.setParts(List.of(textPart(str)));
                    result.add(msg);
                } else if (item instanceof Map<?, ?> map) {
                    parseInputItem(toStringMap(map), result, paramPath);
                }
            }
            return result;
        }

        return List.of();
    }

    /** 解析单个 input item */
    @SuppressWarnings("unchecked")
    private void parseInputItem(Map<String, Object> item, List<UnifiedMessage> result, String paramPath) {
        String type = (String) item.get("type");

        // message 类型：role + content
        if ("message".equals(type)) {
            String role = (String) item.get("role");
            if (role == null) return;

            UnifiedMessage msg = new UnifiedMessage();
            msg.setRole(role);
            msg.setParts(parseMessageContent(item.get("content"), paramPath + ".content"));

            // assistant 消息中的 tool_calls
            if ("assistant".equals(role) && item.get("tool_calls") instanceof List<?> tcList) {
                msg.setToolCalls(parseAssistantToolCalls(tcList, paramPath + ".tool_calls"));
            }
            result.add(msg);
        }
        // function_call 类型：assistant 的工具调用
        else if ("function_call".equals(type)) {
            UnifiedToolCall call = new UnifiedToolCall();
            call.setId((String) item.get("call_id"));
            call.setType("function");
            call.setToolName((String) item.get("name"));
            call.setArgumentsJson((String) item.get("arguments"));

            UnifiedMessage msg = new UnifiedMessage();
            msg.setRole("assistant");
            msg.setToolCalls(List.of(call));
            msg.setParts(List.of());
            result.add(msg);
        }
        // function_call_output 类型：工具返回结果
        else if ("function_call_output".equals(type)) {
            UnifiedMessage msg = new UnifiedMessage();
            msg.setRole("tool");
            msg.setToolCallId((String) item.get("call_id"));
            msg.setToolName(null);

            String output = (String) item.get("output");
            msg.setParts(List.of(textPart(output != null ? output : "")));
            result.add(msg);
        }
    }

    /** 解析 message content */
    @SuppressWarnings("unchecked")
    private List<UnifiedPart> parseMessageContent(Object content, String paramPath) {
        if (content == null) return List.of();
        if (content instanceof String str) {
            return List.of(textPart(str));
        }
        if (content instanceof List<?> list) {
            List<UnifiedPart> parts = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) instanceof Map<?, ?> map) {
                    parts.add(parseContentPart(toStringMap(map), paramPath + "[" + i + "]"));
                }
            }
            return parts;
        }
        return List.of();
    }

    /** 解析单个 content part */
    @SuppressWarnings("unchecked")
    private UnifiedPart parseContentPart(Map<String, Object> map, String paramPath) {
        String type = (String) map.get("type");

        // 文本类型：input_text 或 text
        if ("input_text".equals(type) || "text".equals(type)) {
            return textPart((String) map.get("text"));
        }

        // 图片 URL 类型
        if ("input_image".equals(type) || "image_url".equals(type)) {
            String url = (String) map.get("image_url");
            if (url == null) url = (String) map.get("url");
            return ProtocolUtils.parseDataUri(url);
        }

        return textPart("");
    }

    /** 解析 assistant 消息中的 tool_calls */
    @SuppressWarnings("unchecked")
    private List<UnifiedToolCall> parseAssistantToolCalls(List<?> tcList, String paramPath) {
        List<UnifiedToolCall> result = new ArrayList<>();
        for (Object item : tcList) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            Map<String, Object> tc = toStringMap(raw);
            String tcType = (String) tc.get("type");
            if (!"function_call".equals(tcType)) continue;

            UnifiedToolCall call = new UnifiedToolCall();
            call.setId((String) tc.get("id"));
            call.setType("function");
            call.setToolName((String) tc.get("name"));
            call.setArgumentsJson((String) tc.get("arguments"));
            result.add(call);
        }
        return result.isEmpty() ? null : result;
    }

    /** 解析工具定义（支持嵌套和扁平两种格式） */
    @SuppressWarnings("unchecked")
    private List<UnifiedTool> parseTools(Object toolsObj) {
        if (!(toolsObj instanceof List<?> list)) return List.of();
        List<UnifiedTool> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            Map<String, Object> tool = toStringMap(raw);
            String toolType = (String) tool.get("type");

            // 嵌套格式：{type:"function", function:{name, description, parameters}}
            if ("function".equals(toolType) && tool.get("function") instanceof Map<?, ?> funcRaw) {
                Map<String, Object> function = toStringMap(funcRaw);
                String name = (String) function.get("name");
                if (name == null || name.isBlank()) continue;

                UnifiedTool t = new UnifiedTool();
                t.setName(name);
                t.setDescription((String) function.get("description"));
                t.setType("function");
                t.setStrict((Boolean) function.get("strict"));
                t.setInputSchema((Map<String, Object>) function.get("parameters"));
                result.add(t);
            }
            // 扁平格式：{type:"function", name, description, parameters}
            else if ("function".equals(toolType) && tool.get("name") instanceof String name && !name.isBlank()) {
                UnifiedTool t = new UnifiedTool();
                t.setName(name);
                t.setDescription((String) tool.get("description"));
                t.setType("function");
                t.setStrict((Boolean) tool.get("strict"));
                t.setInputSchema((Map<String, Object>) tool.get("parameters"));
                result.add(t);
            }
        }
        return result;
    }

    /** 解析工具选择 */
    private UnifiedToolChoice parseToolChoice(Object toolChoiceObj) {
        if (toolChoiceObj == null) return null;
        if (toolChoiceObj instanceof String str) {
            if (!Set.of("auto", "none", "required").contains(str)) return null;
            UnifiedToolChoice choice = new UnifiedToolChoice();
            choice.setType(str);
            return choice;
        }
        return null;
    }

    /** 解析生成配置（含 reasoning 配置映射） */
    @SuppressWarnings("unchecked")
    private UnifiedGenerationConfig parseGenerationConfig(Map<String, Object> req) {
        UnifiedGenerationConfig config = new UnifiedGenerationConfig();
        config.setTemperature(req.get("temperature") instanceof Number n ? n.doubleValue() : null);
        config.setTopP(req.get("top_p") instanceof Number n ? n.doubleValue() : null);
        config.setMaxOutputTokens(req.get("max_output_tokens") instanceof Number n ? n.intValue() : null);

        // reasoning 配置
        Object reasoningObj = req.get("reasoning");
        if (reasoningObj instanceof Map<?, ?> reasoningMap) {
            UnifiedReasoningConfig reasoning = new UnifiedReasoningConfig();
            String effort = (String) ((Map<?, ?>) reasoningMap).get("effort");
            reasoning.setEnabled(effort != null);
            reasoning.setEffort(effort);
            reasoning.setSummary((String) ((Map<?, ?>) reasoningMap).get("summary"));
            config.setReasoning(reasoning);
        }

        return config;
    }

    // ===================== 响应编码辅助方法 =====================

    /** 编码 usage */
    private Map<String, Object> encodeUsage(UnifiedUsage usage) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("input_tokens", usage.getInputTokens() != null ? usage.getInputTokens() : 0);
        result.put("output_tokens", usage.getOutputTokens() != null ? usage.getOutputTokens() : 0);
        result.put("total_tokens", usage.getTotalTokens() != null ? usage.getTotalTokens() : 0);
        if (usage.getCachedInputTokens() != null && usage.getCachedInputTokens() > 0) {
            result.put("input_tokens_details", Map.of("cached_tokens", usage.getCachedInputTokens()));
        }
        return result;
    }
}
