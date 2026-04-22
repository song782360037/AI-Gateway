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
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
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

    public OpenAiResponsesProviderClient(ReactorClientHttpConnector httpConnector,
                                         ObjectMapper objectMapper,
                                         GatewayProperties gatewayProperties,
                                         CircuitBreakerManager circuitBreakerManager,
                                         ReasoningSemanticMapper reasoningSemanticMapper) {
        super(httpConnector, objectMapper, gatewayProperties, circuitBreakerManager);
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

        // 诊断：记录发送到上游的请求体摘要
        List<?> inputItems = (List<?>) requestBody.get("input");
        log.info("[OpenAI Responses] 发送非流式请求, provider={}, model={}, inputSize={}, bodyKeys={}",
                config.providerName(), request.getModel(),
                inputItems != null ? inputItems.size() : "null",
                requestBody.keySet());

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
            responseMono = responseMono.retryWhen(buildRetrySpec(config, request.getStatsContext()));
        }

        return withCircuitBreaker(config.providerName(), request.getModel(), responseMono)
                .onErrorMap(this::mapTransportError)
                .map(this::parseResponse);
    }

    @Override
    public Flux<UnifiedStreamEvent> streamChat(UnifiedRequest request) {
        ProviderRuntimeConfig config = resolveRuntimeConfig(request);
        Map<String, Object> requestBody = buildRequestBody(request, true);

        // 诊断：记录发送到上游的请求体摘要
        List<?> inputItems = (List<?>) requestBody.get("input");
        log.info("[OpenAI Responses] 发送流式请求, provider={}, model={}, inputSize={}, bodyKeys={}",
                config.providerName(), request.getModel(),
                inputItems != null ? inputItems.size() : "null",
                requestBody.keySet());

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
                    .retryWhen(buildStreamRetrySpec(config, firstTokenReceived, request.getStatsContext()));
        }

        return withCircuitBreakerFlux(config.providerName(), request.getModel(), sseFlux)
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
        List<Object> inputItems = buildInput(request);
        body.put("input", inputItems);

        // 调试日志：记录发送到上游的 input 大小和 messages 状态
        if (inputItems.isEmpty()) {
            log.warn("[OpenAI Responses] 构建请求体时 input 为空, messages={}, systemPrompt={}",
                    request.getMessages() != null ? request.getMessages().size() : "null",
                    request.getSystemPrompt() != null ? "present" : "null");
        }

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
                UnifiedReasoningConfig reasoningConfig = request.getGenerationConfig().getReasoning();
                String reasoningEffort = reasoningSemanticMapper.toOpenAiEffort(reasoningConfig);
                if (reasoningEffort != null && !reasoningEffort.isBlank()) {
                    Map<String, Object> reasoningMap = new LinkedHashMap<>();
                    reasoningMap.put("effort", reasoningEffort);
                    // 传递 summary 参数，使 OpenAI 返回可见推理摘要
                    if (reasoningConfig.getSummary() != null && !reasoningConfig.getSummary().isBlank()) {
                        reasoningMap.put("summary", reasoningConfig.getSummary());
                    }
                    body.put("reasoning", reasoningMap);
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

        Map<String, String> toolCallIdMappings = new HashMap<>();
        List<Object> input = new ArrayList<>();
        for (UnifiedMessage msg : request.getMessages()) {
            switch (msg.getRole()) {
                case "user" -> input.add(buildMessageInputItem("user", msg));
                case "assistant" -> {
                    // 如果有 toolCalls，先输出 assistant 内容
                    if (msg.getParts() != null && !msg.getParts().isEmpty()) {
                        input.add(buildMessageInputItem("assistant", msg));
                    }
                    // tool_calls → function_call 条目
                    if (msg.getToolCalls() != null) {
                        for (UnifiedToolCall tc : msg.getToolCalls()) {
                            String responsesCallId = mapToolCallIdForResponses(tc.getId(), toolCallIdMappings);
                            Map<String, Object> fc = new LinkedHashMap<>();
                            fc.put("type", "function_call");
                            fc.put("id", responsesCallId);
                            fc.put("call_id", responsesCallId);
                            fc.put("name", tc.getToolName());
                            fc.put("arguments", tc.getArgumentsJson() != null ? tc.getArgumentsJson() : "{}");
                            input.add(fc);
                        }
                    }
                }
                case "tool" -> {
                    Map<String, Object> output = new LinkedHashMap<>();
                    output.put("type", "function_call_output");
                    output.put("call_id", mapToolCallIdForResponses(msg.getToolCallId(), toolCallIdMappings));
                    output.put("output", extractTextContent(msg));
                    input.add(output);
                }
                default -> log.warn("[OpenAI Responses] 忽略不支持的角色: {}", msg.getRole());
            }
        }
        return input;
    }

    /**
     * 构建标准 Responses API message input item。
     * <p>
     * 为兼容严格上游实现，显式输出 type=message，content 使用 input_text / input_image 数组格式。
     * </p>
     */
    private Map<String, Object> buildMessageInputItem(String role, UnifiedMessage msg) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "message");
        item.put("role", role);

        String textContentType = resolveMessageTextContentType(role);
        if (msg.getParts() == null || msg.getParts().isEmpty()) {
            item.put("content", List.of(Map.of("type", textContentType, "text", "")));
            return item;
        }

        // 检查是否只有文本内容（快速路径）
        boolean hasOnlyText = msg.getParts().stream().allMatch(p -> "text".equals(p.getType()));
        if (hasOnlyText) {
            item.put("content", List.of(Map.of("type", textContentType, "text", extractTextContent(msg))));
            return item;
        }

        // 包含图片等非文本内容，构建混合 content 数组
        List<Map<String, Object>> contentList = new ArrayList<>();
        for (UnifiedPart part : msg.getParts()) {
            if ("text".equals(part.getType())) {
                String text = part.getText() != null ? part.getText() : "";
                if (!text.isEmpty()) {
                    contentList.add(Map.of("type", textContentType, "text", text));
                }
            } else if ("image".equals(part.getType())) {
                contentList.add(buildResponsesImageContent(part));
            }
        }

        if (contentList.isEmpty()) {
            contentList.add(Map.of("type", textContentType, "text", ""));
        }
        item.put("content", contentList);
        return item;
    }

    /**
     * 根据消息角色选择 Responses API 文本 content 类型。
     * <p>
     * user 历史消息属于输入语义，使用 input_text；assistant 历史消息属于输出语义，
     * 使用 output_text，避免严格上游在重放历史 assistant 消息时校验失败。
     * </p>
     */
    private String resolveMessageTextContentType(String role) {
        return "assistant".equals(role) ? "output_text" : "input_text";
    }

    /**
     * 构建 Responses API 图片 content 项
     * <p>
     * 格式：{type:"input_image", image_url:"https://..." | "data:image/...;base64,...", detail?"high"}
     * </p>
     */
    private Map<String, Object> buildResponsesImageContent(UnifiedPart part) {
        Map<String, Object> imageContent = new LinkedHashMap<>();
        imageContent.put("type", "input_image");

        // 构建 image_url
        if (part.getUrl() != null && !part.getUrl().isBlank()) {
            imageContent.put("image_url", part.getUrl());
        } else if (part.getBase64Data() != null && !part.getBase64Data().isBlank()) {
            String mimeType = part.getMimeType() != null ? part.getMimeType() : "image/png";
            imageContent.put("image_url", "data:" + mimeType + ";base64," + part.getBase64Data());
        } else {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, "image part is missing image_url or base64 data");
        }

        // detail 参数（可选）
        if (part.getAttributes() != null && part.getAttributes().get("detail") != null) {
            imageContent.put("detail", part.getAttributes().get("detail"));
        }
        return imageContent;
    }

    /**
     * 将统一模型中的工具调用 ID 映射为 Responses 请求内稳定、显式管理的 call_id。
     * <p>
     * 不再依赖原始 ID 的前缀语义，而是在单次请求内为每个原始 ID 建立一条稳定映射，
     * 确保 assistant 的 function_call 与后续 tool 的 function_call_output 始终复用同一个
     * Responses call_id。
     * </p>
     */
    private String mapToolCallIdForResponses(String originalId, Map<String, String> toolCallIdMappings) {
        if (originalId == null || originalId.isBlank()) {
            return "";
        }
        return toolCallIdMappings.computeIfAbsent(originalId,
                ignored -> "fc_" + toolCallIdMappings.size());
    }

    /**
     * 构建工具定义（Responses API 扁平格式）
     * <p>
     * Responses API 使用扁平结构：{type, name, description, parameters, strict}
     * 不同于 Chat Completions 的嵌套格式：{type, function: {name, ...}}
     * </p>
     */
    private List<Map<String, Object>> buildTools(List<UnifiedTool> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (UnifiedTool tool : tools) {
            if (tool.getName() == null || tool.getName().isBlank()) {
                continue;
            }
            Map<String, Object> toolDef = new LinkedHashMap<>();
            toolDef.put("type", tool.getType() != null ? tool.getType() : "function");
            toolDef.put("name", tool.getName());
            if (tool.getDescription() != null) {
                toolDef.put("description", tool.getDescription());
            }
            if (tool.getInputSchema() != null) {
                toolDef.put("parameters", tool.getInputSchema());
            }
            if (tool.getStrict() != null) {
                toolDef.put("strict", tool.getStrict());
            }
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
     * <p>
     * OpenAI Responses API 的正常响应体也包含 "error": null 字段，
     * 必须用 hasNonNull 而非 has 来判断是否真正存在错误。
     * </p>
     */
    private UnifiedResponse parseResponse(JsonNode json) {
        // 注意：必须用 hasNonNull，OpenAI Responses 正常响应中 "error" 字段值为 null，
        // 若用 has() 会把正常响应误判为错误
        if (json.hasNonNull("error")) {
            JsonNode errorNode = json.get("error");
            String msg = errorNode.path("message").asText(null);
            String type = errorNode.path("type").asText(null);
            String code = errorNode.path("code").asText(null);

            // 构建详细的错误信息
            StringBuilder errorDetail = new StringBuilder();
            if (msg != null && !msg.isBlank()) {
                errorDetail.append(msg);
            }
            if (type != null && !type.isBlank()) {
                if (errorDetail.length() > 0) errorDetail.append(", ");
                errorDetail.append("type=").append(type);
            }
            if (code != null && !code.isBlank()) {
                if (errorDetail.length() > 0) errorDetail.append(", ");
                errorDetail.append("code=").append(code);
            }
            if (errorDetail.length() == 0) {
                // 兜底：记录完整 error 节点用于调试
                errorDetail.append("unknown error, raw=").append(errorNode);
            }

            log.warn("[OpenAI Responses] 上游返回错误响应: {}", errorDetail);
            throw new GatewayException(ErrorCode.PROVIDER_ERROR, "OpenAI Responses error: " + errorDetail);
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
                            if ("output_text".equals(contentType) || "input_text".equals(contentType) || "text".equals(contentType)) {
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
                    JsonNode summary = item.path("summary");
                    if (summary.isArray() && !summary.isEmpty()) {
                        for (JsonNode s : summary) {
                            UnifiedPart thinkingPart = new UnifiedPart();
                            thinkingPart.setType("thinking");
                            thinkingPart.setText(s.path("text").asText());
                            thinkingParts.add(thinkingPart);
                        }
                    } else {
                        JsonNode content = item.path("content");
                        if (content.isArray()) {
                            for (JsonNode c : content) {
                                UnifiedPart thinkingPart = new UnifiedPart();
                                thinkingPart.setType("thinking");
                                thinkingPart.setText(c.path("text").asText());
                                thinkingParts.add(thinkingPart);
                            }
                        }
                    }
                } else if ("function_call".equals(type)) {
                    UnifiedToolCall call = new UnifiedToolCall();
                    // 优先使用 call_id（Responses API 标准字段），回退到 id
                    String callId = textOrNull(item.get("call_id"));
                    call.setId(callId != null ? callId : textOrNull(item.get("id")));
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
        response.setCreated(longOrNull(json.get("created_at")));
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
            case "response.reasoning.delta", "response.reasoning_summary_text.delta" -> handleReasoningDelta(json);
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
        JsonNode item = json.hasNonNull("item") ? json.get("item") : json.get("output");
        if (item == null || item.isMissingNode() || item.isNull()) {
            return Flux.empty();
        }
        String type = item.path("type").asText();
        Integer outputIndex = intOrNull(json.get("output_index"));
        String itemId = textOrNull(item.get("id"));
        if ("function_call".equals(type)) {
            String callId = textOrNull(item.get("call_id"));
            if (callId == null) {
                callId = itemId;
            }
            state.rememberToolCall(itemId, callId, outputIndex, item.path("name").asText());

            UnifiedStreamEvent e = new UnifiedStreamEvent();
            e.setType("tool_call");
            e.setOutputIndex(outputIndex);
            e.setItemId(itemId);
            e.setToolCallId(callId);
            e.setToolName(item.path("name").asText());
            return Flux.just(e);
        }
        return Flux.empty();
    }

    /** 文本增量 */
    private Flux<UnifiedStreamEvent> handleTextDelta(JsonNode json) {
        UnifiedStreamEvent e = new UnifiedStreamEvent();
        e.setType("text_delta");
        e.setOutputIndex(intOrNull(json.get("output_index")));
        e.setItemId(textOrNull(json.get("item_id")));
        e.setTextDelta(json.path("delta").asText());
        return Flux.just(e);
    }

    /** 思考内容增量（包括 reasoning delta 和 reasoning summary text delta） */
    private Flux<UnifiedStreamEvent> handleReasoningDelta(JsonNode json) {
        UnifiedStreamEvent e = new UnifiedStreamEvent();
        e.setType("thinking_delta");
        e.setOutputIndex(intOrNull(json.get("output_index")));
        e.setItemId(textOrNull(json.get("item_id")));
        e.setThinkingDelta(json.path("delta").asText());
        return Flux.just(e);
    }

    /** content_part 新增事件 */
    private Flux<UnifiedStreamEvent> handleContentPartAdded(JsonNode json) {
        JsonNode part = json.path("part");
        if ("reasoning".equals(part.path("type").asText())) {
            UnifiedStreamEvent e = new UnifiedStreamEvent();
            e.setType("thinking_delta");
            e.setOutputIndex(intOrNull(json.get("output_index")));
            e.setItemId(textOrNull(json.get("item_id")));
            e.setThinkingDelta(part.path("text").asText());
            return Flux.just(e);
        }
        return Flux.empty();
    }

    /** 工具参数增量 */
    private Flux<UnifiedStreamEvent> handleFunctionCallDelta(JsonNode json, StreamState state) {
        String delta = json.path("delta").asText();
        String callId = textOrNull(json.get("call_id"));
        String itemId = textOrNull(json.get("item_id"));
        Integer outputIndex = intOrNull(json.get("output_index"));

        StreamToolCallState toolState = state.resolveToolCall(itemId, callId);
        if (toolState != null) {
            if (toolState.arguments != null) {
                toolState.arguments.append(delta);
            }
            if (callId == null) {
                callId = toolState.callId;
            }
            if (itemId == null) {
                itemId = toolState.itemId;
            }
            if (outputIndex == null) {
                outputIndex = toolState.outputIndex;
            }
        }

        UnifiedStreamEvent e = new UnifiedStreamEvent();
        e.setType("tool_call_delta");
        e.setOutputIndex(outputIndex);
        e.setItemId(itemId);
        e.setToolCallId(callId);
        e.setArgumentsDelta(delta);
        return Flux.just(e);
    }

    /** 输出条目完成 */
    private Flux<UnifiedStreamEvent> handleOutputItemDone(JsonNode json, StreamState state) {
        String itemId = textOrNull(json.get("item_id"));
        String callId = textOrNull(json.get("call_id"));
        state.forgetToolCall(itemId, callId);
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

    /** 解析 Responses API usage（input_tokens / output_tokens / total_tokens / cached_tokens） */
    private UnifiedUsage parseResponseUsage(JsonNode usageNode) {
        if (usageNode == null || usageNode.isNull() || usageNode.isMissingNode()) {
            return null;
        }
        UnifiedUsage usage = new UnifiedUsage();
        usage.setInputTokens(usageNode.path("input_tokens").isMissingNode()
                ? null : usageNode.path("input_tokens").asInt());
        usage.setCachedInputTokens(usageNode.path("input_tokens_details").path("cached_tokens").isMissingNode()
                ? null : usageNode.path("input_tokens_details").path("cached_tokens").asInt());
        usage.setOutputTokens(usageNode.path("output_tokens").isMissingNode()
                ? null : usageNode.path("output_tokens").asInt());
        usage.setTotalTokens(usageNode.path("total_tokens").isMissingNode()
                ? null : usageNode.path("total_tokens").asInt());
        return usage;
    }

    private Integer intOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || !node.canConvertToInt()) {
            return null;
        }
        return node.asInt();
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
        private final Map<String, StreamToolCallState> toolCallsByItemId = new HashMap<>();
        private final Map<String, StreamToolCallState> toolCallsByCallId = new HashMap<>();
        /** 最近一次 added 的 tool call，用于 delta 不带任何身份字段时的兜底回填 */
        private StreamToolCallState lastToolCall;

        void rememberToolCall(String itemId, String callId, Integer outputIndex, String toolName) {
            StreamToolCallState state = new StreamToolCallState(itemId, callId, outputIndex, toolName);
            if (itemId != null && !itemId.isBlank()) {
                toolCallsByItemId.put(itemId, state);
            }
            if (callId != null && !callId.isBlank()) {
                toolCallsByCallId.put(callId, state);
            }
            lastToolCall = state;
        }

        StreamToolCallState resolveToolCall(String itemId, String callId) {
            if (itemId != null && !itemId.isBlank() && toolCallsByItemId.containsKey(itemId)) {
                return toolCallsByItemId.get(itemId);
            }
            if (callId != null && !callId.isBlank()) {
                return toolCallsByCallId.get(callId);
            }
            // delta 没带任何身份字段时，回退到最近一次 added 的 tool call
            return lastToolCall;
        }

        void forgetToolCall(String itemId, String callId) {
            StreamToolCallState state = resolveToolCall(itemId, callId);
            if (state == null) {
                return;
            }
            if (state.itemId != null && !state.itemId.isBlank()) {
                toolCallsByItemId.remove(state.itemId);
            }
            if (state.callId != null && !state.callId.isBlank()) {
                toolCallsByCallId.remove(state.callId);
            }
            if (state == lastToolCall) {
                lastToolCall = null;
            }
        }
    }

    private static class StreamToolCallState {
        private final String itemId;
        private final String callId;
        private final Integer outputIndex;
        private final String toolName;
        private final StringBuilder arguments = new StringBuilder();

        private StreamToolCallState(String itemId, String callId, Integer outputIndex, String toolName) {
            this.itemId = itemId;
            this.callId = callId;
            this.outputIndex = outputIndex;
            this.toolName = toolName;
        }
    }
}
