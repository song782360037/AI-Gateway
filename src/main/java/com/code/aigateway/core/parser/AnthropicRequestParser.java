package com.code.aigateway.core.parser;

import com.code.aigateway.api.request.AnthropicMessagesRequest;
import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.model.UnifiedGenerationConfig;
import com.code.aigateway.core.model.UnifiedMessage;
import com.code.aigateway.core.model.UnifiedPart;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedTool;
import com.code.aigateway.core.model.UnifiedToolCall;
import com.code.aigateway.core.model.UnifiedToolChoice;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Anthropic Messages API 请求解析器
 * <p>
 * 将 Anthropic Messages API 格式请求转换为统一请求模型。
 * 关键映射：
 * <ul>
 *   <li>system → systemPrompt</li>
 *   <li>messages[] → UnifiedMessage[]（Anthropic 要求 user/assistant 交替）</li>
 *   <li>assistant 消息中的 tool_use 块 → UnifiedToolCall</li>
 *   <li>user 消息中的 tool_result 块 → role=tool 的 UnifiedMessage</li>
 *   <li>tools[].input_schema → UnifiedTool.inputSchema</li>
 *   <li>max_tokens → generationConfig.maxOutputTokens（Anthropic 必填）</li>
 * </ul>
 * </p>
 */
@Component
public class AnthropicRequestParser {

    private static final Set<String> STRING_TOOL_CHOICES = Set.of("auto", "any");

    private final ObjectMapper objectMapper;

    public AnthropicRequestParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public UnifiedRequest parse(AnthropicMessagesRequest request) {
        UnifiedRequest unifiedRequest = new UnifiedRequest();

        unifiedRequest.setRequestProtocol("anthropic");
        unifiedRequest.setResponseProtocol("anthropic");
        unifiedRequest.setModel(request.getModel());
        unifiedRequest.setStream(Boolean.TRUE.equals(request.getStream()));
        unifiedRequest.setMetadata(request.getMetadata());
        unifiedRequest.setSystemPrompt(request.getSystem());

        // 生成配置
        UnifiedGenerationConfig config = new UnifiedGenerationConfig();
        config.setMaxOutputTokens(request.getMaxTokens());
        config.setTemperature(request.getTemperature());
        config.setTopP(request.getTopP());
        config.setStopSequences(request.getStopSequences());
        unifiedRequest.setGenerationConfig(config);

        // 解析消息（含 tool_use / tool_result 块处理）
        unifiedRequest.setMessages(parseMessages(request.getMessages()));

        // 解析工具定义
        unifiedRequest.setTools(parseTools(request.getTools()));

        // 解析工具选择
        unifiedRequest.setToolChoice(parseToolChoice(request.getToolChoice()));

        return unifiedRequest;
    }

    /**
     * 解析消息列表
     * <p>
     * Anthropic 消息中可能包含 tool_use（assistant）和 tool_result（user）内容块，
     * 需要将其拆分为独立的 UnifiedMessage。
     * </p>
     */
    private List<UnifiedMessage> parseMessages(List<AnthropicMessagesRequest.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        List<UnifiedMessage> result = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            AnthropicMessagesRequest.Message msg = messages.get(i);
            String paramPath = "messages[" + i + "]";

            if ("assistant".equals(msg.getRole())) {
                parseAssistantMessage(msg, result, paramPath);
            } else if ("user".equals(msg.getRole())) {
                parseUserMessage(msg, result, paramPath);
            } else {
                // 跳过未知角色
            }
        }
        return result;
    }

    /**
     * 解析 assistant 消息
     * <p>
     * 从 content 块中提取 text 和 tool_use，生成对应的 UnifiedMessage。
     * tool_use 块生成 role=assistant 的 UnifiedMessage + toolCalls。
     * </p>
     */
    private void parseAssistantMessage(AnthropicMessagesRequest.Message msg, List<UnifiedMessage> result, String paramPath) {
        List<Map<String, Object>> blocks = parseContentBlocks(msg.getContent(), paramPath);

        List<UnifiedToolCall> toolCalls = new ArrayList<>();
        StringBuilder textBuilder = new StringBuilder();

        for (Map<String, Object> block : blocks) {
            String type = (String) block.get("type");
            if ("text".equals(type)) {
                if (block.get("text") instanceof String text) {
                    textBuilder.append(text);
                }
            } else if ("tool_use".equals(type)) {
                UnifiedToolCall toolCall = new UnifiedToolCall();
                toolCall.setId((String) block.get("id"));
                toolCall.setType("function");
                toolCall.setToolName((String) block.get("name"));
                // input 是一个 Object，需要序列化为 JSON string
                Object input = block.get("input");
                toolCall.setArgumentsJson(input != null ? stringify(input) : "{}");
                toolCalls.add(toolCall);
            }
        }

        // 如果有文本内容，先添加文本消息
        if (!textBuilder.isEmpty()) {
            UnifiedMessage textMsg = new UnifiedMessage();
            textMsg.setRole("assistant");
            UnifiedPart textPart = new UnifiedPart();
            textPart.setType("text");
            textPart.setText(textBuilder.toString());
            textMsg.setParts(List.of(textPart));
            result.add(textMsg);
        }

        // 如果有工具调用，添加 toolCalls 消息
        if (!toolCalls.isEmpty()) {
            UnifiedMessage toolMsg = new UnifiedMessage();
            toolMsg.setRole("assistant");
            toolMsg.setToolCalls(toolCalls);
            toolMsg.setParts(List.of());
            result.add(toolMsg);
        }
    }

    /**
     * 解析 user 消息
     * <p>
     * 从 content 块中提取 text 和 tool_result。
     * tool_result 块生成 role=tool 的 UnifiedMessage（每个 tool_result 一条）。
     * text 块生成普通 user 消息。
     * 连续的 tool_result 需合并为单条 user 消息（Anthropic 要求 user/assistant 交替）。
     * </p>
     */
    private void parseUserMessage(AnthropicMessagesRequest.Message msg, List<UnifiedMessage> result, String paramPath) {
        List<Map<String, Object>> blocks = parseContentBlocks(msg.getContent(), paramPath);

        List<UnifiedMessage> toolResults = new ArrayList<>();
        StringBuilder textBuilder = new StringBuilder();

        for (Map<String, Object> block : blocks) {
            String type = (String) block.get("type");
            if ("text".equals(type)) {
                if (block.get("text") instanceof String text) {
                    textBuilder.append(text);
                }
            } else if ("tool_result".equals(type)) {
                UnifiedMessage toolMsg = new UnifiedMessage();
                toolMsg.setRole("tool");
                toolMsg.setToolCallId((String) block.get("tool_use_id"));

                // content 可能是字符串或数组
                Object content = block.get("content");
                String text = "";
                if (content instanceof String s) {
                    text = s;
                } else if (content instanceof List<?> parts) {
                    StringBuilder sb = new StringBuilder();
                    for (Object part : parts) {
                        if (part instanceof Map<?, ?> pm && "text".equals(pm.get("type"))) {
                            sb.append(pm.get("text"));
                        }
                    }
                    text = sb.toString();
                }

                UnifiedPart part = new UnifiedPart();
                part.setType("text");
                part.setText(text);
                toolMsg.setParts(List.of(part));
                toolResults.add(toolMsg);
            }
        }

        // 先添加文本 user 消息
        if (!textBuilder.isEmpty()) {
            UnifiedMessage textMsg = new UnifiedMessage();
            textMsg.setRole("user");
            UnifiedPart textPart = new UnifiedPart();
            textPart.setType("text");
            textPart.setText(textBuilder.toString());
            textMsg.setParts(List.of(textPart));
            result.add(textMsg);
        }

        // tool_result 消息（role=tool）
        result.addAll(toolResults);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseContentBlocks(Object content, String paramPath) {
        if (content == null) {
            return List.of();
        }
        if (content instanceof String) {
            // 字符串 content 视为单个 text 块
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("type", "text");
            block.put("text", content);
            return List.of(block);
        }
        if (content instanceof List<?> list) {
            List<Map<String, Object>> blocks = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    blocks.add((Map<String, Object>) map);
                }
            }
            return blocks;
        }
        return List.of();
    }

    private List<UnifiedTool> parseTools(List<AnthropicMessagesRequest.Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        List<UnifiedTool> result = new ArrayList<>();
        for (AnthropicMessagesRequest.Tool tool : tools) {
            UnifiedTool unifiedTool = new UnifiedTool();
            unifiedTool.setName(tool.getName());
            unifiedTool.setDescription(tool.getDescription());
            unifiedTool.setType("function");
            // Anthropic 使用 input_schema
            unifiedTool.setInputSchema(tool.getInputSchema());
            result.add(unifiedTool);
        }
        return result;
    }

    private UnifiedToolChoice parseToolChoice(Object toolChoiceObj) {
        if (toolChoiceObj == null) {
            return null;
        }
        UnifiedToolChoice choice = new UnifiedToolChoice();
        if (toolChoiceObj instanceof String str) {
            if (!STRING_TOOL_CHOICES.contains(str)) {
                throw new GatewayException(ErrorCode.INVALID_REQUEST, "tool_choice must be auto or any", "tool_choice");
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

    private String stringify(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
