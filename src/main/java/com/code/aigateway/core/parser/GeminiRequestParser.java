package com.code.aigateway.core.parser;

import com.code.aigateway.api.request.GeminiGenerateContentRequest;
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
import java.util.List;
import java.util.Map;

/**
 * Gemini API 请求解析器
 * <p>
 * 将 Gemini generateContent 格式请求转换为统一请求模型。
 * 关键映射：
 * <ul>
 *   <li>systemInstruction.parts[].text → systemPrompt</li>
 *   <li>role="model" → UnifiedMessage.role="assistant"</li>
 *   <li>role="user" → UnifiedMessage.role="user"</li>
 *   <li>functionCall → UnifiedToolCall</li>
 *   <li>functionResponse → role=tool 的 UnifiedMessage</li>
 *   <li>functionDeclarations → UnifiedTool[]</li>
 * </ul>
 * </p>
 */
@Component
public class GeminiRequestParser {

    private final ObjectMapper objectMapper;

    public GeminiRequestParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public UnifiedRequest parse(GeminiGenerateContentRequest request) {
        UnifiedRequest unifiedRequest = new UnifiedRequest();

        unifiedRequest.setRequestProtocol("gemini");
        unifiedRequest.setResponseProtocol("gemini");
        unifiedRequest.setModel(request.getModel());
        unifiedRequest.setStream(Boolean.TRUE.equals(request.getStream()));

        // 系统指令
        if (request.getSystemInstruction() != null && request.getSystemInstruction().getParts() != null) {
            StringBuilder sb = new StringBuilder();
            for (GeminiGenerateContentRequest.Part part : request.getSystemInstruction().getParts()) {
                if (part.getText() != null) {
                    if (!sb.isEmpty()) sb.append("\n");
                    sb.append(part.getText());
                }
            }
            unifiedRequest.setSystemPrompt(sb.length() > 0 ? sb.toString() : null);
        }

        // 生成配置
        if (request.getGenerationConfig() != null) {
            UnifiedGenerationConfig config = new UnifiedGenerationConfig();
            config.setMaxOutputTokens(request.getGenerationConfig().getMaxOutputTokens());
            config.setTemperature(request.getGenerationConfig().getTemperature());
            config.setTopP(request.getGenerationConfig().getTopP());
            config.setStopSequences(request.getGenerationConfig().getStopSequences());
            unifiedRequest.setGenerationConfig(config);
        }

        // 解析消息
        unifiedRequest.setMessages(parseContents(request.getContents()));

        // 解析工具
        unifiedRequest.setTools(parseTools(request.getTools()));

        // 解析工具选择
        unifiedRequest.setToolChoice(parseToolConfig(request.getToolConfig()));

        return unifiedRequest;
    }

    private List<UnifiedMessage> parseContents(List<GeminiGenerateContentRequest.Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return List.of();
        }

        List<UnifiedMessage> messages = new ArrayList<>();
        for (GeminiGenerateContentRequest.Content content : contents) {
            if (content.getParts() == null) continue;

            // 收集文本和 functionCall/functionResponse
            StringBuilder textBuilder = new StringBuilder();
            List<UnifiedToolCall> toolCalls = new ArrayList<>();
            List<GeminiGenerateContentRequest.FunctionResponse> functionResponses = new ArrayList<>();

            for (GeminiGenerateContentRequest.Part part : content.getParts()) {
                if (part.getText() != null) {
                    textBuilder.append(part.getText());
                }
                if (part.getFunctionCall() != null) {
                    UnifiedToolCall toolCall = new UnifiedToolCall();
                    toolCall.setType("function");
                    toolCall.setToolName(part.getFunctionCall().getName());
                    // args 是 Object，需序列化为 JSON string
                    Object args = part.getFunctionCall().getArgs();
                    toolCall.setArgumentsJson(stringify(args));
                    toolCalls.add(toolCall);
                }
                if (part.getFunctionResponse() != null) {
                    functionResponses.add(part.getFunctionResponse());
                }
            }

            // 映射 role：model → assistant
            String role = "model".equals(content.getRole()) ? "assistant" : content.getRole();

            // 添加文本消息
            if (!textBuilder.isEmpty()) {
                UnifiedMessage msg = new UnifiedMessage();
                msg.setRole(role);
                UnifiedPart part = new UnifiedPart();
                part.setType("text");
                part.setText(textBuilder.toString());
                msg.setParts(List.of(part));
                messages.add(msg);
            }

            // 添加工具调用消息（仅 assistant）
            if (!toolCalls.isEmpty()) {
                UnifiedMessage msg = new UnifiedMessage();
                msg.setRole(role);
                msg.setToolCalls(toolCalls);
                msg.setParts(List.of());
                messages.add(msg);
            }

            // 添加工具响应消息
            for (GeminiGenerateContentRequest.FunctionResponse resp : functionResponses) {
                UnifiedMessage msg = new UnifiedMessage();
                msg.setRole("tool");
                // Gemini 的 functionResponse.name 可用于关联，但没有 call_id
                UnifiedPart part = new UnifiedPart();
                part.setType("text");
                part.setText(stringify(resp.getResponse()));
                msg.setParts(List.of(part));
                messages.add(msg);
            }
        }
        return messages;
    }

    private List<UnifiedTool> parseTools(List<GeminiGenerateContentRequest.Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        List<UnifiedTool> result = new ArrayList<>();
        for (GeminiGenerateContentRequest.Tool tool : tools) {
            if (tool.getFunctionDeclarations() == null) continue;
            for (GeminiGenerateContentRequest.FunctionDeclaration decl : tool.getFunctionDeclarations()) {
                UnifiedTool unifiedTool = new UnifiedTool();
                unifiedTool.setName(decl.getName());
                unifiedTool.setDescription(decl.getDescription());
                unifiedTool.setType("function");
                unifiedTool.setInputSchema(decl.getParameters());
                result.add(unifiedTool);
            }
        }
        return result;
    }

    private UnifiedToolChoice parseToolConfig(GeminiGenerateContentRequest.ToolConfig config) {
        if (config == null || config.getFunctionCallingConfig() == null) {
            return null;
        }
        String mode = config.getFunctionCallingConfig().getMode();
        UnifiedToolChoice choice = new UnifiedToolChoice();
        if (mode == null) return null;

        return switch (mode.toUpperCase()) {
            case "AUTO" -> { choice.setType("auto"); yield choice; }
            case "NONE" -> { choice.setType("none"); yield choice; }
            case "ANY" -> { choice.setType("any"); yield choice; }
            default -> null;
        };
    }

    private String stringify(Object obj) {
        if (obj == null) return "{}";
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
