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
import com.code.aigateway.core.model.UnifiedResponseFormat;
import com.code.aigateway.core.model.UnifiedReasoningConfig;
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
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * OpenAI Chat Completions 提供商客户端
 * <p>
 * 负责把统一请求模型转换为 OpenAI Chat Completions 兼容请求，
 * 并将上游响应重新映射为统一响应模型。
 * </p>
 *
 * @author sst
 */
@Component
@Slf4j
public class OpenAiProviderClient extends AbstractProviderClient {

    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";

    private final ReasoningSemanticMapper reasoningSemanticMapper;

    public OpenAiProviderClient(ReactorClientHttpConnector httpConnector,
                                ObjectMapper objectMapper,
                                GatewayProperties gatewayProperties,
                                CircuitBreakerManager circuitBreakerManager,
                                ReasoningSemanticMapper reasoningSemanticMapper) {
        super(httpConnector, objectMapper, gatewayProperties, circuitBreakerManager);
        this.reasoningSemanticMapper = reasoningSemanticMapper;
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.OPENAI;
    }

    @Override
    public Mono<UnifiedResponse> chat(UnifiedRequest request) {
        ProviderRuntimeConfig config = resolveRuntimeConfig(request);
        Map<String, Object> requestBody = buildRequestBody(request, false);

        Mono<JsonNode> responseMono = buildWebClient(config, extractCorrelationId(request))
                .post()
                .uri(CHAT_COMPLETIONS_PATH)
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

        // 熔断器包裹：在 retry 之后、错误映射之前
        return withCircuitBreaker(config.providerName(), request.getModel(), responseMono)
                .onErrorMap(this::mapTransportError)
                .map(this::parseChatResponse);
    }

    @Override
    public Flux<UnifiedStreamEvent> streamChat(UnifiedRequest request) {
        ProviderRuntimeConfig config = resolveRuntimeConfig(request);
        Map<String, Object> requestBody = buildRequestBody(request, true);
        AtomicBoolean firstTokenReceived = new AtomicBoolean(false);

        // 流式状态：捕获 usage-only chunk 中的 usage 数据
        StreamState state = new StreamState();

        Flux<ServerSentEvent<String>> sseFlux = buildWebClient(config, extractCorrelationId(request))
                .post()
                .uri(CHAT_COMPLETIONS_PATH)
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

        return withCircuitBreakerFlux(config.providerName(), request.getModel(), sseFlux)
                .onErrorMap(this::mapTransportError)
                .flatMap(event -> parseStreamEvent(event, state));
    }

    // ==================== 请求构建 ====================

    private Map<String, Object> buildRequestBody(UnifiedRequest request, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModel());
        body.put("messages", buildMessages(request));
        body.put("stream", stream);

        // 流式请求时要求上游返回 usage 统计
        if (stream) {
            body.put("stream_options", Map.of("include_usage", true));
        }

        if (request.getGenerationConfig() != null) {
            if (request.getGenerationConfig().getTemperature() != null) {
                body.put("temperature", request.getGenerationConfig().getTemperature());
            }
            if (request.getGenerationConfig().getTopP() != null) {
                body.put("top_p", request.getGenerationConfig().getTopP());
            }
            if (request.getGenerationConfig().getMaxOutputTokens() != null) {
                body.put("max_tokens", request.getGenerationConfig().getMaxOutputTokens());
            }
            if (request.getGenerationConfig().getStopSequences() != null && !request.getGenerationConfig().getStopSequences().isEmpty()) {
                body.put("stop", request.getGenerationConfig().getStopSequences());
            }
            if (request.getGenerationConfig().getReasoning() != null) {
                String reasoningEffort = reasoningSemanticMapper.toOpenAiEffort(request.getGenerationConfig().getReasoning());
                if (reasoningEffort != null && !reasoningEffort.isBlank()) {
                    body.put("reasoning_effort", reasoningEffort);
                }
            }
        }

        if (request.getTools() != null && !request.getTools().isEmpty()) {
            body.put("tools", buildTools(request.getTools()));
            log.info("[OpenAI-Request] model={}, stream={}, 转发 tools 数量={}, toolChoice={}",
                    request.getModel(), stream, request.getTools().size(), request.getToolChoice());
        } else {
            log.warn("[OpenAI-Request] model={}, stream={}, 未检测到 tools, request.tools={}",
                    request.getModel(), stream, request.getTools());
        }
        if (request.getToolChoice() != null) {
            body.put("tool_choice", buildToolChoice(request.getToolChoice()));
        }
        if (request.getResponseFormat() != null) {
            body.put("response_format", buildResponseFormat(request.getResponseFormat()));
        }
        return body;
    }

    private List<Map<String, Object>> buildMessages(UnifiedRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", request.getSystemPrompt()));
        }
        if (request.getMessages() == null) {
            return messages;
        }

        for (UnifiedMessage msg : request.getMessages()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("role", msg.getRole());
            if (msg.getToolCallId() != null) {
                map.put("tool_call_id", msg.getToolCallId());
            }
            if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                map.put("tool_calls", buildToolCalls(msg.getToolCalls()));
            }
            map.put("content", buildMessageContent(msg.getParts()));
            messages.add(map);
        }
        return messages;
    }

    private Object buildMessageContent(List<UnifiedPart> parts) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        if (parts.size() == 1 && "text".equals(parts.get(0).getType())) {
            return parts.get(0).getText() == null ? "" : parts.get(0).getText();
        }

        List<Map<String, Object>> content = new ArrayList<>();
        for (UnifiedPart part : parts) {
            if ("text".equals(part.getType())) {
                content.add(Map.of("type", "text", "text", part.getText() == null ? "" : part.getText()));
                continue;
            }
            if ("image".equals(part.getType())) {
                Map<String, Object> imageUrl = new LinkedHashMap<>();
                imageUrl.put("url", resolveImageUrl(part));
                if (part.getAttributes() != null && part.getAttributes().get("detail") != null) {
                    imageUrl.put("detail", part.getAttributes().get("detail"));
                }
                content.add(Map.of("type", "image_url", "image_url", imageUrl));
                continue;
            }
            throw new GatewayException(ErrorCode.INVALID_REQUEST, "unsupported message part type: " + part.getType());
        }
        return content;
    }

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
            result.add(Map.of(
                    "type", tool.getType() == null ? "function" : tool.getType(),
                    "function", function
            ));
        }
        return result;
    }

    private Object buildToolChoice(UnifiedToolChoice toolChoice) {
        if (toolChoice.getType() == null) {
            return null;
        }
        if (!"specific".equals(toolChoice.getType())) {
            return toolChoice.getType();
        }
        return Map.of("type", "function", "function", Map.of("name", toolChoice.getToolName()));
    }

    private Map<String, Object> buildResponseFormat(UnifiedResponseFormat format) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", format.getType());
        if (!"json_schema".equals(format.getType())) {
            return map;
        }
        Map<String, Object> jsonSchema = new LinkedHashMap<>();
        jsonSchema.put("name", format.getName());
        if (format.getStrict() != null) {
            jsonSchema.put("strict", format.getStrict());
        }
        jsonSchema.put("schema", format.getSchema());
        map.put("json_schema", jsonSchema);
        return map;
    }

    private List<Map<String, Object>> buildToolCalls(List<UnifiedToolCall> toolCalls) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (UnifiedToolCall tc : toolCalls) {
            result.add(Map.of(
                    "id", tc.getId(),
                    "type", tc.getType() == null ? "function" : tc.getType(),
                    "function", Map.of(
                            "name", tc.getToolName(),
                            "arguments", tc.getArgumentsJson() == null ? "{}" : tc.getArgumentsJson()
                    )
            ));
        }
        return result;
    }

    private String resolveImageUrl(UnifiedPart part) {
        if (part.getUrl() != null && !part.getUrl().isBlank()) {
            return part.getUrl();
        }
        if (part.getBase64Data() != null && !part.getBase64Data().isBlank()) {
            String mimeType = part.getMimeType() == null || part.getMimeType().isBlank()
                    ? "application/octet-stream" : part.getMimeType();
            return "data:" + mimeType + ";base64," + part.getBase64Data();
        }
        throw new GatewayException(ErrorCode.INVALID_REQUEST, "image part is missing url or base64 data");
    }

    // ==================== 响应解析 ====================

    private UnifiedResponse parseChatResponse(JsonNode json) {
        JsonNode choices = json.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new GatewayException(ErrorCode.PROVIDER_ERROR, "invalid upstream response: choices is empty");
        }

        JsonNode firstChoice = choices.get(0);
        JsonNode message = firstChoice.get("message");
        if (message == null || message.isNull() || !message.isObject()) {
            throw new GatewayException(ErrorCode.PROVIDER_ERROR, "invalid upstream response: message is missing");
        }

        // 解析 tool_calls（如果存在）
        List<UnifiedToolCall> toolCalls = null;
        if (message.has("tool_calls") && message.get("tool_calls").isArray()) {
            toolCalls = new ArrayList<>();
            for (JsonNode tc : message.get("tool_calls")) {
                UnifiedToolCall call = new UnifiedToolCall();
                call.setId(textOrNull(tc.get("id")));
                call.setType(textOrNull(tc.get("type")));
                call.setToolName(tc.path("function").path("name").asText());
                call.setArgumentsJson(tc.path("function").path("arguments").asText("{}"));
                toolCalls.add(call);
            }
        }

        UnifiedPart part = new UnifiedPart();
        part.setType("text");
        JsonNode contentNode = message.get("content");
        part.setText(contentNode == null || contentNode.isNull() ? "" : contentNode.asText());

        UnifiedOutput output = new UnifiedOutput();
        output.setRole(message.path("role").asText("assistant"));
        output.setParts(List.of(part));
        output.setToolCalls(toolCalls);

        UnifiedResponse response = new UnifiedResponse();
        response.setId(textOrNull(json.get("id")));
        response.setModel(textOrNull(json.get("model")));
        response.setProvider("openai");
        response.setFinishReason(textOrNull(firstChoice.get("finish_reason")));
        response.setUsage(parseUsage(json.get("usage")));
        response.setOutputs(List.of(output));
        return response;
    }

    /**
     * 解析流式 SSE 事件
     * <p>
     * OpenAI 开启 stream_options.include_usage 后，最终 usage 数据
     * 在一个 choices=[] 的独立 chunk 中返回，需要提取为 "usage_only" 事件
     * 供统计层采集，不会输出给客户端。
     * </p>
     */
    private Flux<UnifiedStreamEvent> parseStreamEvent(ServerSentEvent<String> event, StreamState state) {
        String data = event.data();
        if (data == null || data.isBlank() || "[DONE]".equals(data.trim())) {
            return Flux.empty();
        }

        JsonNode chunk;
        try {
            chunk = objectMapper.readTree(data);
        } catch (JsonProcessingException e) {
            return Flux.error(new GatewayException(ErrorCode.STREAM_PARSE_ERROR, "failed to parse upstream stream chunk"));
        }

        if (chunk.has("error")) {
            return Flux.error(new GatewayException(ErrorCode.PROVIDER_ERROR, "provider stream failed"));
        }

        JsonNode usageNode = chunk.get("usage");
        JsonNode choices = chunk.path("choices");

        // OpenAI 启用 stream_options.include_usage 时，最终 chunk 为 choices:[] + usage
        // 需要提取 usage 为 "usage_only" 事件，供统计层采集
        if (!choices.isArray() || choices.isEmpty()) {
            if (usageNode != null && !usageNode.isNull() && !usageNode.isMissingNode()) {
                state.pendingUsage = parseUsage(usageNode);
                UnifiedStreamEvent usageEvent = new UnifiedStreamEvent();
                usageEvent.setType("usage_only");
                usageEvent.setUsage(state.pendingUsage);
                return Flux.just(usageEvent);
            }
            return Flux.empty();
        }

        List<UnifiedStreamEvent> events = new ArrayList<>();
        for (JsonNode choice : choices) {
            int index = choice.has("index") ? choice.get("index").asInt() : 0;
            JsonNode delta = choice.path("delta");

            // 解析文本 delta
            if (delta.has("content") && !delta.get("content").isNull()) {
                UnifiedStreamEvent textEvent = new UnifiedStreamEvent();
                textEvent.setType("text_delta");
                textEvent.setOutputIndex(index);
                textEvent.setTextDelta(delta.get("content").asText());
                events.add(textEvent);
            }

            // 解析 tool_calls delta
            if (delta.has("tool_calls") && delta.get("tool_calls").isArray()) {
                for (JsonNode tcDelta : delta.get("tool_calls")) {
                    // 使用 tool_call 自身的 index，而非 choice index
                    int toolIndex = tcDelta.has("index") ? tcDelta.get("index").asInt() : 0;

                    // tool_call 开始（含 name 和 id）
                    // OpenAI 首个 chunk 同时包含 name 和 arguments（空串），
                    // 优先以 name 判定为 tool_call 开始事件
                    if (tcDelta.has("function") && tcDelta.path("function").has("name")) {
                        String toolName = tcDelta.path("function").path("name").asText();
                        log.info("[OpenAI-Stream] 检测到 tool_call 开始: toolIndex={}, id={}, name={}",
                                toolIndex, textOrNull(tcDelta.get("id")), toolName);
                        UnifiedStreamEvent tcEvent = new UnifiedStreamEvent();
                        tcEvent.setType("tool_call");
                        tcEvent.setOutputIndex(toolIndex);
                        tcEvent.setToolCallId(textOrNull(tcDelta.get("id")));
                        tcEvent.setToolName(toolName);
                        events.add(tcEvent);
                    }
                    // tool_call 参数增量（仅当无 name 时，即后续 chunk）
                    else if (tcDelta.has("function") && tcDelta.path("function").has("arguments")) {
                        UnifiedStreamEvent tcEvent = new UnifiedStreamEvent();
                        tcEvent.setType("tool_call_delta");
                        tcEvent.setOutputIndex(toolIndex);
                        tcEvent.setToolCallId(textOrNull(tcDelta.get("id")));
                        tcEvent.setArgumentsDelta(tcDelta.path("function").path("arguments").asText());
                        events.add(tcEvent);
                    }
                }
            }

            // finish_reason：优先使用当前 chunk 的 usage，回退到 usage-only chunk 捕获的 pendingUsage
            JsonNode finishNode = choice.get("finish_reason");
            if (finishNode != null && !finishNode.isNull()) {
                UnifiedStreamEvent doneEvent = new UnifiedStreamEvent();
                doneEvent.setType("done");
                doneEvent.setOutputIndex(index);
                doneEvent.setFinishReason(finishNode.asText());
                // 当前 chunk 的 usage 可能为 null（标准 OpenAI 行为），回退到已捕获的 pendingUsage
                UnifiedUsage usage = parseUsage(usageNode);
                doneEvent.setUsage(usage != null ? usage : state.pendingUsage);
                events.add(doneEvent);
            }
        }
        return Flux.fromIterable(events);
    }

    /**
     * 流式解析状态：捕获 usage-only chunk 的 usage 数据供后续使用
     */
    private static class StreamState {
        /** 从 usage-only chunk（choices:[]）捕获的 usage */
        UnifiedUsage pendingUsage;
    }
}
