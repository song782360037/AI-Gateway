package com.code.aigateway.provider.anthropic;

import com.code.aigateway.config.GatewayProperties;
import com.code.aigateway.core.capability.ReasoningSemanticMapper;
import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.resilience.CircuitBreakerManager;
import com.code.aigateway.core.model.UnifiedMessage;
import com.code.aigateway.core.model.UnifiedOutput;
import com.code.aigateway.core.model.UnifiedPart;
import com.code.aigateway.core.model.UnifiedReasoningConfig;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedResponse;
import com.code.aigateway.core.model.UnifiedStreamEvent;
import com.code.aigateway.core.model.UnifiedTool;
import com.code.aigateway.core.model.UnifiedToolCall;
import com.code.aigateway.core.model.UnifiedToolChoice;
import com.code.aigateway.core.model.UnifiedUsage;
import com.code.aigateway.provider.AbstractProviderClient;
import com.code.aigateway.provider.ProviderType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Anthropic Messages API 提供商客户端
 * <p>
 * 适配 Anthropic Claude 系列模型的 Messages API（/v1/messages），
 * 支持流式/非流式、工具调用（含多轮）、错误处理与重试。
 * </p>
 *
 * @author sst
 */
@Component
@Slf4j
public class AnthropicProviderClient extends AbstractProviderClient {

    private static final String MESSAGES_PATH = "/v1/messages";
    private static final String DEFAULT_ANTHROPIC_VERSION = "2023-06-01";
    private static final int DEFAULT_MAX_TOKENS = 4096;

    private final ReasoningSemanticMapper reasoningSemanticMapper;

    public AnthropicProviderClient(WebClient.Builder webClientBuilder,
                                   ObjectMapper objectMapper,
                                   GatewayProperties gatewayProperties,
                                   CircuitBreakerManager circuitBreakerManager,
                                   ReasoningSemanticMapper reasoningSemanticMapper) {
        super(webClientBuilder, objectMapper, gatewayProperties, circuitBreakerManager);
        this.reasoningSemanticMapper = reasoningSemanticMapper;
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.ANTHROPIC;
    }

    @Override
    protected WebClient buildWebClient(ProviderRuntimeConfig config, String correlationId) {
        // Anthropic 使用 x-api-key header 而非 Bearer Token
        WebClient.Builder builder = webClientBuilder
                .baseUrl(config.baseUrl())
                .defaultHeader("x-api-key", config.apiKey())
                .defaultHeader("anthropic-version", resolveApiVersion())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (correlationId != null && !correlationId.isBlank()) {
            builder.defaultHeader("X-Correlation-Id", correlationId);
        }
        return builder.build();
    }

    @Override
    public Mono<UnifiedResponse> chat(UnifiedRequest request) {
        ProviderRuntimeConfig config = resolveRuntimeConfig(request);
        Map<String, Object> requestBody = buildRequestBody(request, false);

        Mono<JsonNode> responseMono = buildWebClient(config, extractCorrelationId(request))
                .post()
                .uri(MESSAGES_PATH)
                .body(BodyInserters.fromValue(requestBody))
                .retrieve()
                .onStatus(status -> status.isError(), response -> mapErrorResponse(response, config))
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(config.timeoutSeconds()));

        if (config.maxRetries() > 0) {
            responseMono = responseMono.retryWhen(buildRetrySpec(config));
        }

        return withCircuitBreaker(config.providerName(), request.getModel(), responseMono)
                .onErrorMap(this::mapTransportError)
                .map(this::parseResponse);
    }

    @Override
    public Flux<UnifiedStreamEvent> streamChat(UnifiedRequest request) {
        ProviderRuntimeConfig config = resolveRuntimeConfig(request);
        Map<String, Object> requestBody = buildRequestBody(request, true);
        AtomicBoolean firstTokenReceived = new AtomicBoolean(false);

        // 流式状态跟踪器：累积 tool call 参数
        StreamState state = new StreamState();

        Flux<ServerSentEvent<String>> sseFlux = buildWebClient(config, extractCorrelationId(request))
                .post()
                .uri(MESSAGES_PATH)
                .body(BodyInserters.fromValue(requestBody))
                .retrieve()
                .onStatus(status -> status.isError(), response -> mapErrorResponse(response, config))
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .timeout(Duration.ofSeconds(config.timeoutSeconds()));

        if (config.maxRetries() > 0) {
            sseFlux = sseFlux
                    .doOnNext(event -> firstTokenReceived.set(true))
                    .retryWhen(buildStreamRetrySpec(config, firstTokenReceived));
        }

        return withCircuitBreakerFlux(config.providerName(), request.getModel(), sseFlux)
                .onErrorMap(this::mapTransportError)
                .flatMap(event -> parseStreamEvent(event, state));
    }

    // ==================== 请求构建 ====================

    /**
     * 构建 Anthropic Messages API 请求体
     */
    private Map<String, Object> buildRequestBody(UnifiedRequest request, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModel());
        body.put("stream", stream);

        // max_tokens 是 Anthropic 必填字段
        int maxTokens = DEFAULT_MAX_TOKENS;
        if (request.getGenerationConfig() != null && request.getGenerationConfig().getMaxOutputTokens() != null) {
            maxTokens = request.getGenerationConfig().getMaxOutputTokens();
        }
        body.put("max_tokens", maxTokens);

        // system prompt 单独传入
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            body.put("system", request.getSystemPrompt());
        }

        // 转换消息列表（角色映射 + 连续 tool_result 合并）
        body.put("messages", buildMessages(request));

        // 生成配置
        if (request.getGenerationConfig() != null) {
            if (request.getGenerationConfig().getTemperature() != null) {
                body.put("temperature", request.getGenerationConfig().getTemperature());
            }
            if (request.getGenerationConfig().getTopP() != null) {
                body.put("top_p", request.getGenerationConfig().getTopP());
            }
            if (request.getGenerationConfig().getTopK() != null) {
                body.put("top_k", request.getGenerationConfig().getTopK());
            }
            if (request.getGenerationConfig().getStopSequences() != null && !request.getGenerationConfig().getStopSequences().isEmpty()) {
                body.put("stop_sequences", request.getGenerationConfig().getStopSequences());
            }
            if (request.getGenerationConfig().getReasoning() != null) {
                Integer budgetTokens = reasoningSemanticMapper.toAnthropicBudgetTokens(request.getGenerationConfig().getReasoning());
                if (budgetTokens != null) {
                    Map<String, Object> thinking = new LinkedHashMap<>();
                    thinking.put("type", "enabled");
                    thinking.put("budget_tokens", budgetTokens);
                    body.put("thinking", thinking);
                }
            }
        }

        // 工具定义
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            body.put("tools", buildTools(request.getTools()));
        }
        if (request.getToolChoice() != null) {
            body.put("tool_choice", buildToolChoice(request.getToolChoice()));
        }
        return body;
    }

    /**
     * 构建消息列表，处理角色映射和连续 tool_result 合并
     * <p>
     * Anthropic 要求 user/assistant 严格交替，连续的 tool_result 需合并到单条 user 消息中。
     * </p>
     */
    private List<Map<String, Object>> buildMessages(UnifiedRequest request) {
        if (request.getMessages() == null) {
            return List.of();
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        for (UnifiedMessage msg : request.getMessages()) {
            switch (msg.getRole()) {
                case "user" -> messages.add(buildUserMessage(msg));
                case "assistant" -> messages.add(buildAssistantMessage(msg));
                case "tool" -> {
                    // tool → user + tool_result content block
                    Map<String, Object> toolResult = new LinkedHashMap<>();
                    toolResult.put("type", "tool_result");
                    toolResult.put("tool_use_id", msg.getToolCallId() != null ? msg.getToolCallId() : "");
                    toolResult.put("content", extractTextContent(msg));

                    // 如果前一条也是 tool_result 合并到同一个 user 消息中
                    if (!messages.isEmpty()) {
                        Map<String, Object> last = messages.get(messages.size() - 1);
                        if ("user".equals(last.get("role")) && last.get("content") instanceof List<?> list) {
                            if (!list.isEmpty() && list.getFirst() instanceof Map<?, ?> firstBlock
                                    && "tool_result".equals(firstBlock.get("type"))) {
                                List<Object> merged = new ArrayList<>(list);
                                merged.add(toolResult);
                                last.put("content", merged);
                                continue;
                            }
                        }
                    }
                    // 新建可变的 user 消息（后续可能被合并修改）
                    Map<String, Object> userMsg = new LinkedHashMap<>();
                    userMsg.put("role", "user");
                    userMsg.put("content", new ArrayList<>(List.of(toolResult)));
                    messages.add(userMsg);
                }
                default -> log.warn("[Anthropic] 忽略不支持的角色: {}", msg.getRole());
            }
        }
        return messages;
    }

    private Map<String, Object> buildUserMessage(UnifiedMessage msg) {
        return Map.of("role", "user", "content", extractTextContent(msg));
    }

    /**
     * 构建 assistant 消息。
     * 如果包含 tool_calls，将其转换为 Anthropic 的 tool_use content block。
     */
    private Map<String, Object> buildAssistantMessage(UnifiedMessage msg) {
        List<Object> content = new ArrayList<>();

        // 文本内容
        String text = extractTextContent(msg);
        if (text != null && !text.isEmpty()) {
            content.add(Map.of("type", "text", "text", text));
        }

        // tool_calls → tool_use content blocks
        if (msg.getToolCalls() != null) {
            for (UnifiedToolCall tc : msg.getToolCalls()) {
                Map<String, Object> toolUse = new LinkedHashMap<>();
                toolUse.put("type", "tool_use");
                toolUse.put("id", tc.getId() != null ? tc.getId() : "");
                toolUse.put("name", tc.getToolName());
                // Anthropic 的 input 是 object 而非 JSON string
                toolUse.put("input", parseJsonArgs(tc.getArgumentsJson()));
                content.add(toolUse);
            }
        }

        if (content.size() == 1 && content.getFirst() instanceof Map<?, ?> map
                && "text".equals(map.get("type"))) {
            // 纯文本消息用字符串简化
            return Map.of("role", "assistant", "content", map.get("text"));
        }
        return Map.of("role", "assistant", "content", content);
    }

    /**
     * 构建工具定义（Anthropic 用 input_schema 而非 parameters）
     */
    private List<Map<String, Object>> buildTools(List<UnifiedTool> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (UnifiedTool tool : tools) {
            Map<String, Object> toolDef = new LinkedHashMap<>();
            toolDef.put("name", tool.getName());
            if (tool.getDescription() != null) {
                toolDef.put("description", tool.getDescription());
            }
            if (tool.getInputSchema() != null) {
                toolDef.put("input_schema", tool.getInputSchema());
            }
            result.add(toolDef);
        }
        return result;
    }

    /**
     * 构建 tool_choice（Anthropic 格式）
     * <p>
     * auto → {"type":"auto"}, required → {"type":"any"}, specific → {"type":"tool","name":...}
     * </p>
     */
    private Object buildToolChoice(UnifiedToolChoice choice) {
        if (choice.getType() == null) {
            return null;
        }
        return switch (choice.getType()) {
            case "auto" -> Map.of("type", "auto");
            case "required" -> Map.of("type", "any");
            case "specific" -> Map.of("type", "tool", "name", choice.getToolName());
            default -> null;
        };
    }

    // ==================== 响应解析 ====================

    /**
     * 解析非流式响应
     */
    private UnifiedResponse parseResponse(JsonNode json) {
        if ("error".equals(textOrNull(json.get("type")))) {
            String msg = json.path("error").path("message").asText("unknown error");
            throw new GatewayException(ErrorCode.PROVIDER_ERROR, "Anthropic error: " + msg);
        }

        List<UnifiedToolCall> toolCalls = new ArrayList<>();
        List<UnifiedPart> thinkingParts = new ArrayList<>();
        StringBuilder textBuilder = new StringBuilder();
        JsonNode contentArray = json.path("content");

        if (contentArray.isArray()) {
            for (JsonNode block : contentArray) {
                String blockType = block.path("type").asText();
                if ("text".equals(blockType)) {
                    textBuilder.append(block.path("text").asText());
                } else if ("thinking".equals(blockType) || "redacted_thinking".equals(blockType)) {
                    UnifiedPart thinkingPart = new UnifiedPart();
                    thinkingPart.setType("thinking");
                    thinkingPart.setText(block.has("thinking") ? block.path("thinking").asText() : block.path("text").asText());
                    Map<String, Object> attributes = new LinkedHashMap<>();
                    attributes.put("anthropic_type", blockType);
                    if (block.has("signature")) {
                        attributes.put("signature", block.path("signature").asText());
                    }
                    thinkingPart.setAttributes(attributes);
                    thinkingParts.add(thinkingPart);
                } else if ("tool_use".equals(blockType)) {
                    UnifiedToolCall call = new UnifiedToolCall();
                    call.setId(textOrNull(block.get("id")));
                    call.setType("function");
                    call.setToolName(block.path("name").asText());
                    // Anthropic input 是 object，序列化为 JSON string
                    call.setArgumentsJson(objectToString(block.get("input")));
                    toolCalls.add(call);
                }
            }
        }

        UnifiedOutput output = new UnifiedOutput();
        output.setRole("assistant");
        List<UnifiedPart> outputParts = new ArrayList<>();
        if (!textBuilder.isEmpty()) {
            UnifiedPart part = new UnifiedPart();
            part.setType("text");
            part.setText(textBuilder.toString());
            outputParts.add(part);
        }
        outputParts.addAll(thinkingParts);
        output.setParts(outputParts);
        output.setToolCalls(toolCalls.isEmpty() ? null : toolCalls);

        UnifiedResponse response = new UnifiedResponse();
        response.setId(textOrNull(json.get("id")));
        response.setModel(textOrNull(json.get("model")));
        response.setProvider("anthropic");
        response.setFinishReason(mapFinishReason(textOrNull(json.get("stop_reason"))));
        response.setUsage(parseAnthropicUsage(json.get("usage")));
        response.setOutputs(List.of(output));
        return response;
    }

    /**
     * 解析流式 SSE 事件（6 种 Anthropic event type）
     */
    private Flux<UnifiedStreamEvent> parseStreamEvent(ServerSentEvent<String> event, StreamState state) {
        String data = event.data();
        if (data == null || data.isBlank()) {
            return Flux.empty();
        }

        JsonNode json;
        try {
            json = objectMapper.readTree(data);
        } catch (JsonProcessingException e) {
            return Flux.error(new GatewayException(ErrorCode.STREAM_PARSE_ERROR, "failed to parse Anthropic stream chunk"));
        }

        String eventType = textOrNull(json.get("type"));
        if (eventType == null) {
            return Flux.empty();
        }

        return switch (eventType) {
            case "message_start" -> handleStart(json, state);
            case "content_block_start" -> handleContentBlockStart(json, state);
            case "content_block_delta" -> handleContentBlockDelta(json, state);
            case "content_block_stop" -> handleContentBlockStop(json, state);
            case "message_delta" -> handleMessageDelta(json, state);
            case "message_stop" -> Flux.empty();
            case "ping" -> Flux.empty();
            case "error" -> Flux.error(new GatewayException(ErrorCode.PROVIDER_ERROR,
                    json.path("error").path("message").asText("Anthropic stream error")));
            default -> Flux.empty();
        };
    }

    /** message_start: 捕获消息 ID 和初始 usage */
    private Flux<UnifiedStreamEvent> handleStart(JsonNode json, StreamState state) {
        JsonNode message = json.path("message");
        state.messageId = textOrNull(message.get("id"));
        state.inputTokens = message.path("usage").path("input_tokens").isMissingNode()
                ? null : message.path("usage").path("input_tokens").asInt();
        return Flux.empty();
    }

    /** content_block_start: tool_use 块开始时发射 tool_call 事件 */
    private Flux<UnifiedStreamEvent> handleContentBlockStart(JsonNode json, StreamState state) {
        int index = json.path("index").asInt();
        JsonNode block = json.path("content_block");
        String blockType = block.path("type").asText();

        if ("tool_use".equals(blockType)) {
            state.currentToolId = textOrNull(block.get("id"));
            state.currentToolName = block.path("name").asText();
            state.currentToolArgs = new StringBuilder();
            state.currentToolIndex = index;

            UnifiedStreamEvent e = new UnifiedStreamEvent();
            e.setType("tool_call");
            e.setOutputIndex(index);
            e.setToolCallId(state.currentToolId);
            e.setToolName(state.currentToolName);
            return Flux.just(e);
        }
        return Flux.empty();
    }

    /** content_block_delta: 文本增量或工具参数增量 */
    private Flux<UnifiedStreamEvent> handleContentBlockDelta(JsonNode json, StreamState state) {
        int index = json.path("index").asInt();
        JsonNode delta = json.path("delta");
        String deltaType = delta.path("type").asText();

        if ("text_delta".equals(deltaType)) {
            UnifiedStreamEvent e = new UnifiedStreamEvent();
            e.setType("text_delta");
            e.setOutputIndex(index);
            e.setTextDelta(delta.path("text").asText());
            return Flux.just(e);
        }

        if ("thinking_delta".equals(deltaType)) {
            UnifiedStreamEvent e = new UnifiedStreamEvent();
            e.setType("thinking_delta");
            e.setOutputIndex(index);
            e.setThinkingDelta(delta.path("thinking").asText());
            return Flux.just(e);
        }

        if ("input_json_delta".equals(deltaType)) {
            String partial = delta.path("partial_json").asText();
            state.currentToolArgs.append(partial);

            UnifiedStreamEvent e = new UnifiedStreamEvent();
            e.setType("tool_call_delta");
            e.setOutputIndex(index);
            e.setToolCallId(state.currentToolId);
            e.setArgumentsDelta(partial);
            return Flux.just(e);
        }
        return Flux.empty();
    }

    /** content_block_stop: tool_use 块结束时发射 tool_call_end */
    private Flux<UnifiedStreamEvent> handleContentBlockStop(JsonNode json, StreamState state) {
        int index = json.path("index").asInt();
        if (index == state.currentToolIndex && state.currentToolId != null) {
            // 重置工具状态
            state.currentToolId = null;
            state.currentToolName = null;
            state.currentToolArgs = null;
            state.currentToolIndex = -1;
        }
        return Flux.empty();
    }

    /** message_delta: 发射 done 事件（含 finish_reason 和 usage） */
    private Flux<UnifiedStreamEvent> handleMessageDelta(JsonNode json, StreamState state) {
        JsonNode delta = json.path("delta");
        JsonNode usageNode = json.path("usage");

        UnifiedUsage usage = new UnifiedUsage();
        usage.setInputTokens(state.inputTokens);
        usage.setOutputTokens(usageNode.path("output_tokens").isMissingNode()
                ? null : usageNode.path("output_tokens").asInt());
        // 计算 totalTokens（与 parseAnthropicUsage 保持一致）
        if (usage.getInputTokens() != null && usage.getOutputTokens() != null) {
            usage.setTotalTokens(usage.getInputTokens() + usage.getOutputTokens());
        }

        UnifiedStreamEvent e = new UnifiedStreamEvent();
        e.setType("done");
        e.setFinishReason(mapFinishReason(textOrNull(delta.get("stop_reason"))));
        e.setUsage(usage);
        return Flux.just(e);
    }

    // ==================== 错误处理 ====================

    /**
     * Anthropic 错误格式：{"type":"error","error":{"type":"overloaded_error","message":"..."}}
     */
    @Override
    protected String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode json = objectMapper.readTree(body);
            JsonNode msgNode = json.path("error").path("message");
            if (!msgNode.isMissingNode() && !msgNode.isNull()) {
                return msgNode.asText();
            }
        } catch (JsonProcessingException ignored) {
        }
        return "";
    }

    /**
     * 提取 Anthropic 错误类型：error.type（如 overloaded_error、invalid_request_error）
     */
    @Override
    protected String extractErrorType(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode json = objectMapper.readTree(body);
            JsonNode typeNode = json.path("error").path("type");
            if (!typeNode.isMissingNode() && !typeNode.isNull()) {
                return typeNode.asText();
            }
        } catch (JsonProcessingException ignored) {
        }
        return "";
    }

    // ==================== 工具方法 ====================

    private String resolveApiVersion() {
        // 优先从 YAML 配置读取 provider 版本
        // 如果没有配置，使用默认值
        return DEFAULT_ANTHROPIC_VERSION;
    }

    /** Anthropic stop_reason → 统一 finishReason */
    private String mapFinishReason(String stopReason) {
        if (stopReason == null) return null;
        return switch (stopReason) {
            case "end_turn" -> "stop";
            case "max_tokens" -> "length";
            case "tool_use" -> "tool_calls";
            case "stop_sequence" -> "stop";
            default -> stopReason;
        };
    }

    /** 解析 Anthropic usage（input_tokens / output_tokens） */
    private UnifiedUsage parseAnthropicUsage(JsonNode usageNode) {
        if (usageNode == null || usageNode.isNull() || usageNode.isMissingNode()) {
            return null;
        }
        UnifiedUsage usage = new UnifiedUsage();
        usage.setInputTokens(usageNode.path("input_tokens").isMissingNode() ? null : usageNode.path("input_tokens").asInt());
        usage.setOutputTokens(usageNode.path("output_tokens").isMissingNode() ? null : usageNode.path("output_tokens").asInt());
        if (usage.getInputTokens() != null && usage.getOutputTokens() != null) {
            usage.setTotalTokens(usage.getInputTokens() + usage.getOutputTokens());
        }
        return usage;
    }

    /** 从消息中提取纯文本内容 */
    private String extractTextContent(UnifiedMessage msg) {
        if (msg.getParts() == null || msg.getParts().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (UnifiedPart part : msg.getParts()) {
            if ("text".equals(part.getType()) && part.getText() != null) {
                sb.append(part.getText());
            }
        }
        return sb.toString();
    }

    /** JSON string → Object（用于 Anthropic tool_use.input） */
    private Object parseJsonArgs(String json) {
        if (json == null || json.isEmpty() || "{}".equals(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    /** JsonNode → JSON string */
    private String objectToString(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /**
     * 流式解析状态跟踪器
     */
    private static class StreamState {
        String messageId;
        Integer inputTokens;
        String currentToolId;
        String currentToolName;
        StringBuilder currentToolArgs;
        int currentToolIndex = -1;
    }
}
