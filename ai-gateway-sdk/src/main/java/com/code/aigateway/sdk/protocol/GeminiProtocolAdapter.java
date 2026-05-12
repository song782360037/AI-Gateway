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

/**
 * Google Gemini API 协议适配器
 * <p>
 * Gemini 协议特性：
 * <ul>
 *   <li>使用 NDJSON（非 SSE），isSse() 返回 false</li>
 *   <li>无初始事件和终止事件</li>
 *   <li>请求格式：contents 数组、systemInstruction、functionDeclarations</li>
 *   <li>响应格式：candidates[].content.parts[].text + usageMetadata</li>
 *   <li>role="model" 对应 "assistant"，role="function" 对应 tool 调用结果</li>
 * </ul>
 * </p>
 */
public class GeminiProtocolAdapter extends AbstractProtocolAdapter {

    public GeminiProtocolAdapter(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public ProtocolType getProtocolType() {
        return ProtocolType.GEMINI;
    }

    @Override
    public boolean isSse() {
        // Gemini 使用 NDJSON，不是 SSE
        return false;
    }

    // ===================== 请求解析 =====================

    @Override
    @SuppressWarnings("unchecked")
    public UnifiedRequest parse(Object rawRequest) {
        Objects.requireNonNull(rawRequest, "rawRequest must not be null");
        Map<String, Object> req = toMap(rawRequest, "rawRequest");

        UnifiedRequest unified = new UnifiedRequest();
        unified.setRequestProtocol("gemini");
        unified.setResponseProtocol("gemini");
        unified.setModel(requireString(req, "model", "model is required"));
        unified.setStream(Boolean.TRUE.equals(req.get("stream")));
        unified.setGenerationConfig(parseGenerationConfig(req));

        // 解析系统指令：systemInstruction.parts[].text
        unified.setSystemPrompt(extractSystemInstruction(req.get("systemInstruction")));

        // 解析消息内容：contents → messages
        List<Map<String, Object>> contents = requireList(req, "contents", "contents is required");
        unified.setMessages(parseContents(contents));

        // 解析工具定义：functionDeclarations → tools
        unified.setTools(parseFunctionDeclarations(req.get("tools")));
        unified.setToolChoice(parseToolConfig(req.get("tool_config")));

        return unified;
    }

    // ===================== 响应编码 =====================

    @Override
    @SuppressWarnings("unchecked")
    public Object encodeResponse(UnifiedResponse response) {
        Objects.requireNonNull(response, "response must not be null");

        // 构建 parts：文本 + 工具调用
        List<Map<String, Object>> parts = new ArrayList<>();

        // 文本内容
        String text = response.collectText();
        if (text != null && !text.isEmpty()) {
            Map<String, Object> textPart = new LinkedHashMap<>();
            textPart.put("text", text);
            parts.add(textPart);
        }

        // 工具调用
        List<UnifiedToolCall> toolCalls = response.collectToolCalls();
        for (UnifiedToolCall tc : toolCalls) {
            Map<String, Object> fc = new LinkedHashMap<>();
            fc.put("name", tc.getToolName());
            fc.put("args", parseArguments(tc.getArgumentsJson()));

            Map<String, Object> functionCallPart = new LinkedHashMap<>();
            functionCallPart.put("functionCall", fc);
            parts.add(functionCallPart);
        }

        // 构建 candidate
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("role", "model");
        content.put("parts", parts);

        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("content", content);
        candidate.put("finishReason", mapFinishReason(response.getFinishReason()));

        // 构建响应
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("candidates", List.of(candidate));
        result.put("modelVersion", response.getModel());

        // usageMetadata
        if (response.getUsage() != null) {
            result.put("usageMetadata", encodeUsageMetadata(response.getUsage()));
        }

        return result;
    }

    // ===================== 流式编码 =====================

    @Override
    public List<EncodedEvent> encodeStreamEvent(UnifiedStreamEvent event, StreamEncodeContext ctx) {
        // thinking_delta 和 done 在 Gemini 流式中不生成输出
        if ("thinking_delta".equals(event.getType()) || "done".equals(event.getType())) {
            return List.of();
        }
        if ("text_delta".equals(event.getType())) {
            return encodeTextDelta(event, ctx);
        }
        if ("tool_call".equals(event.getType())) {
            return encodeToolCallStream(event, ctx);
        }
        if ("tool_call_delta".equals(event.getType())) {
            return encodeToolCallDeltaStream(event, ctx);
        }
        return List.of();
    }

    @Override
    public Object buildError(String message, String errorType, String code, String param) {
        // Gemini 错误格式：{error:{code(Integer), message, status}}
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", mapHttpStatus(errorType));
        error.put("message", message);
        error.put("status", errorType);
        return Map.of("error", error);
    }

    @Override
    public String mapErrorType(ErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_REQUEST, PROVIDER_BAD_REQUEST -> "INVALID_ARGUMENT";
            case AUTH_FAILED, PROVIDER_AUTH_ERROR -> "UNAUTHENTICATED";
            case RATE_LIMITED, PROVIDER_RATE_LIMIT -> "RESOURCE_EXHAUSTED";
            case MODEL_NOT_FOUND, CAPABILITY_NOT_SUPPORTED -> "INVALID_ARGUMENT";
            case PROVIDER_RESOURCE_NOT_FOUND, PROVIDER_NOT_FOUND -> "NOT_FOUND";
            case PROVIDER_TIMEOUT, PROVIDER_CIRCUIT_OPEN, PROVIDER_DISABLED -> "INTERNAL";
            case PROVIDER_ERROR, PROVIDER_SERVER_ERROR -> "INTERNAL";
            default -> "INTERNAL";
        };
    }

    // ===================== 流式编码内部方法 =====================

    /** 编码文本增量流式事件 */
    private List<EncodedEvent> encodeTextDelta(UnifiedStreamEvent event, StreamEncodeContext ctx) {
        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("text", event.getTextDelta() != null ? event.getTextDelta() : "");

        List<Map<String, Object>> parts = List.of(textPart);
        return List.of(EncodedEvent.data(ctx.toJson(buildStreamChunk(parts, null, ctx))));
    }

    /** 编码工具调用开始事件 */
    private List<EncodedEvent> encodeToolCallStream(UnifiedStreamEvent event, StreamEncodeContext ctx) {
        Map<String, Object> fc = new LinkedHashMap<>();
        fc.put("name", event.getToolName() != null ? event.getToolName() : "");
        fc.put("args", Map.of());

        Map<String, Object> functionCallPart = new LinkedHashMap<>();
        functionCallPart.put("functionCall", fc);

        List<Map<String, Object>> parts = List.of(functionCallPart);
        return List.of(EncodedEvent.data(ctx.toJson(buildStreamChunk(parts, null, ctx))));
    }

    /** 编码工具调用参数增量事件 */
    @SuppressWarnings("unchecked")
    private List<EncodedEvent> encodeToolCallDeltaStream(UnifiedStreamEvent event, StreamEncodeContext ctx) {
        // Gemini 流式中工具参数增量：解析增量 JSON 并放入 args
        Object args = Map.of();
        if (event.getArgumentsDelta() != null && !event.getArgumentsDelta().isBlank()) {
            args = parseArguments(event.getArgumentsDelta());
        }

        Map<String, Object> fc = new LinkedHashMap<>();
        fc.put("name", "");
        fc.put("args", args);

        Map<String, Object> functionCallPart = new LinkedHashMap<>();
        functionCallPart.put("functionCall", fc);

        List<Map<String, Object>> parts = List.of(functionCallPart);
        return List.of(EncodedEvent.data(ctx.toJson(buildStreamChunk(parts, null, ctx))));
    }

    /** 构建流式 chunk 结构 */
    private Map<String, Object> buildStreamChunk(List<Map<String, Object>> parts, String finishReason,
                                                  StreamEncodeContext ctx) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("role", "model");
        content.put("parts", parts);

        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("content", content);
        if (finishReason != null) {
            candidate.put("finishReason", finishReason);
        }

        Map<String, Object> chunk = new LinkedHashMap<>();
        chunk.put("candidates", List.of(candidate));
        chunk.put("modelVersion", ctx.getModel());
        return chunk;
    }

    // ===================== 请求解析内部方法 =====================

    /** 解析 contents 数组为统一消息列表 */
    @SuppressWarnings("unchecked")
    private List<UnifiedMessage> parseContents(List<Map<String, Object>> contents) {
        List<UnifiedMessage> result = new ArrayList<>();
        for (int i = 0; i < contents.size(); i++) {
            Map<String, Object> content = contents.get(i);
            String role = (String) content.get("role");
            String paramPath = "contents[" + i + "]";

            // Gemini role 映射：user → user, model → assistant, function → tool
            String unifiedRole = mapRoleFromGemini(role);

            if ("tool".equals(unifiedRole)) {
                // function 角色的 content 是 functionResponse
                parseFunctionResponse(content, result);
            } else {
                parseContentParts(content, unifiedRole, result, paramPath);
            }
        }
        return result;
    }

    /** 解析普通 content 的 parts */
    @SuppressWarnings("unchecked")
    private void parseContentParts(Map<String, Object> content, String role,
                                   List<UnifiedMessage> result, String paramPath) {
        Object partsObj = content.get("parts");
        if (!(partsObj instanceof List<?> partsList)) return;

        List<UnifiedPart> textParts = new ArrayList<>();
        List<UnifiedToolCall> toolCalls = new ArrayList<>();

        for (int j = 0; j < partsList.size(); j++) {
            if (!(partsList.get(j) instanceof Map<?, ?> raw)) continue;
            Map<String, Object> part = toStringMap(raw);
            String partPath = paramPath + ".parts[" + j + "]";

            // 文本部分
            if (part.containsKey("text")) {
                textParts.add(textPart((String) part.get("text")));
            }
            // functionCall → UnifiedToolCall
            else if (part.get("functionCall") instanceof Map<?, ?> fcRaw) {
                Map<String, Object> fc = toStringMap(fcRaw);
                UnifiedToolCall call = new UnifiedToolCall();
                call.setId("fc_" + j);
                call.setType("function");
                call.setToolName((String) fc.get("name"));
                call.setArgumentsJson(stringify(fc.get("args")));
                toolCalls.add(call);
            }
            // inlineData（图片等）
            else if (part.get("inlineData") instanceof Map<?, ?> inlineRaw) {
                Map<String, Object> inline = toStringMap(inlineRaw);
                UnifiedPart imgPart = new UnifiedPart();
                imgPart.setType("image");
                imgPart.setMimeType((String) inline.get("mimeType"));
                imgPart.setBase64Data((String) inline.get("data"));
                textParts.add(imgPart);
            }
        }

        // 构建消息
        if (!textParts.isEmpty()) {
            UnifiedMessage msg = new UnifiedMessage();
            msg.setRole(role);
            msg.setParts(textParts);
            result.add(msg);
        }
        if (!toolCalls.isEmpty()) {
            UnifiedMessage toolMsg = new UnifiedMessage();
            toolMsg.setRole(role);
            toolMsg.setToolCalls(toolCalls);
            toolMsg.setParts(List.of());
            result.add(toolMsg);
        }
    }

    /** 解析 functionResponse → role=tool 的消息 */
    @SuppressWarnings("unchecked")
    private void parseFunctionResponse(Map<String, Object> content, List<UnifiedMessage> result) {
        Object partsObj = content.get("parts");
        if (!(partsObj instanceof List<?> partsList)) return;

        for (Object item : partsList) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            Map<String, Object> part = toStringMap(raw);
            if (!(part.get("functionResponse") instanceof Map<?, ?> frRaw)) continue;

            Map<String, Object> fr = toStringMap(frRaw);
            UnifiedMessage msg = new UnifiedMessage();
            msg.setRole("tool");
            msg.setToolName((String) fr.get("name"));

            // 将 response 内容序列化为文本
            Object response = fr.get("response");
            msg.setParts(List.of(textPart(response != null ? stringify(response) : "")));
            result.add(msg);
        }
    }

    /** 提取系统指令：systemInstruction.parts[].text */
    @SuppressWarnings("unchecked")
    private String extractSystemInstruction(Object sysInstruction) {
        if (!(sysInstruction instanceof Map<?, ?> map)) return null;
        Map<String, Object> si = toStringMap(map);
        Object partsObj = si.get("parts");
        if (!(partsObj instanceof List<?> parts)) return null;

        StringBuilder sb = new StringBuilder();
        for (Object item : parts) {
            if (item instanceof Map<?, ?> p && p.get("text") instanceof String text) {
                if (!sb.isEmpty()) sb.append("\n");
                sb.append(text);
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    /** 解析 functionDeclarations → UnifiedTool[] */
    @SuppressWarnings("unchecked")
    private List<UnifiedTool> parseFunctionDeclarations(Object toolsObj) {
        if (!(toolsObj instanceof Map<?, ?> toolsMap)) return List.of();
        Map<String, Object> tools = toStringMap(toolsMap);
        Object declarations = tools.get("functionDeclarations");
        if (!(declarations instanceof List<?> list)) return List.of();

        List<UnifiedTool> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            Map<String, Object> decl = toStringMap(raw);

            String name = (String) decl.get("name");
            if (name == null || name.isBlank()) continue;

            UnifiedTool t = new UnifiedTool();
            t.setName(name);
            t.setDescription((String) decl.get("description"));
            t.setType("function");
            t.setInputSchema((Map<String, Object>) decl.get("parameters"));
            result.add(t);
        }
        return result;
    }

    /** 解析 tool_config → UnifiedToolChoice */
    @SuppressWarnings("unchecked")
    private UnifiedToolChoice parseToolConfig(Object toolConfigObj) {
        if (!(toolConfigObj instanceof Map<?, ?> map)) return null;
        Map<String, Object> config = toStringMap(map);
        Object modeObj = config.get("function_calling_config");
        if (!(modeObj instanceof Map<?, ?> modeMap)) return null;
        Map<String, Object> fcConfig = toStringMap(modeMap);

        String mode = (String) fcConfig.get("mode");
        if (mode == null) return null;

        UnifiedToolChoice choice = new UnifiedToolChoice();
        if ("AUTO".equalsIgnoreCase(mode)) {
            choice.setType("auto");
        } else if ("NONE".equalsIgnoreCase(mode)) {
            choice.setType("none");
        } else if ("ANY".equalsIgnoreCase(mode)) {
            // ANY 模式下，如果有 allowed_function_names，使用第一个作为 specific
            Object allowed = fcConfig.get("allowed_function_names");
            if (allowed instanceof List<?> names && !names.isEmpty()) {
                choice.setType("specific");
                choice.setToolName((String) names.get(0));
            } else {
                choice.setType("required");
            }
        }
        return choice;
    }

    /** 解析生成配置 */
    private UnifiedGenerationConfig parseGenerationConfig(Map<String, Object> req) {
        UnifiedGenerationConfig config = new UnifiedGenerationConfig();
        config.setTemperature(req.get("temperature") instanceof Number n ? n.doubleValue() : null);
        config.setTopP(req.get("topP") instanceof Number n ? n.doubleValue() : null);
        config.setTopK(req.get("topK") instanceof Number n ? n.intValue() : null);
        config.setMaxOutputTokens(req.get("maxOutputTokens") instanceof Number n ? n.intValue() : null);

        // stop sequences
        if (req.get("stopSequences") instanceof List<?> list) {
            config.setStopSequences(list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList());
        }
        return config;
    }

    // ===================== 响应编码辅助方法 =====================

    /** 编码 usageMetadata */
    private Map<String, Object> encodeUsageMetadata(UnifiedUsage usage) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("promptTokenCount", usage.getInputTokens() != null ? usage.getInputTokens() : 0);
        meta.put("candidatesTokenCount", usage.getOutputTokens() != null ? usage.getOutputTokens() : 0);
        meta.put("totalTokenCount", usage.getTotalTokens() != null ? usage.getTotalTokens() : 0);
        if (usage.getCachedInputTokens() != null && usage.getCachedInputTokens() > 0) {
            meta.put("cachedContentTokenCount", usage.getCachedInputTokens());
        }
        return meta;
    }

    /** 映射完成原因 */
    private String mapFinishReason(String finishReason) {
        if (finishReason == null) return "STOP";
        return switch (finishReason) {
            case "stop" -> "STOP";
            case "length" -> "MAX_TOKENS";
            case "tool_calls" -> "STOP";
            default -> "STOP";
        };
    }

    /** Gemini role → 统一 role 映射 */
    private String mapRoleFromGemini(String geminiRole) {
        if (geminiRole == null) return "user";
        return switch (geminiRole.toLowerCase()) {
            case "model" -> "assistant";
            case "function" -> "tool";
            default -> geminiRole.toLowerCase();
        };
    }

    /** 将错误类型映射为 HTTP 状态码（Integer） */
    private Integer mapHttpStatus(String errorType) {
        if (errorType == null) return 500;
        return switch (errorType) {
            case "INVALID_ARGUMENT" -> 400;
            case "UNAUTHENTICATED" -> 401;
            case "PERMISSION_DENIED" -> 403;
            case "RESOURCE_EXHAUSTED" -> 429;
            case "NOT_FOUND" -> 404;
            case "INTERNAL" -> 500;
            default -> 500;
        };
    }
}
