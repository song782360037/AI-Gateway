package com.code.aigateway.core.parser;

import com.code.aigateway.api.request.OpenAiResponsesRequest;
import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.model.UnifiedGenerationConfig;
import com.code.aigateway.core.model.UnifiedMessage;
import com.code.aigateway.core.model.UnifiedPart;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedTool;
import com.code.aigateway.core.model.UnifiedToolCall;
import com.code.aigateway.core.model.UnifiedToolChoice;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
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
        unifiedRequest.setGenerationConfig(config);

        // 解析 input 数组为消息列表
        unifiedRequest.setMessages(parseInputItems(request.getInput()));
        unifiedRequest.setTools(parseTools(request.getTools()));
        unifiedRequest.setToolChoice(parseToolChoice(request.getToolChoice()));

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
        // 数组格式的 content 暂不处理（Responses API 的 message content 通常为字符串）
        return parts;
    }

    private List<UnifiedTool> parseTools(List<OpenAiResponsesRequest.ToolDef> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        List<UnifiedTool> result = new ArrayList<>();
        for (OpenAiResponsesRequest.ToolDef tool : tools) {
            if (tool.getFunction() == null) continue;
            UnifiedTool unifiedTool = new UnifiedTool();
            unifiedTool.setName(tool.getFunction().getName());
            unifiedTool.setDescription(tool.getFunction().getDescription());
            unifiedTool.setType(tool.getType());
            unifiedTool.setStrict(tool.getFunction().getStrict());
            unifiedTool.setInputSchema(tool.getFunction().getParameters());
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
                throw new GatewayException(ErrorCode.INVALID_REQUEST, "tool_choice must be one of auto, none, required", "tool_choice");
            }
            choice.setType(str);
            return choice;
        }
        // 对象格式的 tool_choice（{"type":"function","name":"xxx"}）
        if (toolChoiceObj instanceof java.util.Map<?, ?> map) {
            Object nameObj = map.get("name");
            if (nameObj instanceof String toolName && !toolName.isBlank()) {
                choice.setType("specific");
                choice.setToolName(toolName);
                return choice;
            }
        }
        return null;
    }
}
