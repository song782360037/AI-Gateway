package com.code.aigateway.sdk.protocol;

import com.code.aigateway.sdk.error.ErrorCode;
import com.code.aigateway.sdk.error.ProtocolException;
import com.code.aigateway.sdk.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * OpenAI Chat Completions 请求解析器
 * <p>
 * 从 OpenAI Chat Completions API 格式的 Map 解析为统一请求模型。
 * </p>
 */
class OpenAiChatRequestParser {

    private static final Set<String> STRING_TOOL_CHOICES = Set.of("auto", "none", "required");
    private static final Set<String> RESPONSE_FORMAT_TYPES = Set.of("text", "json_object", "json_schema");

    private final ObjectMapper objectMapper;

    OpenAiChatRequestParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将 OpenAI Chat Completions 格式的原始请求解析为统一请求模型
     */
    @SuppressWarnings("unchecked")
    UnifiedRequest parse(Object rawRequest) {
        Objects.requireNonNull(rawRequest, "rawRequest must not be null");
        Map<String, Object> req = ProtocolUtils.toMap(objectMapper, rawRequest, "rawRequest");

        UnifiedRequest unified = new UnifiedRequest();
        unified.setRequestProtocol("openai-chat");
        unified.setResponseProtocol("openai-chat");
        unified.setModel(ProtocolUtils.requireString(req, "model", "model is required"));
        unified.setStream(Boolean.TRUE.equals(req.get("stream")));
        unified.setMetadata((Map<String, Object>) req.get("metadata"));
        unified.setGenerationConfig(parseGenerationConfig(req));
        unified.setResponseFormat(parseResponseFormat(req.get("response_format")));

        // 解析消息
        List<Map<String, Object>> messages = ProtocolUtils.requireList(req, "messages", "messages is required");
        ParsedMessages parsed = parseMessages(messages);
        unified.setSystemPrompt(parsed.systemPrompt);
        unified.setMessages(parsed.messages);

        // 解析工具
        List<UnifiedTool> tools = parseTools(req.get("tools"));
        unified.setTools(tools);
        unified.setToolChoice(normalizeToolChoice(parseToolChoice(req.get("tool_choice")), tools));

        return unified;
    }

    // ===================== 消息解析 =====================

    @SuppressWarnings("unchecked")
    private ParsedMessages parseMessages(List<Map<String, Object>> messages) {
        List<UnifiedMessage> result = new ArrayList<>();
        List<String> systemPrompts = new ArrayList<>();

        for (int i = 0; i < messages.size(); i++) {
            Map<String, Object> msg = messages.get(i);
            String role = (String) msg.get("role");
            String paramPath = "messages[" + i + "]";

            if ("system".equalsIgnoreCase(role) && msg.get("content") instanceof String str) {
                systemPrompts.add(str);
                continue;
            }

            UnifiedMessage unified = new UnifiedMessage();
            unified.setRole(role);
            unified.setToolCallId((String) msg.get("tool_call_id"));
            unified.setParts(parseContent(msg.get("content"), paramPath + ".content"));
            unified.setToolCalls(parseMessageToolCalls(msg.get("tool_calls"), paramPath + ".tool_calls"));
            result.add(unified);
        }

        return new ParsedMessages(
                systemPrompts.isEmpty() ? null : String.join("\n\n", systemPrompts),
                result
        );
    }

    @SuppressWarnings("unchecked")
    private List<UnifiedPart> parseContent(Object content, String paramPath) {
        if (content == null) return List.of();
        if (content instanceof String str) {
            return List.of(ProtocolUtils.textPart(str));
        }
        if (content instanceof List<?> list) {
            List<UnifiedPart> parts = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) instanceof Map<?, ?> map) {
                    parts.add(parseContentPart(ProtocolUtils.toStringMap(map), paramPath + "[" + i + "]"));
                }
            }
            return parts;
        }
        return List.of();
    }

    private UnifiedPart parseContentPart(Map<String, Object> map, String paramPath) {
        String type = (String) map.get("type");
        if ("text".equals(type)) {
            return ProtocolUtils.textPart((String) map.get("text"));
        }
        if ("image_url".equals(type) && map.get("image_url") instanceof Map<?, ?> imageUrl) {
            String url = (String) imageUrl.get("url");
            UnifiedPart part = ProtocolUtils.parseDataUri(url);
            if (imageUrl.get("detail") instanceof String detail) {
                part.setAttributes(Map.of("detail", detail));
            }
            return part;
        }
        return ProtocolUtils.textPart("");
    }

    @SuppressWarnings("unchecked")
    private List<UnifiedToolCall> parseMessageToolCalls(Object toolCallsObj, String paramPath) {
        if (!(toolCallsObj instanceof List<?> list)) return null;
        List<UnifiedToolCall> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            Map<String, Object> tc = ProtocolUtils.toStringMap(raw);
            Map<String, Object> function = tc.get("function") instanceof Map<?, ?> m ? ProtocolUtils.toStringMap(m) : null;
            if (function == null) continue;

            UnifiedToolCall call = new UnifiedToolCall();
            call.setId((String) tc.get("id"));
            call.setType((String) tc.get("type"));
            call.setToolName((String) function.get("name"));
            call.setArgumentsJson((String) function.get("arguments"));
            result.add(call);
        }
        return result.isEmpty() ? null : result;
    }

    // ===================== 工具解析 =====================

    @SuppressWarnings("unchecked")
    private List<UnifiedTool> parseTools(Object toolsObj) {
        if (!(toolsObj instanceof List<?> list)) return List.of();
        List<UnifiedTool> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            Map<String, Object> tool = ProtocolUtils.toStringMap(raw);
            Map<String, Object> function = tool.get("function") instanceof Map<?, ?> m ? ProtocolUtils.toStringMap(m) : null;
            if (function == null) continue;

            String name = (String) function.get("name");
            if (name == null || name.isBlank()) continue;

            UnifiedTool t = new UnifiedTool();
            t.setName(name);
            t.setDescription((String) function.get("description"));
            t.setType((String) tool.get("type"));
            t.setStrict((Boolean) function.get("strict"));
            t.setInputSchema((Map<String, Object>) function.get("parameters"));
            result.add(t);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private UnifiedToolChoice parseToolChoice(Object obj) {
        if (obj == null) return null;
        if (obj instanceof String str) {
            if (!STRING_TOOL_CHOICES.contains(str)) {
                throw new ProtocolException(ErrorCode.INVALID_REQUEST, "tool_choice must be one of auto, none, required", "tool_choice");
            }
            UnifiedToolChoice choice = new UnifiedToolChoice();
            choice.setType(str);
            return choice;
        }
        if (obj instanceof Map<?, ?> map) {
            Map<String, Object> m = (Map<String, Object>) map;
            if (!"function".equals(m.get("type"))) return null;
            if (m.get("function") instanceof Map<?, ?> func) {
                String name = (String) ((Map<String, Object>) func).get("name");
                UnifiedToolChoice choice = new UnifiedToolChoice();
                choice.setType("specific");
                choice.setToolName(name);
                return choice;
            }
        }
        return null;
    }

    private UnifiedToolChoice normalizeToolChoice(UnifiedToolChoice choice, List<UnifiedTool> tools) {
        if (choice == null || !"specific".equals(choice.getType())) return choice;
        if (tools == null || tools.isEmpty()) {
            throw new ProtocolException(ErrorCode.INVALID_REQUEST, "tool_choice requires non-empty tools", "tool_choice");
        }
        boolean exists = tools.stream().map(UnifiedTool::getName).anyMatch(choice.getToolName()::equals);
        if (!exists) {
            throw new ProtocolException(ErrorCode.INVALID_REQUEST, "tool_choice name must match one of tools", "tool_choice.function.name");
        }
        return choice;
    }

    // ===================== 配置解析 =====================

    private UnifiedGenerationConfig parseGenerationConfig(Map<String, Object> req) {
        UnifiedGenerationConfig config = new UnifiedGenerationConfig();
        config.setTemperature(req.get("temperature") instanceof Number n ? n.doubleValue() : null);
        config.setTopP(req.get("top_p") instanceof Number n ? n.doubleValue() : null);
        // 优先使用 max_completion_tokens
        Integer maxTokens = req.get("max_completion_tokens") instanceof Number n ? n.intValue() :
                (req.get("max_tokens") instanceof Number n2 ? n2.intValue() : null);
        config.setMaxOutputTokens(maxTokens);

        // reasoning_effort
        String reasoningEffort = (String) req.get("reasoning_effort");
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            UnifiedReasoningConfig reasoning = new UnifiedReasoningConfig();
            reasoning.setEnabled(true);
            reasoning.setEffort(reasoningEffort);
            config.setReasoning(reasoning);
        }

        // stop sequences
        if (req.get("stop") instanceof String s) {
            config.setStopSequences(List.of(s));
        } else if (req.get("stop") instanceof List<?> list) {
            config.setStopSequences(list.stream().filter(String.class::isInstance).map(String.class::cast).toList());
        }

        config.setParallelToolCalls(req.get("parallel_tool_calls") instanceof Boolean b ? b : null);
        return config;
    }

    @SuppressWarnings("unchecked")
    private UnifiedResponseFormat parseResponseFormat(Object obj) {
        if (!(obj instanceof Map<?, ?> map)) return null;
        Map<String, Object> m = (Map<String, Object>) map;
        String type = (String) m.get("type");
        if (type == null || !RESPONSE_FORMAT_TYPES.contains(type)) return null;

        UnifiedResponseFormat format = new UnifiedResponseFormat();
        format.setType(type);
        if ("json_schema".equals(type) && m.get("json_schema") instanceof Map<?, ?> schema) {
            Map<String, Object> s = (Map<String, Object>) schema;
            format.setName((String) s.get("name"));
            format.setStrict((Boolean) s.get("strict"));
            format.setSchema((Map<String, Object>) s.get("schema"));
        }
        return format;
    }

    private record ParsedMessages(String systemPrompt, List<UnifiedMessage> messages) {}
}
