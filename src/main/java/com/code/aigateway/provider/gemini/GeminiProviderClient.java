package com.code.aigateway.provider.gemini;

import com.code.aigateway.config.GatewayProperties;
import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.resilience.CircuitBreakerManager;
import com.code.aigateway.core.model.UnifiedMessage;
import com.code.aigateway.core.model.UnifiedOutput;
import com.code.aigateway.core.model.UnifiedPart;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedResponse;
import com.code.aigateway.core.model.UnifiedStreamEvent;
import com.code.aigateway.core.model.UnifiedTool;
import com.code.aigateway.core.model.UnifiedToolCall;
import com.code.aigateway.core.model.UnifiedToolChoice;
import com.code.aigateway.core.model.UnifiedUsage;
import com.code.aigateway.provider.AbstractProviderClient;
import com.code.aigateway.provider.ProviderType;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Google Gemini 提供商客户端
 * <p>
 * 适配 Gemini generateContent / streamGenerateContent API，
 * 支持流式/非流式、工具调用（含多轮）、错误处理与重试。
 * </p>
 * <p>
 * 关键差异：
 * <ul>
 *   <li>流式响应是 JSON 流而非 SSE</li>
 *   <li>API key 在 URL query 参数而非 header</li>
 *   <li>assistant 角色在 Gemini 中叫 "model"</li>
 *   <li>functionCall.args 是 Object 而非 JSON string</li>
 *   <li>端点含模型名 /models/{model}:generateContent</li>
 * </ul>
 * </p>
 *
 * @author sst
 */
@Component
@Slf4j
public class GeminiProviderClient extends AbstractProviderClient {

    public GeminiProviderClient(WebClient.Builder webClientBuilder,
                                ObjectMapper objectMapper,
                                GatewayProperties gatewayProperties,
                                CircuitBreakerManager circuitBreakerManager) {
        super(webClientBuilder, objectMapper, gatewayProperties, circuitBreakerManager);
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.GEMINI;
    }

    /**
     * Gemini 不使用 Bearer Token 认证，API key 在 URL 参数中传入。
     * 因此 WebClient 不设置 Authorization header。
     */
    @Override
    protected WebClient buildWebClient(ProviderRuntimeConfig config, String correlationId) {
        WebClient.Builder builder = webClientBuilder
                .baseUrl(config.baseUrl());
        if (correlationId != null && !correlationId.isBlank()) {
            builder.defaultHeader("X-Correlation-Id", correlationId);
        }
        return builder.build();
    }

    @Override
    public Mono<UnifiedResponse> chat(UnifiedRequest request) {
        ProviderRuntimeConfig config = resolveRuntimeConfig(request);
        Map<String, Object> requestBody = buildRequestBody(request);
        String uri = buildUri(request.getModel(), config.apiKey(), false);

        Mono<JsonNode> responseMono = buildWebClient(config, extractCorrelationId(request))
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
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
                .map(json -> parseResponse(json, request.getModel()));
    }

    @Override
    public Flux<UnifiedStreamEvent> streamChat(UnifiedRequest request) {
        ProviderRuntimeConfig config = resolveRuntimeConfig(request);
        Map<String, Object> requestBody = buildRequestBody(request);
        String uri = buildUri(request.getModel(), config.apiKey(), true);
        AtomicBoolean firstTokenReceived = new AtomicBoolean(false);

        // Gemini 流式通过 ?alt=sse 返回 SSE 格式数据
        Flux<ServerSentEvent<String>> sseFlux = buildWebClient(config, extractCorrelationId(request))
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
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
                .filter(event -> event.data() != null && !event.data().isBlank())
                .flatMap(event -> parseStreamChunks(event.data()));
    }

    // ==================== 请求构建 ====================

    /**
     * 构建 Gemini API URI（含 model 和 API key）
     */
    private String buildUri(String model, String apiKey, boolean stream) {
        String action = stream ? ":streamGenerateContent?alt=sse&key=" : ":generateContent?key=";
        return "/v1beta/models/" + model + action + apiKey;
    }

    private Map<String, Object> buildRequestBody(UnifiedRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();

        // system instruction
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            body.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", request.getSystemPrompt()))
            ));
        }

        // 转换消息列表
        body.put("contents", buildContents(request));

        // 生成配置
        Map<String, Object> genConfig = buildGenerationConfig(request);
        if (!genConfig.isEmpty()) {
            body.put("generationConfig", genConfig);
        }

        // 工具定义
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            body.put("tools", buildTools(request.getTools()));
        }
        if (request.getToolChoice() != null) {
            Map<String, Object> toolConfig = buildToolConfig(request.getToolChoice());
            if (toolConfig != null) {
                body.put("toolConfig", toolConfig);
            }
        }
        return body;
    }

    /**
     * 构建消息内容列表。
     * Gemini 用 "model" 而非 "assistant"，"function" 而非 "tool"。
     */
    private List<Map<String, Object>> buildContents(UnifiedRequest request) {
        if (request.getMessages() == null) {
            return List.of();
        }

        List<Map<String, Object>> contents = new ArrayList<>();
        for (UnifiedMessage msg : request.getMessages()) {
            switch (msg.getRole()) {
                case "user" -> contents.add(buildContentBlock("user", msg));
                case "assistant" -> contents.add(buildContentBlock("model", msg));
                case "tool" -> contents.add(buildFunctionResponse(msg));
                default -> log.warn("[Gemini] 忽略不支持的角色: {}", msg.getRole());
            }
        }
        return contents;
    }

    /**
     * 构建普通内容块（user/model 角色）
     */
    private Map<String, Object> buildContentBlock(String role, UnifiedMessage msg) {
        List<Map<String, Object>> parts = new ArrayList<>();

        // 文本内容
        if (msg.getParts() != null) {
            for (UnifiedPart part : msg.getParts()) {
                if ("text".equals(part.getType()) && part.getText() != null && !part.getText().isEmpty()) {
                    parts.add(Map.of("text", part.getText()));
                }
                if ("image".equals(part.getType())) {
                    parts.add(buildImagePart(part));
                }
            }
        }

        // assistant 的 tool_calls → functionCall parts
        if (msg.getToolCalls() != null) {
            for (UnifiedToolCall tc : msg.getToolCalls()) {
                Map<String, Object> functionCall = new LinkedHashMap<>();
                functionCall.put("name", tc.getToolName());
                functionCall.put("args", parseJsonArgs(tc.getArgumentsJson()));
                parts.add(Map.of("functionCall", functionCall));
            }
        }

        if (parts.isEmpty()) {
            // Gemini 要求 parts 非空
            parts.add(Map.of("text", ""));
        }
        return Map.of("role", role, "parts", parts);
    }

    /**
     * 构建 functionResponse 块（tool 角色映射）
     */
    private Map<String, Object> buildFunctionResponse(UnifiedMessage msg) {
        String text = extractTextContent(msg);
        // Gemini functionResponse 需要 name 和 response 对象
        Map<String, Object> response = new LinkedHashMap<>();
        if (text != null && !text.isEmpty()) {
            response.put("result", text);
        } else {
            response.put("result", "");
        }

        return Map.of(
                "role", "user",
                "parts", List.of(Map.of(
                        "functionResponse", Map.of(
                                "name", msg.getToolName() != null ? msg.getToolName() : "",
                                "response", response
                        )
                ))
        );
    }

    private Map<String, Object> buildImagePart(UnifiedPart part) {
        if (part.getBase64Data() != null && !part.getBase64Data().isBlank()) {
            String mimeType = part.getMimeType() == null ? "image/png" : part.getMimeType();
            return Map.of(
                    "inlineData", Map.of(
                            "mimeType", mimeType,
                            "data", part.getBase64Data()
                    )
            );
        }
        if (part.getUrl() != null && !part.getUrl().isBlank()) {
            return Map.of("text", "[image: " + part.getUrl() + "]");
        }
        return Map.of("text", "");
    }

    private Map<String, Object> buildGenerationConfig(UnifiedRequest request) {
        Map<String, Object> config = new LinkedHashMap<>();
        if (request.getGenerationConfig() != null) {
            if (request.getGenerationConfig().getTemperature() != null) {
                config.put("temperature", request.getGenerationConfig().getTemperature());
            }
            if (request.getGenerationConfig().getTopP() != null) {
                config.put("topP", request.getGenerationConfig().getTopP());
            }
            if (request.getGenerationConfig().getMaxOutputTokens() != null) {
                config.put("maxOutputTokens", request.getGenerationConfig().getMaxOutputTokens());
            }
            if (request.getGenerationConfig().getStopSequences() != null && !request.getGenerationConfig().getStopSequences().isEmpty()) {
                config.put("stopSequences", request.getGenerationConfig().getStopSequences());
            }
        }
        return config;
    }

    /**
     * 构建工具定义（Gemini 用 functionDeclarations 而非 tools）
     */
    private List<Map<String, Object>> buildTools(List<UnifiedTool> tools) {
        List<Map<String, Object>> declarations = new ArrayList<>();
        for (UnifiedTool tool : tools) {
            Map<String, Object> func = new LinkedHashMap<>();
            func.put("name", tool.getName());
            if (tool.getDescription() != null) {
                func.put("description", tool.getDescription());
            }
            if (tool.getInputSchema() != null) {
                func.put("parameters", tool.getInputSchema());
            }
            declarations.add(func);
        }
        return List.of(Map.of("functionDeclarations", declarations));
    }

    /**
     * 构建 toolConfig（Gemini 的工具选择配置）
     */
    private Map<String, Object> buildToolConfig(UnifiedToolChoice choice) {
        if (choice.getType() == null) {
            return null;
        }
        String mode = switch (choice.getType()) {
            case "auto" -> "AUTO";
            case "none" -> "NONE";
            case "required" -> "ANY";
            case "specific" -> "ANY";
            default -> null;
        };
        if (mode == null) {
            return null;
        }

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("mode", mode);
        if ("specific".equals(choice.getType()) && choice.getToolName() != null) {
            config.put("allowedFunctionNames", List.of(choice.getToolName()));
        }
        return Map.of("functionCallingConfig", config);
    }

    // ==================== 响应解析 ====================

    /**
     * 解析非流式响应
     */
    private UnifiedResponse parseResponse(JsonNode json, String requestModel) {
        JsonNode error = json.get("error");
        if (error != null && !error.isNull()) {
            throw new GatewayException(ErrorCode.PROVIDER_ERROR,
                    "Gemini error: " + error.path("message").asText("unknown"));
        }

        JsonNode candidates = json.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            // 可能被安全过滤
            JsonNode promptFeedback = json.get("promptFeedback");
            if (promptFeedback != null) {
                throw new GatewayException(ErrorCode.PROVIDER_ERROR,
                        "Gemini prompt blocked: " + promptFeedback.path("blockReason").asText("unknown"));
            }
            throw new GatewayException(ErrorCode.PROVIDER_ERROR, "Gemini response: no candidates");
        }

        return parseCandidate(candidates.get(0), json, requestModel);
    }

    /**
     * 解析单个 candidate
     */
    private UnifiedResponse parseCandidate(JsonNode candidate, JsonNode fullJson, String requestModel) {
        List<UnifiedToolCall> toolCalls = new ArrayList<>();
        StringBuilder textBuilder = new StringBuilder();

        JsonNode parts = candidate.path("content").path("parts");
        if (parts.isArray()) {
            for (JsonNode part : parts) {
                if (part.has("text")) {
                    textBuilder.append(part.get("text").asText());
                }
                if (part.has("functionCall")) {
                    JsonNode fc = part.get("functionCall");
                    UnifiedToolCall call = new UnifiedToolCall();
                    call.setId("call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
                    call.setType("function");
                    call.setToolName(fc.path("name").asText());
                    // Gemini args 是 object，转为 JSON string
                    call.setArgumentsJson(objectToString(fc.get("args")));
                    toolCalls.add(call);
                }
            }
        }

        UnifiedPart textPart = new UnifiedPart();
        textPart.setType("text");
        textPart.setText(textBuilder.toString());

        UnifiedOutput output = new UnifiedOutput();
        output.setRole("assistant");
        output.setParts(textBuilder.isEmpty() && !toolCalls.isEmpty() ? List.of() : List.of(textPart));
        output.setToolCalls(toolCalls.isEmpty() ? null : toolCalls);

        String finishReason = mapFinishReason(textOrNull(candidate.get("finishReason")));

        UnifiedResponse response = new UnifiedResponse();
        response.setId("gemini-" + UUID.randomUUID().toString().substring(0, 8));
        response.setModel(textOrNull(fullJson.get("modelVersion")) != null
                ? fullJson.get("modelVersion").asText() : requestModel);
        response.setProvider("gemini");
        response.setFinishReason(finishReason);
        response.setUsage(parseGeminiUsage(fullJson.get("usageMetadata")));
        response.setOutputs(List.of(output));
        return response;
    }

    /**
     * 解析流式 JSON chunk。
     * Gemini 流式可能返回多个 JSON 对象拼接，或 JSON 数组。
     */
    private Flux<UnifiedStreamEvent> parseStreamChunks(String chunk) {
        if (chunk == null || chunk.isBlank()) {
            return Flux.empty();
        }

        // 处理可能的 JSON 数组（Gemini 有时把多个 chunk 放在数组里）
        String trimmed = chunk.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            // 去掉数组外层括号，拆分内部对象
            return parseJsonArray(trimmed);
        }

        // 单个 JSON 对象
        return parseSingleChunk(trimmed);
    }

    private Flux<UnifiedStreamEvent> parseJsonArray(String jsonArray) {
        try {
            JsonNode array = objectMapper.readTree(jsonArray);
            List<UnifiedStreamEvent> events = new ArrayList<>();
            if (array.isArray()) {
                for (JsonNode item : array) {
                    events.addAll(extractStreamEvents(item));
                }
            }
            return Flux.fromIterable(events);
        } catch (Exception e) {
            return Flux.empty();
        }
    }

    private Flux<UnifiedStreamEvent> parseSingleChunk(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return Flux.fromIterable(extractStreamEvents(node));
        } catch (Exception e) {
            return Flux.error(new GatewayException(ErrorCode.STREAM_PARSE_ERROR,
                    "failed to parse Gemini stream chunk"));
        }
    }

    /**
     * 从单个 Gemini 流式 chunk 中提取事件
     */
    private List<UnifiedStreamEvent> extractStreamEvents(JsonNode chunk) {
        List<UnifiedStreamEvent> events = new ArrayList<>();

        JsonNode candidates = chunk.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            return events;
        }

        JsonNode candidate = candidates.get(0);
        JsonNode parts = candidate.path("content").path("parts");
        String finishReason = mapFinishReason(textOrNull(candidate.get("finishReason")));

        if (parts.isArray()) {
            int partIndex = 0;
            for (JsonNode part : parts) {
                // 文本增量
                if (part.has("text")) {
                    UnifiedStreamEvent e = new UnifiedStreamEvent();
                    e.setType("text_delta");
                    e.setOutputIndex(partIndex);
                    e.setTextDelta(part.get("text").asText());
                    events.add(e);
                }

                // 函数调用
                if (part.has("functionCall")) {
                    JsonNode fc = part.get("functionCall");
                    String callId = "call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

                    // tool_call 开始
                    UnifiedStreamEvent begin = new UnifiedStreamEvent();
                    begin.setType("tool_call");
                    begin.setOutputIndex(partIndex);
                    begin.setToolCallId(callId);
                    begin.setToolName(fc.path("name").asText());
                    events.add(begin);

                    // 参数（Gemini 一次性发送完整参数）
                    UnifiedStreamEvent args = new UnifiedStreamEvent();
                    args.setType("tool_call_delta");
                    args.setOutputIndex(partIndex);
                    args.setToolCallId(callId);
                    args.setArgumentsDelta(objectToString(fc.get("args")));
                    events.add(args);
                }
                partIndex++;
            }
        }

        // finish_reason
        if (finishReason != null) {
            JsonNode usageNode = chunk.get("usageMetadata");
            UnifiedStreamEvent done = new UnifiedStreamEvent();
            done.setType("done");
            done.setFinishReason(finishReason);
            done.setUsage(parseGeminiUsage(usageNode));
            events.add(done);
        }

        return events;
    }

    // ==================== 错误处理 ====================

    /**
     * Gemini 错误格式：{"error":{"code":400,"message":"...","status":"INVALID_ARGUMENT"}}
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
        } catch (Exception ignored) {
        }
        return "";
    }

    // ==================== 工具方法 ====================

    private String mapFinishReason(String reason) {
        if (reason == null) return null;
        return switch (reason) {
            case "STOP" -> "stop";
            case "MAX_TOKENS" -> "length";
            case "FUNCTION_CALL" -> "tool_calls";
            case "SAFETY" -> "content_filter";
            case "RECITATION" -> "content_filter";
            default -> reason;
        };
    }

    /** 解析 Gemini usage（promptTokenCount / candidatesTokenCount / totalTokenCount） */
    private UnifiedUsage parseGeminiUsage(JsonNode usageNode) {
        if (usageNode == null || usageNode.isNull() || usageNode.isMissingNode()) {
            return null;
        }
        UnifiedUsage usage = new UnifiedUsage();
        usage.setInputTokens(usageNode.path("promptTokenCount").isMissingNode()
                ? null : usageNode.path("promptTokenCount").asInt());
        usage.setOutputTokens(usageNode.path("candidatesTokenCount").isMissingNode()
                ? null : usageNode.path("candidatesTokenCount").asInt());
        usage.setTotalTokens(usageNode.path("totalTokenCount").isMissingNode()
                ? null : usageNode.path("totalTokenCount").asInt());
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

    private Object parseJsonArgs(String json) {
        if (json == null || json.isEmpty() || "{}".equals(json)) return Map.of();
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String objectToString(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return "{}";
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{}";
        }
    }
}
