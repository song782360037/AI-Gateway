package com.code.aigateway.provider.openai;

import com.code.aigateway.config.GatewayProperties;
import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.capability.ReasoningSemanticMapper;
import com.code.aigateway.core.resilience.CircuitBreakerManager;
import com.code.aigateway.core.model.UnifiedMessage;
import com.code.aigateway.core.model.UnifiedOutput;
import com.code.aigateway.core.model.UnifiedPart;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedResponse;
import com.code.aigateway.core.model.UnifiedStreamEvent;
import com.code.aigateway.core.model.UnifiedReasoningConfig;
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
 * OpenAI Responses API 提供商客户端
 * <p>
 * 适配 OpenAI 新格式 /v1/responses 端点，
 * 支持流式/非流式、工具调用（含多轮）、错误处理与重试。
 * </p>
 * <p>
 * 关键差异（与 Chat Completions 相比）：
 * <ul>
 *   <li>messages → input（不同的消息结构）</li>
 *   <li>system prompt → instructions 字段</li>
 *   <li>function_call 用 call_id 而非 tool_call_id</li>
 *   <li>流式 event type 用点分命名如 response.output_text.delta</li>
 *   <li>max_tokens → max_output_tokens</li>
 * </ul>
 * </p>
 *
 * @author sst
 */
@Component
@Slf4j
public class OpenAiResponsesProviderClient extends AbstractProviderClient {

    private static final String RESPONSES_PATH = "/v1/responses";

    private final ReasoningSemanticMapper reasoningSemanticMapper;

    public OpenAiResponsesProviderClient(WebClient.Builder webClientBuilder,
                                         ObjectMapper objectMapper,
                                         GatewayProperties gatewayProperties,
                                         CircuitBreakerManager circuitBreakerManager,
                                         ReasoningSemanticMapper reasoningSemanticMapper) {
        super(webClientBuilder, objectMapper, gatewayProperties, circuitBreakerManager);
        this.reasoningSemanticMapper = reasoningSemanticMapper;
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.OPENAI_RESPONSES;
    }

    @Override
    public Mono<UnifiedResponse> chat(UnifiedRequest request) {
        ProviderRuntimeConfig config = resolveRuntimeConfig(request);
        Map<String, Object> requestBody = buildRequestBody(request, false);

        Mono<JsonNode> responseMono = buildWebClient(config, extractCorrelationId(request))
                .post()
                .uri(RESPONSES_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(requestBody))
                .retrieve()
                .onStatus(status -> status.isError(), response -> mapErrorResponse(response, config))
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(config.timeoutSeconds()));

        if (config.maxRetries() > 0) {
            responseMono = responseMono.retryWhen(buildRetrySpec(config));
        }

        return withCircuitBreaker(config.providerName(), responseMono)
                .onErrorMap(this::mapTransportError)
                .map(this::parseResponse);
    }

    @Override
    public Flux<UnifiedStreamEvent> streamChat(UnifiedRequest request) {
        ProviderRuntimeConfig config = resolveRuntimeConfig(request);
        Map<String, Object> requestBody = buildRequestBody(request, true);
        AtomicBoolean firstTokenReceived = new AtomicBoolean(false);
        StreamState state = new StreamState();

        Flux<ServerSentEvent<String>> sseFlux = buildWebClient(config, extractCorrelationId(request))
                .post()
                .uri(RESPONSES_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
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

        return withCircuitBreakerFlux(config.providerName(), sseFlux)
                .onErrorMap(this::mapTransportError)
                .flatMap(event -> parseStreamEvent(event, state));
    }

    // ==================== 请求构建 ====================

    private Map<String, Object> buildRequestBody(UnifiedRequest request, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModel());
        body.put("stream", stream);

        // system prompt → instructions
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            body.put("instructions", request.getSystemPrompt());
        }

        // messages → input
        body.put("input", buildInput(request));

        // 生成配置
        if (request.getGenerationConfig() != null) {
            if (request.getGenerationConfig().getTemperature() != null) {
                body.put("temperature", request.getGenerationConfig().getTemperature());
            }
            if (request.getGenerationConfig().getTopP() != null) {
                body.put("top_p", request.getGenerationConfig().getTopP());
            }
            if (request.getGenerationConfig().getMaxOutputTokens() != null) {
                body.put("max_output_tokens", request.getGenerationConfig().getMaxOutputTokens());
            }
            if (request.getGenerationConfig().getStopSequences() != null && !request.getGenerationConfig().getStopSequences().isEmpty()) {
                body.put("stop", request.getGenerationConfig().getStopSequences());
            }
            if (request.getGenerationConfig().getReasoning() != null) {
                String reasoningEffort = reasoningSemanticMapper.toOpenAiEffort(request.getGenerationConfig().getReasoning());
                if (reasoningEffort != null && !reasoningEffort.isBlank()) {
                    body.put("reasoning", Map.of("effort", reasoningEffort));
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
     * 构建 input 数组（Responses API 的消息格式）
     * <p>
     * 角色映射：
     * - user → {role:"user", content:[...]}
     * - assistant → {role:"assistant", content:[...]}
     * - assistant + toolCalls → 多个 {type:"function_call", ...} 条目
     * - tool → {type:"function_call_output", call_id, output}
     * </p>
     */
    private List<Object> buildInput(UnifiedRequest request) {
        if (request.getMessages() == null) {
            return List.of();
        }

        List<Object> input = new ArrayList<>();
        for (UnifiedMessage msg : request.getMessages()) {
            switch (msg.getRole()) {
                case "user" -> input.add(Map.of(
                        "role", "user",
                        "content", extractTextContent(msg)
                ));
                case "assistant" -> {
                    // 如果有 toolCalls，先输出 assistant 文本内容
                    String text = extractTextContent(msg);
                    if (text != null && !text.isEmpty()) {
                        input.add(Map.of(
                                "role", "assistant",
                                "content", text
                        ));
                    }
                    // tool_calls → function_call 条目
                    if (msg.getToolCalls() != null) {
                        for (UnifiedToolCall tc : msg.getToolCalls()) {
                            Map<String, Object> fc = new LinkedHashMap<>();
                            fc.put("type", "function_call");
                            fc.put("id", tc.getId() != null ? tc.getId() : "");
                            fc.put("call_id", tc.getId() != null ? tc.getId() : "");
                            fc.put("name", tc.getToolName());
                            fc.put("arguments", tc.getArgumentsJson() != null ? tc.getArgumentsJson() : "{}");
                            input.add(fc);
                        }
                    }
                }
                case "tool" -> {
                    Map<String, Object> output = new LinkedHashMap<>();
                    output.put("type", "function_call_output");
                    output.put("call_id", msg.getToolCallId() != null ? msg.getToolCallId() : "");
                    output.put("output", extractTextContent(msg));
                    input.add(output);
                }
                default -> log.warn("[OpenAI Responses] 忽略不支持的角色: {}", msg.getRole());
            }
        }
        return input;
    }

    /**
     * 构建工具定义（Responses API 格式）
     */
    private List<Map<String, Object>> buildTools(List<UnifiedTool> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (UnifiedTool tool : tools) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.getName());
            if (tool.getDescription() != null) {
                function.put("description", tool.getDescription());
            }
            if (tool.getInputSchema() != null) {
                function.put("parameters", tool.getInputSchema());
            }
            if (tool.getStrict() != null) {
                function.put("strict", tool.getStrict());
            }

            Map<String, Object> toolDef = new LinkedHashMap<>();
            toolDef.put("type", "function");
            toolDef.put("name", tool.getName());
            toolDef.put("function", function);
            result.add(toolDef);
        }
        return result;
    }

    private Object buildToolChoice(UnifiedToolChoice choice) {
        if (choice.getType() == null) return null;
        if (!"specific".equals(choice.getType())) {
            return choice.getType();
        }
        return Map.of("type", "function", "name", choice.getToolName());
    }

    // ==================== 响应解析 ====================

    /**
     * 解析非流式响应
     */
    private UnifiedResponse parseResponse(JsonNode json) {
        if (json.has("error")) {
            String msg = json.path("error").path("message").asText("unknown error");
            throw new GatewayException(ErrorCode.PROVIDER_ERROR, "OpenAI Responses error: " + msg);
        }

        List<UnifiedToolCall> toolCalls = new ArrayList<>();
        List<UnifiedPart> thinkingParts = new ArrayList<>();
        StringBuilder textBuilder = new StringBuilder();

        JsonNode outputArray = json.path("output");
        if (outputArray.isArray()) {
            for (JsonNode item : outputArray) {
                String type = item.path("type").asText();
                if ("message".equals(type)) {
                    // 提取 message content
                    JsonNode content = item.path("content");
                    if (content.isArray()) {
                        for (JsonNode c : content) {
                            String contentType = c.path("type").asText();
                            if ("output_text".equals(contentType) || "text".equals(contentType)) {
                                textBuilder.append(c.path("text").asText());
                            } else if ("reasoning".equals(contentType)) {
                                UnifiedPart thinkingPart = new UnifiedPart();
                                thinkingPart.setType("thinking");
                                thinkingPart.setText(c.path("text").asText());
                                thinkingParts.add(thinkingPart);
                            }
                        }
                    }
                } else if ("reasoning".equals(type)) {
                    JsonNode content = item.path("content");
                    if (content.isArray()) {
                        for (JsonNode c : content) {
                            UnifiedPart thinkingPart = new UnifiedPart();
                            thinkingPart.setType("thinking");
                            thinkingPart.setText(c.path("text").asText());
                            thinkingParts.add(thinkingPart);
                        }
                    }
                } else if ("function_call".equals(type)) {
                    UnifiedToolCall call = new UnifiedToolCall();
                    call.setId(textOrNull(item.get("id")));
                    call.setType("function");
                    call.setToolName(item.path("name").asText());
                    call.setArgumentsJson(item.path("arguments").asText("{}"));
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

        String status = textOrNull(json.get("status"));
        String finishReason = mapStatus(status);

        UnifiedResponse response = new UnifiedResponse();
        response.setId(textOrNull(json.get("id")));
        response.setModel(textOrNull(json.get("model")));
        response.setProvider("openai-responses");
        response.setFinishReason(finishReason);
        response.setUsage(parseResponseUsage(json.get("usage")));
        response.setOutputs(List.of(output));
        return response;
    }

    /**
     * 解析流式 SSE 事件
     * <p>
     * Responses API 使用点分命名的事件类型：
     * - response.output_text.delta → TEXT_DELTA
     * - response.function_call_arguments.delta → TOOL_CALL_DELTA
     * - response.output_item.added → tool_call 开始
     * - response.completed → DONE
     * </p>
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
            return Flux.error(new GatewayException(ErrorCode.STREAM_PARSE_ERROR,
                    "failed to parse OpenAI Responses stream chunk"));
        }

        String eventType = event.event() != null ? event.event() : textOrNull(json.get("type"));
        if (eventType == null) {
            return Flux.empty();
        }

        return switch (eventType) {
            case "response.output_item.added" -> handleOutputItemAdded(json, state);
            case "response.content_part.added" -> handleContentPartAdded(json);
            case "response.output_text.delta" -> handleTextDelta(json);
            case "response.reasoning.delta" -> handleReasoningDelta(json);
            case "response.function_call_arguments.delta" -> handleFunctionCallDelta(json, state);
            case "response.output_item.done" -> handleOutputItemDone(json, state);
            case "response.completed" -> handleCompleted(json);
            case "response.done" -> handleCompleted(json);
            case "error" -> Flux.error(new GatewayException(ErrorCode.PROVIDER_ERROR,
                    json.path("error").path("message").asText("stream error")));
            default -> Flux.empty();
        };
    }

    /** tool_call 条目添加 */
    private Flux<UnifiedStreamEvent> handleOutputItemAdded(JsonNode json, StreamState state) {
        JsonNode item = json.path("item");
        String type = item.path("type").asText();
        if ("function_call".equals(type)) {
            String callId = textOrNull(item.get("call_id"));
            if (callId == null) callId = textOrNull(item.get("id"));
            state.currentCallId = callId;
            state.currentToolName = item.path("name").asText();
            state.currentArgs = new StringBuilder();

            UnifiedStreamEvent e = new UnifiedStreamEvent();
            e.setType("tool_call");
            e.setToolCallId(callId);
            e.setToolName(state.currentToolName);
            return Flux.just(e);
        }
        return Flux.empty();
    }

    /** 文本增量 */
    private Flux<UnifiedStreamEvent> handleTextDelta(JsonNode json) {
        UnifiedStreamEvent e = new UnifiedStreamEvent();
        e.setType("text_delta");
        e.setTextDelta(json.path("delta").asText());
        return Flux.just(e);
    }

    /** 思考内容增量 */
    private Flux<UnifiedStreamEvent> handleReasoningDelta(JsonNode json) {
        UnifiedStreamEvent e = new UnifiedStreamEvent();
        e.setType("thinking_delta");
        e.setThinkingDelta(json.path("delta").asText());
        return Flux.just(e);
    }

    /** content_part 新增事件 */
    private Flux<UnifiedStreamEvent> handleContentPartAdded(JsonNode json) {
        JsonNode part = json.path("part");
        if ("reasoning".equals(part.path("type").asText())) {
            UnifiedStreamEvent e = new UnifiedStreamEvent();
            e.setType("thinking_delta");
            e.setThinkingDelta(part.path("text").asText());
            return Flux.just(e);
        }
        return Flux.empty();
    }

    /** 工具参数增量 */
    private Flux<UnifiedStreamEvent> handleFunctionCallDelta(JsonNode json, StreamState state) {
        String delta = json.path("delta").asText();
        if (state.currentArgs != null) {
            state.currentArgs.append(delta);
        }

        UnifiedStreamEvent e = new UnifiedStreamEvent();
        e.setType("tool_call_delta");
        e.setToolCallId(state.currentCallId);
        e.setArgumentsDelta(delta);
        return Flux.just(e);
    }

    /** 输出条目完成 */
    private Flux<UnifiedStreamEvent> handleOutputItemDone(JsonNode json, StreamState state) {
        state.currentCallId = null;
        state.currentToolName = null;
        state.currentArgs = null;
        return Flux.empty();
    }

    /** 响应完成 */
    private Flux<UnifiedStreamEvent> handleCompleted(JsonNode json) {
        JsonNode response = json.has("response") ? json.get("response") : json;
        String status = textOrNull(response.get("status"));

        UnifiedStreamEvent e = new UnifiedStreamEvent();
        e.setType("done");
        e.setFinishReason(mapStatus(status));
        e.setUsage(parseResponseUsage(response.get("usage")));
        return Flux.just(e);
    }

    // ==================== 工具方法 ====================

    /** status → finishReason */
    private String mapStatus(String status) {
        if (status == null) return null;
        return switch (status) {
            case "completed" -> "stop";
            case "incomplete" -> "length";
            case "failed" -> "error";
            default -> status;
        };
    }

    /** 解析 Responses API usage（input_tokens / output_tokens / total_tokens） */
    private UnifiedUsage parseResponseUsage(JsonNode usageNode) {
        if (usageNode == null || usageNode.isNull() || usageNode.isMissingNode()) {
            return null;
        }
        UnifiedUsage usage = new UnifiedUsage();
        usage.setInputTokens(usageNode.path("input_tokens").isMissingNode()
                ? null : usageNode.path("input_tokens").asInt());
        usage.setOutputTokens(usageNode.path("output_tokens").isMissingNode()
                ? null : usageNode.path("output_tokens").asInt());
        usage.setTotalTokens(usageNode.path("total_tokens").isMissingNode()
                ? null : usageNode.path("total_tokens").asInt());
        return usage;
    }

    private String extractTextContent(UnifiedMessage msg) {
        if (msg.getParts() == null || msg.getParts().isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (UnifiedPart part : msg.getParts()) {
            if ("text".equals(part.getType()) && part.getText() != null) {
                sb.append(part.getText());
            }
        }
        return sb.toString();
    }

    /**
     * 流式解析状态
     */
    private static class StreamState {
        String currentCallId;
        String currentToolName;
        StringBuilder currentArgs;
    }
}
