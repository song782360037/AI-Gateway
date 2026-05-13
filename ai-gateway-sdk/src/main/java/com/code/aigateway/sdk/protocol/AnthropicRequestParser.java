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
 * Anthropic 请求解析器
 * <p>
 * 从 Anthropic Messages API 格式的 Map 解析为统一请求模型。
 * 包含消息、工具定义、生成配置、系统提示词等解析逻辑。
 * </p>
 */
class AnthropicRequestParser {

    /** Anthropic 允许的字符串 tool_choice 值 */
    private static final Set<String> STRING_TOOL_CHOICES = Set.of("auto", "any");

    private final ObjectMapper objectMapper;

    AnthropicRequestParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将 Anthropic 格式的原始请求解析为统一请求模型
     */
    @SuppressWarnings("unchecked")
    UnifiedRequest parse(Object rawRequest) {
        Objects.requireNonNull(rawRequest, "rawRequest must not be null");
        Map<String, Object> req = ProtocolUtils.toMap(objectMapper, rawRequest, "rawRequest");

        UnifiedRequest unified = new UnifiedRequest();
        unified.setRequestProtocol("anthropic");
        unified.setResponseProtocol("anthropic");
        unified.setModel(ProtocolUtils.requireString(req, "model", "model is required"));
        unified.setStream(Boolean.TRUE.equals(req.get("stream")));
        unified.setMetadata((Map<String, Object>) req.get("metadata"));
        unified.setSystemPrompt(extractSystemPrompt(req.get("system")));

        // 生成配置
        unified.setGenerationConfig(parseGenerationConfig(req));

        // 解析消息（含 tool_use / tool_result 块处理）
        List<Map<String, Object>> messages = ProtocolUtils.requireList(req, "messages", "messages is required");
        unified.setMessages(parseMessages(messages));

        // 解析工具定义和工具选择
        unified.setTools(parseTools(req.get("tools")));
        unified.setToolChoice(parseToolChoice(req.get("tool_choice")));

        return unified;
    }

    // ===================== 消息解析 =====================

    /** 解析消息列表（含 tool_use / tool_result 块处理） */
    @SuppressWarnings("unchecked")
    private List<UnifiedMessage> parseMessages(List<Map<String, Object>> messages) {
        List<UnifiedMessage> result = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            Map<String, Object> msg = messages.get(i);
            String role = (String) msg.get("role");
            String paramPath = "messages[" + i + "]";

            if ("assistant".equals(role)) {
                parseAssistantMessage(msg, result, paramPath);
            } else if ("user".equals(role)) {
                parseUserMessage(msg, result, paramPath);
            }
            // 跳过未知角色
        }
        return result;
    }

    /** 解析 assistant 消息：提取 text、thinking 和 tool_use 内容块 */
    @SuppressWarnings("unchecked")
    private void parseAssistantMessage(Map<String, Object> msg, List<UnifiedMessage> result, String paramPath) {
        List<Map<String, Object>> blocks = parseContentBlocks(msg.get("content"), paramPath);

        List<UnifiedToolCall> toolCalls = new ArrayList<>();
        List<UnifiedPart> parts = new ArrayList<>();

        for (Map<String, Object> block : blocks) {
            String type = (String) block.get("type");
            if ("text".equals(type)) {
                if (block.get("text") instanceof String text) {
                    parts.add(ProtocolUtils.textPart(text));
                }
            } else if ("thinking".equals(type) || "redacted_thinking".equals(type)) {
                // thinking 内容块，优先取 thinking 字段，其次取 text 字段
                UnifiedPart part = new UnifiedPart();
                part.setType("thinking");
                part.setText(block.get("thinking") instanceof String thinking ? thinking : (String) block.get("text"));
                // 保留 signature 和原始类型信息
                if (block.containsKey("signature")) {
                    Map<String, Object> attributes = new LinkedHashMap<>();
                    attributes.put("signature", block.get("signature"));
                    attributes.put("anthropic_type", type);
                    part.setAttributes(attributes);
                }
                parts.add(part);
            } else if ("tool_use".equals(type)) {
                UnifiedToolCall toolCall = new UnifiedToolCall();
                toolCall.setId((String) block.get("id"));
                toolCall.setType("function");
                toolCall.setToolName((String) block.get("name"));
                // input 是 Object，序列化为 JSON string
                Object input = block.get("input");
                toolCall.setArgumentsJson(input != null ? ProtocolUtils.stringify(objectMapper, input) : "{}");
                toolCalls.add(toolCall);
            }
        }

        // 将 parts 和 toolCalls 放在同一条 assistant 消息中
        // DeepSeek 等 Provider 校验 tool_use 所在消息必须同时包含 thinking 块，
        // 拆分成两条消息会导致 tool_use-only 消息缺失 thinking 而返回 400
        if (!parts.isEmpty() || !toolCalls.isEmpty()) {
            UnifiedMessage assistantMsg = new UnifiedMessage();
            assistantMsg.setRole("assistant");
            assistantMsg.setParts(parts);
            if (!toolCalls.isEmpty()) {
                assistantMsg.setToolCalls(toolCalls);
            }
            result.add(assistantMsg);
        }
    }

    /** 解析 user 消息：提取 text、image 和 tool_result 内容块 */
    @SuppressWarnings("unchecked")
    private void parseUserMessage(Map<String, Object> msg, List<UnifiedMessage> result, String paramPath) {
        List<Map<String, Object>> blocks = parseContentBlocks(msg.get("content"), paramPath);

        List<UnifiedMessage> toolResults = new ArrayList<>();
        List<UnifiedPart> parts = new ArrayList<>();

        for (Map<String, Object> block : blocks) {
            String type = (String) block.get("type");
            if ("text".equals(type)) {
                if (block.get("text") instanceof String text) {
                    parts.add(ProtocolUtils.textPart(text));
                }
            } else if ("image".equals(type)) {
                // Anthropic 图片格式：{type:"image", source:{type:"base64"|"url", ...}}
                parts.add(parseAnthropicImage(block));
            } else if ("tool_result".equals(type)) {
                UnifiedMessage toolMsg = new UnifiedMessage();
                toolMsg.setRole("tool");
                toolMsg.setToolCallId((String) block.get("tool_use_id"));

                // content 可能是字符串或数组
                Object content = block.get("content");
                String text = "";
                if (content instanceof String s) {
                    text = s;
                } else if (content instanceof List<?> contentParts) {
                    StringBuilder sb = new StringBuilder();
                    for (Object partObj : contentParts) {
                        if (partObj instanceof Map<?, ?> pm && "text".equals(pm.get("type"))) {
                            sb.append(pm.get("text"));
                        }
                    }
                    text = sb.toString();
                }

                toolMsg.setParts(List.of(ProtocolUtils.textPart(text)));
                toolResults.add(toolMsg);
            }
        }

        // 先添加文本 user 消息
        if (!parts.isEmpty()) {
            UnifiedMessage textMsg = new UnifiedMessage();
            textMsg.setRole("user");
            textMsg.setParts(parts);
            result.add(textMsg);
        }

        // tool_result 消息（role=tool）
        result.addAll(toolResults);
    }

    // ===================== 内容块与字段解析 =====================

    /** 解析内容块列表 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseContentBlocks(Object content, String paramPath) {
        if (content == null) {
            return List.of();
        }
        // 字符串 content 视为单个 text 块
        if (content instanceof String str) {
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("type", "text");
            block.put("text", str);
            return List.of(block);
        }
        // 数组格式
        if (content instanceof List<?> list) {
            List<Map<String, Object>> blocks = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    blocks.add(ProtocolUtils.toStringMap(map));
                }
            }
            return blocks;
        }
        return List.of();
    }

    /** 解析 Anthropic 图片内容块 */
    @SuppressWarnings("unchecked")
    private UnifiedPart parseAnthropicImage(Map<String, Object> block) {
        UnifiedPart part = new UnifiedPart();
        part.setType("image");

        Object sourceObj = block.get("source");
        if (!(sourceObj instanceof Map<?, ?> source)) {
            return part;
        }

        String sourceType = (String) source.get("type");
        if ("base64".equals(sourceType)) {
            if (source.get("media_type") instanceof String mediaType) {
                part.setMimeType(mediaType);
            }
            if (source.get("data") instanceof String data) {
                part.setBase64Data(data);
            }
        } else if ("url".equals(sourceType)) {
            if (source.get("url") instanceof String url) {
                part.setUrl(url);
            }
        }
        return part;
    }

    /** 从 system 字段提取系统提示词（支持字符串和数组两种格式） */
    @SuppressWarnings("unchecked")
    private String extractSystemPrompt(Object system) {
        if (system == null) {
            return null;
        }
        // 字符串格式
        if (system instanceof String s && !s.isBlank()) {
            return s;
        }
        // 数组格式：[{"type":"text","text":"..."}]
        if (system instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map
                        && "text".equals(map.get("type"))
                        && map.get("text") instanceof String text) {
                    if (!sb.isEmpty()) {
                        sb.append("\n");
                    }
                    sb.append(text);
                }
            }
            return sb.isEmpty() ? null : sb.toString();
        }
        return null;
    }

    // ===================== 工具定义解析 =====================

    /** 解析工具定义（Anthropic 使用 input_schema） */
    @SuppressWarnings("unchecked")
    private List<UnifiedTool> parseTools(Object toolsObj) {
        if (!(toolsObj instanceof List<?> list)) return List.of();
        List<UnifiedTool> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            Map<String, Object> tool = ProtocolUtils.toStringMap(raw);

            String name = (String) tool.get("name");
            if (name == null || name.isBlank()) continue;

            UnifiedTool t = new UnifiedTool();
            t.setName(name);
            t.setDescription((String) tool.get("description"));
            t.setType("function");
            // Anthropic 使用 input_schema（而非 OpenAI 的 parameters）
            t.setInputSchema((Map<String, Object>) tool.get("input_schema"));
            result.add(t);
        }
        return result;
    }

    /** 解析工具选择（auto/any/object 格式） */
    private UnifiedToolChoice parseToolChoice(Object toolChoiceObj) {
        if (toolChoiceObj == null) return null;

        UnifiedToolChoice choice = new UnifiedToolChoice();
        if (toolChoiceObj instanceof String str) {
            if (!STRING_TOOL_CHOICES.contains(str)) {
                throw new ProtocolException(ErrorCode.INVALID_REQUEST, "tool_choice must be auto or any", "tool_choice");
            }
            choice.setType(str);
            return choice;
        }
        // 对象格式：{"type":"tool","name":"xxx"}
        if (toolChoiceObj instanceof Map<?, ?> map) {
            String type = (String) map.get("type");
            if ("tool".equals(type) && map.get("name") instanceof String name && !name.isBlank()) {
                choice.setType("specific");
                choice.setToolName(name);
                return choice;
            }
        }
        return null;
    }

    // ===================== 生成配置解析 =====================

    /** 解析生成配置（含 thinking 映射） */
    @SuppressWarnings("unchecked")
    private UnifiedGenerationConfig parseGenerationConfig(Map<String, Object> req) {
        UnifiedGenerationConfig config = new UnifiedGenerationConfig();
        config.setMaxOutputTokens(req.get("max_tokens") instanceof Number n ? n.intValue() : null);
        config.setTemperature(req.get("temperature") instanceof Number n ? n.doubleValue() : null);
        config.setTopP(req.get("top_p") instanceof Number n ? n.doubleValue() : null);
        config.setTopK(req.get("top_k") instanceof Number n ? n.intValue() : null);

        // stop sequences
        if (req.get("stop_sequences") instanceof List<?> list) {
            config.setStopSequences(list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList());
        }

        // thinking 配置映射到 UnifiedReasoningConfig
        Object thinkingObj = req.get("thinking");
        if (thinkingObj instanceof Map<?, ?> thinkingMap) {
            parseThinking((Map<String, Object>) thinkingMap, config);
        }

        return config;
    }

    /** 解析 Anthropic thinking 配置（支持 enabled / adaptive / disabled） */
    private void parseThinking(Map<String, Object> thinkingMap, UnifiedGenerationConfig config) {
        String thinkingType = (String) thinkingMap.get("type");
        if (thinkingType == null) {
            return;
        }

        // disabled → 显式关闭思考
        if ("disabled".equalsIgnoreCase(thinkingType)) {
            UnifiedReasoningConfig reasoning = new UnifiedReasoningConfig();
            reasoning.setEnabled(false);
            config.setReasoning(reasoning);
            return;
        }

        UnifiedReasoningConfig reasoning = new UnifiedReasoningConfig();

        // enabled / adaptive → 启用思考
        boolean enabled = "enabled".equalsIgnoreCase(thinkingType)
                || "adaptive".equalsIgnoreCase(thinkingType);
        reasoning.setEnabled(enabled);

        if (!enabled) {
            config.setReasoning(reasoning);
            return;
        }

        // budget_tokens（enabled 模式必需，adaptive 模式可选）
        if (thinkingMap.get("budget_tokens") instanceof Number n) {
            reasoning.setBudgetTokens(n.intValue());
        }

        // adaptive 模式的 output_config.effort 映射到 reasoning.effort
        if ("adaptive".equalsIgnoreCase(thinkingType)
                && thinkingMap.get("output_config") instanceof Map<?, ?> outputConfig) {
            @SuppressWarnings("unchecked")
            Map<String, Object> oc = (Map<String, Object>) outputConfig;
            if (oc.get("effort") instanceof String effort && !effort.isBlank()) {
                reasoning.setEffort(effort);
            }
        }

        // summary：仅当用户未显式指定时设置默认值 "auto"，避免覆盖用户意图
        if (reasoning.getSummary() == null || reasoning.getSummary().isBlank()) {
            reasoning.setSummary("auto");
        }
        config.setReasoning(reasoning);
    }
}
