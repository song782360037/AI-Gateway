package com.code.aigateway.provider.openai;

import com.code.aigateway.config.GatewayProperties;
import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.model.UnifiedMessage;
import com.code.aigateway.core.model.UnifiedOutput;
import com.code.aigateway.core.model.UnifiedPart;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedResponse;
import com.code.aigateway.core.model.UnifiedResponseFormat;
import com.code.aigateway.core.model.UnifiedStreamEvent;
import com.code.aigateway.core.model.UnifiedTool;
import com.code.aigateway.core.model.UnifiedToolCall;
import com.code.aigateway.core.model.UnifiedToolChoice;
import com.code.aigateway.core.model.UnifiedUsage;
import com.code.aigateway.provider.ProviderClient;
import com.code.aigateway.provider.ProviderType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 提供商客户端
 * <p>
 * 负责把统一请求模型转换为 OpenAI 兼容请求，
 * 并将上游响应重新映射为统一响应模型。
 * </p>
 *
 * @author sst
 */
@Component
@RequiredArgsConstructor
public class OpenAiProviderClient implements ProviderClient {

    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final GatewayProperties gatewayProperties;

    /**
     * 返回 OpenAI 提供商类型
     *
     * @return ProviderType.OPENAI
     */
    @Override
    public ProviderType getProviderType() {
        return ProviderType.OPENAI;
    }

    /**
     * 发送非流式聊天请求
     *
     * @param request 统一的请求模型
     * @return 统一响应
     */
    @Override
    public Mono<UnifiedResponse> chat(UnifiedRequest request) {
        ProviderRuntimeConfig runtimeConfig = resolveRuntimeConfig(request);
        Map<String, Object> requestBody = buildRequestBody(request, false);

        return buildWebClient(runtimeConfig)
                .post()
                .uri(CHAT_COMPLETIONS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(requestBody))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> mapErrorResponse(response, runtimeConfig))
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(runtimeConfig.timeoutSeconds()))
                .onErrorMap(this::mapTransportError)
                .map(this::parseChatResponse);
    }

    /**
     * 发送流式聊天请求
     *
     * @param request 统一的请求模型
     * @return 统一流式事件
     */
    @Override
    public Flux<UnifiedStreamEvent> streamChat(UnifiedRequest request) {
        ProviderRuntimeConfig runtimeConfig = resolveRuntimeConfig(request);
        Map<String, Object> requestBody = buildRequestBody(request, true);

        return buildWebClient(runtimeConfig)
                .post()
                .uri(CHAT_COMPLETIONS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(BodyInserters.fromValue(requestBody))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> mapErrorResponse(response, runtimeConfig))
                // 让 WebFlux 按 SSE 协议切分事件，再把每个 data 字段映射为统一流式事件。
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .timeout(Duration.ofSeconds(runtimeConfig.timeoutSeconds()))
                .onErrorMap(this::mapTransportError)
                .flatMap(this::parseStreamEvent);
    }

    /**
     * 构建 WebClient
     *
     * @param runtimeConfig provider 运行时配置
     * @return WebClient
     */
    private WebClient buildWebClient(ProviderRuntimeConfig runtimeConfig) {
        return webClientBuilder
                .baseUrl(runtimeConfig.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + runtimeConfig.apiKey())
                .build();
    }

    /**
     * 解析 provider 运行时配置
     *
     * @param request 统一请求
     * @return provider 运行时配置
     */
    private ProviderRuntimeConfig resolveRuntimeConfig(UnifiedRequest request) {
        UnifiedRequest.ProviderExecutionContext executionContext = request.getExecutionContext();
        String providerName = executionContext != null && executionContext.getProviderName() != null
                ? executionContext.getProviderName()
                : request.getProvider();
        if (providerName == null || providerName.isBlank()) {
            throw new GatewayException(ErrorCode.PROVIDER_NOT_FOUND, "provider name is missing");
        }

        // 优先从 executionContext 获取运行时参数（由持久化路由写入），
        // 若不存在则回退到 YAML 静态配置。
        GatewayProperties.ProviderProperties providerProperties = gatewayProperties.getProviders() == null
                ? null
                : gatewayProperties.getProviders().get(providerName);

        // API Key：运行时参数优先，YAML 配置兜底
        String apiKey = executionContext != null && executionContext.getProviderApiKey() != null
                ? executionContext.getProviderApiKey()
                : (providerProperties != null ? providerProperties.getApiKey() : null);
        if (apiKey == null || apiKey.isBlank()) {
            throw new GatewayException(ErrorCode.PROVIDER_ERROR, "provider api key is missing: " + providerName);
        }

        // baseUrl：运行时参数优先，YAML 配置兜底
        String baseUrl = executionContext != null && executionContext.getProviderBaseUrl() != null
                ? executionContext.getProviderBaseUrl()
                : (providerProperties != null ? providerProperties.getBaseUrl() : null);
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new GatewayException(ErrorCode.PROVIDER_ERROR, "provider base url is missing: " + providerName);
        }

        // timeoutSeconds：运行时参数优先，YAML 配置兜底
        Integer timeoutSeconds = executionContext != null && executionContext.getProviderTimeoutSeconds() != null
                ? executionContext.getProviderTimeoutSeconds()
                : (providerProperties != null ? providerProperties.getTimeoutSeconds() : null);
        if (timeoutSeconds == null || timeoutSeconds <= 0) {
            timeoutSeconds = 60;
        }

        return new ProviderRuntimeConfig(providerName, trimTrailingSlash(baseUrl), apiKey, timeoutSeconds);
    }

    /**
     * 构建上游 OpenAI 兼容请求体
     * <p>
     * 当前阶段按“文本优先”策略发送稳定字段，
     * 对于无法安全表达的能力会显式失败，避免静默丢字段。
     * </p>
     *
     * @param request 统一请求
     * @param stream  是否流式
     * @return 上游请求体
     */
    private Map<String, Object> buildRequestBody(UnifiedRequest request, boolean stream) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", request.getModel());
        requestBody.put("messages", buildMessages(request));
        requestBody.put("stream", stream);

        if (request.getGenerationConfig() != null) {
            if (request.getGenerationConfig().getTemperature() != null) {
                requestBody.put("temperature", request.getGenerationConfig().getTemperature());
            }
            if (request.getGenerationConfig().getTopP() != null) {
                requestBody.put("top_p", request.getGenerationConfig().getTopP());
            }
            if (request.getGenerationConfig().getMaxOutputTokens() != null) {
                requestBody.put("max_tokens", request.getGenerationConfig().getMaxOutputTokens());
            }
            if (request.getGenerationConfig().getStopSequences() != null && !request.getGenerationConfig().getStopSequences().isEmpty()) {
                requestBody.put("stop", request.getGenerationConfig().getStopSequences());
            }
            if (request.getGenerationConfig().getParallelToolCalls() != null) {
                requestBody.put("parallel_tool_calls", request.getGenerationConfig().getParallelToolCalls());
            }
        }

        if (request.getTools() != null && !request.getTools().isEmpty()) {
            requestBody.put("tools", buildTools(request.getTools()));
        }
        if (request.getToolChoice() != null) {
            requestBody.put("tool_choice", buildToolChoice(request.getToolChoice()));
        }
        if (request.getResponseFormat() != null) {
            requestBody.put("response_format", buildResponseFormat(request.getResponseFormat()));
        }
        return requestBody;
    }

    /**
     * 构建上游 messages
     *
     * @param request 统一请求
     * @return messages 列表
     */
    private List<Map<String, Object>> buildMessages(UnifiedRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            messages.add(Map.of(
                    "role", "system",
                    "content", request.getSystemPrompt()
            ));
        }
        if (request.getMessages() == null) {
            return messages;
        }

        for (UnifiedMessage message : request.getMessages()) {
            Map<String, Object> messageMap = new LinkedHashMap<>();
            messageMap.put("role", message.getRole());
            if (message.getToolCallId() != null) {
                messageMap.put("tool_call_id", message.getToolCallId());
            }
            if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
                messageMap.put("tool_calls", buildToolCalls(message.getToolCalls()));
            }
            messageMap.put("content", buildMessageContent(message.getParts()));
            messages.add(messageMap);
        }
        return messages;
    }

    /**
     * 构建消息内容
     *
     * @param parts 统一内容片段
     * @return OpenAI 兼容 content
     */
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
                content.add(Map.of(
                        "type", "text",
                        "text", part.getText() == null ? "" : part.getText()
                ));
                continue;
            }
            if ("image".equals(part.getType())) {
                Map<String, Object> imageUrl = new LinkedHashMap<>();
                imageUrl.put("url", resolveImageUrl(part));
                if (part.getAttributes() != null && part.getAttributes().get("detail") != null) {
                    imageUrl.put("detail", part.getAttributes().get("detail"));
                }
                content.add(Map.of(
                        "type", "image_url",
                        "image_url", imageUrl
                ));
                continue;
            }
            throw new GatewayException(ErrorCode.INVALID_REQUEST, "unsupported message part type: " + part.getType());
        }
        return content;
    }

    /**
     * 构建工具定义列表
     *
     * @param tools 工具定义
     * @return OpenAI 兼容工具定义列表
     */
    private List<Map<String, Object>> buildTools(List<UnifiedTool> tools) {
        List<Map<String, Object>> toolDefinitions = new ArrayList<>();
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

            Map<String, Object> toolDefinition = new LinkedHashMap<>();
            toolDefinition.put("type", tool.getType() == null ? "function" : tool.getType());
            toolDefinition.put("function", function);
            toolDefinitions.add(toolDefinition);
        }
        return toolDefinitions;
    }

    /**
     * 构建 tool_choice
     *
     * @param toolChoice 工具选择配置
     * @return OpenAI 兼容 tool_choice
     */
    private Object buildToolChoice(UnifiedToolChoice toolChoice) {
        if (toolChoice.getType() == null) {
            return null;
        }
        if (!"specific".equals(toolChoice.getType())) {
            return toolChoice.getType();
        }
        return Map.of(
                "type", "function",
                "function", Map.of("name", toolChoice.getToolName())
        );
    }

    /**
     * 构建响应格式配置
     *
     * @param responseFormat 统一响应格式配置
     * @return OpenAI 兼容响应格式配置
     */
    private Map<String, Object> buildResponseFormat(UnifiedResponseFormat responseFormat) {
        Map<String, Object> responseFormatMap = new LinkedHashMap<>();
        responseFormatMap.put("type", responseFormat.getType());
        if (!"json_schema".equals(responseFormat.getType())) {
            return responseFormatMap;
        }

        Map<String, Object> jsonSchema = new LinkedHashMap<>();
        jsonSchema.put("name", responseFormat.getName());
        if (responseFormat.getStrict() != null) {
            jsonSchema.put("strict", responseFormat.getStrict());
        }
        jsonSchema.put("schema", responseFormat.getSchema());
        responseFormatMap.put("json_schema", jsonSchema);
        return responseFormatMap;
    }

    /**
     * 构建历史 tool_calls
     *
     * @param toolCalls 统一工具调用列表
     * @return OpenAI 兼容 tool_calls
     */
    private List<Map<String, Object>> buildToolCalls(List<UnifiedToolCall> toolCalls) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (UnifiedToolCall toolCall : toolCalls) {
            result.add(Map.of(
                    "id", toolCall.getId(),
                    "type", toolCall.getType() == null ? "function" : toolCall.getType(),
                    "function", Map.of(
                            "name", toolCall.getToolName(),
                            "arguments", toolCall.getArgumentsJson() == null ? "{}" : toolCall.getArgumentsJson()
                    )
            ));
        }
        return result;
    }

    /**
     * 解析非流式响应
     *
     * @param responseJson 上游响应 JSON
     * @return 统一响应
     */
    private UnifiedResponse parseChatResponse(JsonNode responseJson) {
        JsonNode choices = responseJson.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new GatewayException(ErrorCode.PROVIDER_ERROR, "invalid upstream response: choices is empty");
        }

        JsonNode firstChoice = choices.get(0);
        JsonNode message = firstChoice.get("message");
        if (message == null || message.isNull() || !message.isObject()) {
            throw new GatewayException(ErrorCode.PROVIDER_ERROR, "invalid upstream response: message is missing");
        }
        if (message.has("tool_calls")) {
            throw new GatewayException(ErrorCode.PROVIDER_ERROR, "tool_calls in non-stream response are not supported yet");
        }

        JsonNode contentNode = message.get("content");
        if (contentNode == null || contentNode.isNull()) {
            throw new GatewayException(ErrorCode.PROVIDER_ERROR, "invalid upstream response: content is missing");
        }

        UnifiedPart part = new UnifiedPart();
        part.setType("text");
        part.setText(contentNode.asText());

        UnifiedOutput output = new UnifiedOutput();
        output.setRole(message.path("role").asText("assistant"));
        output.setParts(List.of(part));

        UnifiedResponse response = new UnifiedResponse();
        response.setId(textOrNull(responseJson.get("id")));
        response.setModel(textOrNull(responseJson.get("model")));
        response.setProvider("openai");
        response.setFinishReason(textOrNull(firstChoice.get("finish_reason")));
        response.setUsage(parseUsage(responseJson.get("usage")));
        response.setOutputs(List.of(output));
        return response;
    }

    /**
     * 解析上游 SSE 事件
     *
     * @param event 上游 SSE 事件
     * @return 统一流式事件
     */
    private Flux<UnifiedStreamEvent> parseStreamEvent(ServerSentEvent<String> event) {
        String data = event.data();
        if (data == null || data.isBlank()) {
            return Flux.empty();
        }
        if ("[DONE]".equals(data.trim())) {
            return Flux.empty();
        }

        JsonNode chunkJson;
        try {
            chunkJson = objectMapper.readTree(data);
        } catch (JsonProcessingException e) {
            return Flux.error(new GatewayException(ErrorCode.STREAM_PARSE_ERROR, "failed to parse upstream stream chunk"));
        }

        JsonNode usageNode = chunkJson.get("usage");
        if (chunkJson.has("error")) {
            return Flux.error(new GatewayException(ErrorCode.PROVIDER_ERROR, "provider stream failed"));
        }

        JsonNode choices = chunkJson.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return Flux.error(new GatewayException(ErrorCode.STREAM_PARSE_ERROR, "invalid upstream stream chunk: choices is empty"));
        }

        List<UnifiedStreamEvent> events = new ArrayList<>();
        for (JsonNode choice : choices) {
            JsonNode delta = choice.path("delta");
            if (delta.has("tool_calls")) {
                return Flux.error(new GatewayException(ErrorCode.PROVIDER_ERROR, "tool_calls in stream response are not supported yet"));
            }
            if (delta.has("content") && !delta.get("content").isNull()) {
                UnifiedStreamEvent textEvent = new UnifiedStreamEvent();
                textEvent.setType("text_delta");
                textEvent.setOutputIndex(choice.has("index") ? choice.get("index").asInt() : 0);
                textEvent.setTextDelta(delta.get("content").asText());
                events.add(textEvent);
            }

            JsonNode finishReasonNode = choice.get("finish_reason");
            if (finishReasonNode != null && !finishReasonNode.isNull()) {
                UnifiedStreamEvent doneEvent = new UnifiedStreamEvent();
                doneEvent.setType("done");
                doneEvent.setOutputIndex(choice.has("index") ? choice.get("index").asInt() : 0);
                doneEvent.setFinishReason(finishReasonNode.asText());
                doneEvent.setUsage(parseUsage(usageNode));
                events.add(doneEvent);
            }
        }
        return Flux.fromIterable(events);
    }

    /**
     * 将上游错误响应映射为统一异常
     *
     * @param response 上游响应
     * @param runtimeConfig provider 运行时配置
     * @return 统一异常
     */
    private Mono<? extends Throwable> mapErrorResponse(ClientResponse response, ProviderRuntimeConfig runtimeConfig) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> {
                    String message = extractErrorMessage(body);
                    if (response.statusCode().value() == 429) {
                        return new GatewayException(ErrorCode.PROVIDER_RATE_LIMIT,
                                message.isBlank() ? "provider rate limited" : message);
                    }
                    return new GatewayException(ErrorCode.PROVIDER_ERROR,
                            message.isBlank() ? "provider request failed" : "provider request failed");
                });
    }

    /**
     * 将底层传输异常映射为统一异常
     *
     * @param throwable 原始异常
     * @return 统一异常
     */
    private Throwable mapTransportError(Throwable throwable) {
        if (throwable instanceof GatewayException) {
            return throwable;
        }
        if (throwable instanceof java.util.concurrent.TimeoutException) {
            return new GatewayException(ErrorCode.PROVIDER_TIMEOUT, "provider request timeout");
        }
        return new GatewayException(ErrorCode.PROVIDER_ERROR, "provider request failed");
    }

    /**
     * 提取上游错误消息
     *
     * @param body 原始响应体
     * @return 错误消息
     */
    private String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "provider request failed";
        }
        try {
            JsonNode jsonNode = objectMapper.readTree(body);
            JsonNode messageNode = jsonNode.path("error").path("message");
            if (!messageNode.isMissingNode() && !messageNode.isNull()) {
                return messageNode.asText();
            }
        } catch (JsonProcessingException ignored) {
            // 忽略上游错误体解析失败，直接回退到原始文本。
        }
        return body;
    }

    /**
     * 解析 usage
     *
     * @param usageNode usage 节点
     * @return 统一 usage
     */
    private UnifiedUsage parseUsage(JsonNode usageNode) {
        if (usageNode == null || usageNode.isNull() || usageNode.isMissingNode()) {
            return null;
        }
        UnifiedUsage usage = new UnifiedUsage();
        usage.setInputTokens(usageNode.path("prompt_tokens").isMissingNode() ? null : usageNode.path("prompt_tokens").asInt());
        usage.setOutputTokens(usageNode.path("completion_tokens").isMissingNode() ? null : usageNode.path("completion_tokens").asInt());
        usage.setTotalTokens(usageNode.path("total_tokens").isMissingNode() ? null : usageNode.path("total_tokens").asInt());
        return usage;
    }

    /**
     * 解析图片地址
     *
     * @param part 统一图片片段
     * @return OpenAI 兼容图片地址
     */
    private String resolveImageUrl(UnifiedPart part) {
        if (part.getUrl() != null && !part.getUrl().isBlank()) {
            return part.getUrl();
        }
        if (part.getBase64Data() != null && !part.getBase64Data().isBlank()) {
            String mimeType = part.getMimeType() == null || part.getMimeType().isBlank()
                    ? "application/octet-stream"
                    : part.getMimeType();
            return "data:" + mimeType + ";base64," + part.getBase64Data();
        }
        throw new GatewayException(ErrorCode.INVALID_REQUEST, "image part is missing url or base64 data");
    }

    /**
     * 去除尾部斜杠，避免与固定路径拼接时重复。
     *
     * @param baseUrl 原始 baseUrl
     * @return 规整后的 baseUrl
     */
    private String trimTrailingSlash(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    /**
     * 读取文本值
     *
     * @param node JSON 节点
     * @return 文本值或 null
     */
    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return node.asText();
    }

    private record ProviderRuntimeConfig(String providerName, String baseUrl, String apiKey, Integer timeoutSeconds) {
    }
}
