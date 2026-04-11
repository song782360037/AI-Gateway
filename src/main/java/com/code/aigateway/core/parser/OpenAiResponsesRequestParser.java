package com.code.aigateway.core.parser;

import com.code.aigateway.api.request.OpenAiResponsesRequest;
import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.model.UnifiedGenerationConfig;
import com.code.aigateway.core.model.UnifiedMessage;
import com.code.aigateway.core.model.UnifiedPart;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedReasoningConfig;
import com.code.aigateway.core.model.UnifiedTool;
import com.code.aigateway.core.model.UnifiedToolCall;
import com.code.aigateway.core.model.UnifiedToolChoice;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpenAI Responses API 请求解析器
 * <p>
 * 将 Responses API 格式请求转换为统一请求模型。
 * 关键映射：
 * <ul>
 *   <li>instructions → systemPrompt</li>
 *   <li>input[] → UnifiedMessage[]（message / function_call / function_call_output 三种 item 类型）</li>
 *   <li>max_output_tokens → generationConfig.maxOutputTokens</li>
 * </ul>
 * </p>
 */
@Component
public class OpenAiResponsesRequestParser {

    private static final Set<String> STRING_TOOL_CHOICES = Set.of("auto", "none", "required");
    private static final Set<String> VALID_REASONING_EFFORTS = Set.of("low", "medium", "high");

    public UnifiedRequest parse(OpenAiResponsesRequest request) {
        UnifiedRequest unifiedRequest = new UnifiedRequest();

        unifiedRequest.setRequestProtocol("openai-responses");
        unifiedRequest.setResponseProtocol("openai-responses");
        unifiedRequest.setModel(request.getModel());
        unifiedRequest.setStream(Boolean.TRUE.equals(request.getStream()));
        unifiedRequest.setMetadata(request.getMetadata());
        unifiedRequest.setSystemPrompt(request.getInstructions());

        // 生成配置
        UnifiedGenerationConfig config = new UnifiedGenerationConfig();
        config.setTemperature(request.getTemperature());
        config.setMaxOutputTokens(request.getMaxOutputTokens());
        config.setParallelToolCalls(request.getParallelToolCalls());
        config.setStopSequences(parseStopSequences(request.getStop()));
        Map<String, Object> reasoning = request.getReasoning();
        if (reasoning != null) {
            Object effort = reasoning.get("effort");
            Object summary = reasoning.get("summary");
            String effortValue = null;
            if (effort instanceof String value && !value.isBlank()) {
                validateReasoningEffort(value);
                effortValue = value;
            }
            boolean hasSummary = summary instanceof String summaryValue && !summaryValue.isBlank();
            if (effortValue != null || hasSummary) {
                UnifiedReasoningConfig unifiedReasoning = new UnifiedReasoningConfig();
                unifiedReasoning.setEnabled(true);
                if (effortValue != null) {
                    unifiedReasoning.setEffort(effortValue);
                }
                if (hasSummary) {
                    unifiedReasoning.setSummary((String) summary);
                }
                config.setReasoning(unifiedReasoning);
            }
        }
        unifiedRequest.setGenerationConfig(config);

        // 解析 input 数组为消息列表
        unifiedRequest.setMessages(parseInputItems(request.getInput()));
        List<UnifiedTool> unifiedTools = parseTools(request.getTools());
        unifiedRequest.setTools(unifiedTools);
        unifiedRequest.setToolChoice(normalizeToolChoice(parseToolChoice(request.getToolChoice()), unifiedTools));

        if (reasoning != null) {
            Map<String, Object> metadata = unifiedRequest.getMetadata() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(unifiedRequest.getMetadata());
            metadata.put("openai_reasoning", reasoning);
            unifiedRequest.setMetadata(metadata);
        }

        return unifiedRequest;
    }

    /**
     * 解析 input 数组
     * <p>
     * 将三种 item 类型映射为 UnifiedMessage：
     * <ul>
     *   <li>type=message → role 对应的 UnifiedMessage</li>
     *   <li>type=function_call → role=assistant 的 UnifiedMessage + toolCalls</li>
     *   <li>type=function_call_output → role=tool 的 UnifiedMessage</li>
     * </ul>
     * </p>
     */
    private List<UnifiedMessage> parseInputItems(List<OpenAiResponsesRequest.InputItem> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }

        List<UnifiedMessage> messages = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            OpenAiResponsesRequest.InputItem item = input.get(i);
            String paramPath = "input[" + i + "]";

            if (item == null || item.getType() == null) {
                continue;
            }

            switch (item.getType()) {
                case "message" -> parseMessageItem(item, messages, paramPath);
                case "function_call" -> parseFunctionCallItem(item, messages, paramPath);
                case "function_call_output" -> parseFunctionCallOutputItem(item, messages, paramPath);
                default -> { /* 跳过未知类型 */ }
            }
        }
        return messages;
    }

    private void parseMessageItem(OpenAiResponsesRequest.InputItem item, List<UnifiedMessage> messages, String paramPath) {
        UnifiedMessage message = new UnifiedMessage();
        message.setRole(item.getRole());
        message.setParts(parseContent(item.getContent(), paramPath));
        messages.add(message);
    }

    private void parseFunctionCallItem(OpenAiResponsesRequest.InputItem item, List<UnifiedMessage> messages, String paramPath) {
        UnifiedMessage message = new UnifiedMessage();
        message.setRole("assistant");

        UnifiedToolCall toolCall = new UnifiedToolCall();
        toolCall.setId(item.getCallId());
        toolCall.setType("function");
        toolCall.setToolName(item.getName());
        toolCall.setArgumentsJson(item.getArguments());
        message.setToolCalls(List.of(toolCall));

        // assistant 消息可以包含空文本内容
        message.setParts(List.of());
        messages.add(message);
    }

    private void parseFunctionCallOutputItem(OpenAiResponsesRequest.InputItem item, List<UnifiedMessage> messages, String paramPath) {
        UnifiedMessage message = new UnifiedMessage();
        message.setRole("tool");
        message.setToolCallId(item.getCallId());

        UnifiedPart part = new UnifiedPart();
        part.setType("text");
        part.setText(item.getOutput());
        message.setParts(List.of(part));

        messages.add(message);
    }

    private List<UnifiedPart> parseContent(Object content, String paramPath) {
        List<UnifiedPart> parts = new ArrayList<>();
        if (content == null) {
            return parts;
        }
        if (content instanceof String str) {
            UnifiedPart part = new UnifiedPart();
            part.setType("text");
            part.setText(str);
            parts.add(part);
            return parts;
        }
        if (content instanceof List<?> contentItems) {
            for (int i = 0; i < contentItems.size(); i++) {
                Object item = contentItems.get(i);
                String itemParamPath = paramPath + "[" + i + "]";
                if (!(item instanceof Map<?, ?> map)) {
                    throw new GatewayException(ErrorCode.INVALID_REQUEST,
                            "content item must be an object", itemParamPath);
                }
                Object type = map.get("type");
                if (!(type instanceof String typeValue) || typeValue.isBlank()) {
                    throw new GatewayException(ErrorCode.INVALID_REQUEST,
                            "content item type is required", itemParamPath + ".type");
                }
                if (!"input_text".equals(typeValue) && !"text".equals(typeValue)) {
                    throw new GatewayException(ErrorCode.INVALID_REQUEST,
                            "unsupported content type: " + typeValue, itemParamPath + ".type");
                }
                Object text = map.get("text");
                if (!(text instanceof String textValue)) {
                    throw new GatewayException(ErrorCode.INVALID_REQUEST,
                            "content text must be a string", itemParamPath + ".text");
                }
                UnifiedPart part = new UnifiedPart();
                part.setType("text");
                part.setText(textValue);
                parts.add(part);
            }
            return parts;
        }
        throw new GatewayException(ErrorCode.INVALID_REQUEST,
                "content must be a string or array", paramPath);
    }

    private void validateReasoningEffort(String effort) {
        if (!VALID_REASONING_EFFORTS.contains(effort)) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST,
                    "reasoning.effort must be one of low, medium, high", "reasoning.effort");
        }
    }

    private List<UnifiedTool> parseTools(List<OpenAiResponsesRequest.ToolDef> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        List<UnifiedTool> result = new ArrayList<>();
        for (OpenAiResponsesRequest.ToolDef tool : tools) {
            UnifiedTool unifiedTool = new UnifiedTool();
            unifiedTool.setType(tool.getType() != null ? tool.getType() : "function");

            // 兼容两种格式：优先使用嵌套的 function 对象，回退到扁平字段
            if (tool.getFunction() != null) {
                // 嵌套格式：{type, function: {name, description, parameters}}
                unifiedTool.setName(tool.getFunction().getName());
                unifiedTool.setDescription(tool.getFunction().getDescription());
                unifiedTool.setInputSchema(tool.getFunction().getParameters());
                unifiedTool.setStrict(tool.getFunction().getStrict());
            } else {
                // 扁平格式：{type, name, description, parameters}
                unifiedTool.setName(tool.getName());
                unifiedTool.setDescription(tool.getDescription());
                unifiedTool.setInputSchema(tool.getParameters());
                unifiedTool.setStrict(tool.getStrict());
            }

            if (unifiedTool.getName() == null || unifiedTool.getName().isBlank()) {
                continue;
            }
            result.add(unifiedTool);
        }
        return result;
    }

    private List<String> parseStopSequences(Object stop) {
        if (stop == null) {
            return null;
        }
        if (stop instanceof String stopSequence) {
            return stopSequence.isBlank() ? null : List.of(stopSequence);
        }
        if (stop instanceof List<?> stopList) {
            List<String> stopSequences = stopList.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(value -> !value.isBlank())
                    .toList();
            return stopSequences.isEmpty() ? null : stopSequences;
        }
        return null;
    }

    private UnifiedToolChoice parseToolChoice(Object toolChoiceObj) {
        if (toolChoiceObj == null) {
            return null;
        }
        UnifiedToolChoice choice = new UnifiedToolChoice();
        if (toolChoiceObj instanceof String str) {
            if (!STRING_TOOL_CHOICES.contains(str)) {
                throw new GatewayException(ErrorCode.INVALID_REQUEST, "tool_choice must be one of auto, none, required", "tool_choice");
            }
            choice.setType(str);
            return choice;
        }
        if (!(toolChoiceObj instanceof java.util.Map<?, ?> map)) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, "tool_choice must be a string or object", "tool_choice");
        }

        Object typeObj = map.get("type");
        if (!(typeObj instanceof String type) || !"function".equals(type)) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, "tool_choice.type must be function", "tool_choice.type");
        }

        Object nameObj = map.get("name");
        if (!(nameObj instanceof String toolName) || toolName.isBlank()) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, "tool_choice.name is required", "tool_choice.name");
        }

        choice.setType("specific");
        choice.setToolName(toolName);
        return choice;
    }

    private UnifiedToolChoice normalizeToolChoice(UnifiedToolChoice toolChoice, List<UnifiedTool> tools) {
        if (toolChoice == null || !"specific".equals(toolChoice.getType())) {
            return toolChoice;
        }
        if (tools == null || tools.isEmpty()) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, "tool_choice requires non-empty tools", "tool_choice");
        }
        boolean toolExists = tools.stream()
                .map(UnifiedTool::getName)
                .anyMatch(toolChoice.getToolName()::equals);
        if (!toolExists) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST,
                    "tool_choice.name must match one of tools", "tool_choice.name");
        }
        return toolChoice;
    }
}
