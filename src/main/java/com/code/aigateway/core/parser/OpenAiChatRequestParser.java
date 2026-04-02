package com.code.aigateway.core.parser;

import com.code.aigateway.api.request.OpenAiChatCompletionRequest;
import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.model.UnifiedGenerationConfig;
import com.code.aigateway.core.model.UnifiedMessage;
import com.code.aigateway.core.model.UnifiedPart;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedResponseFormat;
import com.code.aigateway.core.model.UnifiedReasoningConfig;
import com.code.aigateway.core.model.UnifiedTool;
import com.code.aigateway.core.model.UnifiedToolCall;
import com.code.aigateway.core.model.UnifiedToolChoice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpenAI 聊天请求解析器
 * <p>
 * 将 OpenAI 格式的聊天请求转换为统一的请求模型，支持以下特性：
 * <ul>
 *   <li>System Prompt 提取：将字符串类型的 system 消息合并为 systemPrompt</li>
 *   <li>多模态内容：支持 content 为字符串或数组格式</li>
 *   <li>图片输入：支持 image_url 类型的多模态内容</li>
 *   <li>工具调用：支持 tools、tool_choice 和 assistant 历史 tool_calls</li>
 *   <li>结构化输出：支持 response_format</li>
 * </ul>
 * </p>
 *
 * @author sst
 */
@Component
@Slf4j
public class OpenAiChatRequestParser implements RequestParser<OpenAiChatCompletionRequest, UnifiedRequest> {

    private static final Set<String> STRING_TOOL_CHOICES = Set.of("auto", "none", "required");
    private static final Set<String> RESPONSE_FORMAT_TYPES = Set.of("text", "json_object", "json_schema");
    private static final String SPECIFIC_TOOL_CHOICE = "specific";

    /**
     * 将 OpenAI 格式的请求转换为统一格式
     *
     * @param request OpenAI 格式的聊天请求
     * @return 统一格式的请求
     */
    @Override
    public UnifiedRequest parse(OpenAiChatCompletionRequest request) {
        UnifiedRequest unifiedRequest = new UnifiedRequest();

        // 设置协议类型
        unifiedRequest.setRequestProtocol("openai-chat");
        unifiedRequest.setResponseProtocol("openai-chat");
        unifiedRequest.setModel(request.getModel());
        unifiedRequest.setStream(Boolean.TRUE.equals(request.getStream()));
        unifiedRequest.setMetadata(request.getMetadata());
        unifiedRequest.setGenerationConfig(buildGenerationConfig(request));
        unifiedRequest.setResponseFormat(parseResponseFormat(request.getResponseFormat()));

        ParsedMessages parsedMessages = parseMessages(request.getMessages());
        unifiedRequest.setSystemPrompt(parsedMessages.systemPrompt());
        unifiedRequest.setMessages(parsedMessages.messages());

        List<UnifiedTool> tools = parseTools(request.getTools());
        unifiedRequest.setTools(tools);
        unifiedRequest.setToolChoice(normalizeToolChoice(parseToolChoice(request.getToolChoice()), tools));

        // 工具调用诊断日志：记录解析阶段的 tools 状态
        log.info("[Parser] model={}, stream={}, 原始tools={}, 解析后unifiedTools={}, toolChoice={}",
                request.getModel(), request.getStream(),
                request.getTools() != null ? request.getTools().size() : "null",
                tools.size(),
                request.getToolChoice());

        return unifiedRequest;
    }

    /**
     * 构建统一生成配置
     *
     * @param request OpenAI 请求
     * @return 统一生成配置
     */
    private UnifiedGenerationConfig buildGenerationConfig(OpenAiChatCompletionRequest request) {
        UnifiedGenerationConfig config = new UnifiedGenerationConfig();
        config.setTemperature(request.getTemperature());
        config.setTopP(request.getTopP());
        config.setMaxOutputTokens(resolveMaxOutputTokens(request.getMaxTokens(), request.getMaxCompletionTokens()));
        if (request.getReasoningEffort() != null && !request.getReasoningEffort().isBlank()) {
            UnifiedReasoningConfig reasoning = new UnifiedReasoningConfig();
            reasoning.setEnabled(true);
            reasoning.setEffort(request.getReasoningEffort());
            config.setReasoning(reasoning);
        }
        config.setStopSequences(request.getStop());
        config.setParallelToolCalls(request.getParallelToolCalls());
        return config;
    }

    /**
     * 解析消息列表并抽取 system prompt
     *
     * @param messages 原始消息列表
     * @return 解析结果
     */
    private ParsedMessages parseMessages(List<OpenAiChatCompletionRequest.OpenAiMessage> messages) {
        List<UnifiedMessage> unifiedMessages = new ArrayList<>();
        List<String> systemPrompts = new ArrayList<>();

        for (int i = 0; i < messages.size(); i++) {
            OpenAiChatCompletionRequest.OpenAiMessage message = messages.get(i);

            // 字符串 system 消息统一合并进 system prompt；非字符串 system 消息保留原顺序
            if ("system".equalsIgnoreCase(message.getRole()) && message.getContent() instanceof String str) {
                systemPrompts.add(str);
                continue;
            }

            UnifiedMessage unifiedMessage = new UnifiedMessage();
            unifiedMessage.setRole(message.getRole());
            unifiedMessage.setToolCallId(message.getToolCallId());
            unifiedMessage.setParts(parseContent(message.getContent(), "messages[" + i + "].content"));
            unifiedMessage.setToolCalls(parseMessageToolCalls(message.getToolCalls(), "messages[" + i + "].tool_calls"));
            unifiedMessages.add(unifiedMessage);
        }

        String systemPrompt = systemPrompts.isEmpty() ? null : String.join("\n\n", systemPrompts);
        return new ParsedMessages(systemPrompt, unifiedMessages);
    }

    /**
     * 解析消息内容
     *
     * @param content   消息内容
     * @param paramPath 参数路径
     * @return 统一内容片段列表
     */
    private List<UnifiedPart> parseContent(Object content, String paramPath) {
        List<UnifiedPart> parts = new ArrayList<>();
        if (content == null) {
            return parts;
        }

        if (content instanceof String str) {
            parts.add(createTextPart(str));
            return parts;
        }

        if (!(content instanceof List<?> list)) {
            throw invalidRequest(paramPath, paramPath + " must be a string or array");
        }

        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof Map<?, ?> map)) {
                throw invalidRequest(paramPath + "[" + i + "]", paramPath + "[" + i + "] must be an object");
            }
            parts.add(parseContentPart(map, paramPath + "[" + i + "]"));
        }
        return parts;
    }

    /**
     * 解析单个内容片段
     *
     * @param map       内容对象
     * @param paramPath 参数路径
     * @return 统一内容片段
     */
    private UnifiedPart parseContentPart(Map<?, ?> map, String paramPath) {
        Object typeObj = map.get("type");
        if (!(typeObj instanceof String type)) {
            throw invalidRequest(paramPath + ".type", paramPath + ".type must be a string");
        }

        if ("text".equals(type)) {
            Object textObj = map.get("text");
            if (!(textObj instanceof String text)) {
                throw invalidRequest(paramPath + ".text", paramPath + ".text must be a string");
            }
            return createTextPart(text);
        }

        if ("image_url".equals(type)) {
            return parseImagePart(map.get("image_url"), paramPath + ".image_url");
        }

        throw invalidRequest(paramPath + ".type", "unsupported content type: " + type);
    }

    /**
     * 解析图片片段
     *
     * @param imageUrlObj 图片对象
     * @param paramPath   参数路径
     * @return 统一图片片段
     */
    private UnifiedPart parseImagePart(Object imageUrlObj, String paramPath) {
        if (!(imageUrlObj instanceof Map<?, ?> imageMap)) {
            throw invalidRequest(paramPath, paramPath + " must be an object");
        }

        Object urlObj = imageMap.get("url");
        if (!(urlObj instanceof String url) || url.isBlank()) {
            throw invalidRequest(paramPath + ".url", paramPath + ".url is required");
        }

        UnifiedPart part = new UnifiedPart();
        part.setType("image");

        Object detail = imageMap.get("detail");
        if (detail instanceof String detailValue) {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("detail", detailValue);
            part.setAttributes(attributes);
        }

        if (url.startsWith("data:")) {
            DataUrl dataUrl = parseDataUrl(url, paramPath + ".url");
            part.setMimeType(dataUrl.mimeType());
            part.setBase64Data(dataUrl.base64Data());
            return part;
        }

        part.setUrl(url);
        return part;
    }

    /**
     * 解析图片 data URL
     *
     * @param url       原始 data URL
     * @param paramPath 参数路径
     * @return 解析结果
     */
    private DataUrl parseDataUrl(String url, String paramPath) {
        int commaIndex = url.indexOf(',');
        if (commaIndex <= 5) {
            throw invalidRequest(paramPath, paramPath + " is not a valid data url");
        }

        String metadata = url.substring(5, commaIndex);
        String data = url.substring(commaIndex + 1);
        if (data.isBlank()) {
            throw invalidRequest(paramPath, paramPath + " is missing base64 data");
        }

        String[] metadataParts = metadata.split(";");
        if (metadataParts.length < 2 || !"base64".equalsIgnoreCase(metadataParts[metadataParts.length - 1])) {
            throw invalidRequest(paramPath, paramPath + " must be base64 encoded");
        }

        String mimeType = metadataParts[0].isBlank() ? "application/octet-stream" : metadataParts[0];
        return new DataUrl(mimeType, data);
    }

    /**
     * 解析工具定义列表
     *
     * @param tools OpenAI 工具列表
     * @return 统一工具列表
     */
    private List<UnifiedTool> parseTools(List<OpenAiChatCompletionRequest.OpenAiTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }

        List<UnifiedTool> result = new ArrayList<>();
        for (int i = 0; i < tools.size(); i++) {
            OpenAiChatCompletionRequest.OpenAiTool tool = tools.get(i);
            String paramPath = "tools[" + i + "]";
            if (tool.getFunction() == null) {
                throw invalidRequest(paramPath + ".function", paramPath + ".function is required");
            }

            String name = tool.getFunction().getName();
            if (name == null || name.isBlank()) {
                throw invalidRequest(paramPath + ".function.name", paramPath + ".function.name is required");
            }

            UnifiedTool unifiedTool = new UnifiedTool();
            unifiedTool.setName(name);
            unifiedTool.setDescription(tool.getFunction().getDescription());
            unifiedTool.setType(tool.getType());
            unifiedTool.setStrict(tool.getFunction().getStrict());
            unifiedTool.setInputSchema(tool.getFunction().getParameters());
            result.add(unifiedTool);
        }
        return result;
    }

    /**
     * 解析工具选择配置
     *
     * @param toolChoiceObj 工具选择配置
     * @return 统一工具选择
     */
    private UnifiedToolChoice parseToolChoice(Object toolChoiceObj) {
        if (toolChoiceObj == null) {
            return null;
        }

        UnifiedToolChoice choice = new UnifiedToolChoice();
        if (toolChoiceObj instanceof String str) {
            if (!STRING_TOOL_CHOICES.contains(str)) {
                throw invalidRequest("tool_choice", "tool_choice must be one of auto, none, required");
            }
            choice.setType(str);
            return choice;
        }

        if (!(toolChoiceObj instanceof Map<?, ?> map)) {
            throw invalidRequest("tool_choice", "tool_choice must be a string or object");
        }

        Object typeObj = map.get("type");
        if (!(typeObj instanceof String type) || !"function".equals(type)) {
            throw invalidRequest("tool_choice.type", "tool_choice.type must be function");
        }

        Object functionObj = map.get("function");
        if (!(functionObj instanceof Map<?, ?> functionMap)) {
            throw invalidRequest("tool_choice.function", "tool_choice.function is required");
        }

        Object nameObj = functionMap.get("name");
        if (!(nameObj instanceof String toolName) || toolName.isBlank()) {
            throw invalidRequest("tool_choice.function.name", "tool_choice.function.name is required");
        }

        choice.setType(SPECIFIC_TOOL_CHOICE);
        choice.setToolName(toolName);
        return choice;
    }

    /**
     * 规范化工具选择配置
     *
     * @param toolChoice 原始工具选择
     * @param tools      工具列表
     * @return 规范化后的工具选择
     */
    private UnifiedToolChoice normalizeToolChoice(UnifiedToolChoice toolChoice, List<UnifiedTool> tools) {
        if (toolChoice == null) {
            return null;
        }
        if (!SPECIFIC_TOOL_CHOICE.equals(toolChoice.getType())) {
            return toolChoice;
        }
        if (tools == null || tools.isEmpty()) {
            throw invalidRequest("tool_choice", "tool_choice requires non-empty tools");
        }

        boolean toolExists = tools.stream()
                .map(UnifiedTool::getName)
                .anyMatch(toolChoice.getToolName()::equals);
        if (!toolExists) {
            throw invalidRequest("tool_choice.function.name", "tool_choice.function.name must match one of tools");
        }
        return toolChoice;
    }

    /**
     * 解析 assistant 历史消息中的工具调用
     *
     * @param toolCalls  工具调用列表
     * @param paramPath 参数路径
     * @return 统一工具调用列表
     */
    private List<UnifiedToolCall> parseMessageToolCalls(List<OpenAiChatCompletionRequest.OpenAiToolCall> toolCalls,
                                                        String paramPath) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return null;
        }

        List<UnifiedToolCall> result = new ArrayList<>();
        for (int i = 0; i < toolCalls.size(); i++) {
            OpenAiChatCompletionRequest.OpenAiToolCall toolCall = toolCalls.get(i);
            String toolCallPath = paramPath + "[" + i + "]";
            if (toolCall.getFunction() == null) {
                throw invalidRequest(toolCallPath + ".function", toolCallPath + ".function is required");
            }

            String toolName = toolCall.getFunction().getName();
            if (toolName == null || toolName.isBlank()) {
                throw invalidRequest(toolCallPath + ".function.name", toolCallPath + ".function.name is required");
            }

            String arguments = toolCall.getFunction().getArguments();
            if (arguments == null) {
                throw invalidRequest(toolCallPath + ".function.arguments", toolCallPath + ".function.arguments is required");
            }

            UnifiedToolCall unifiedToolCall = new UnifiedToolCall();
            unifiedToolCall.setId(toolCall.getId());
            unifiedToolCall.setType(toolCall.getType());
            unifiedToolCall.setToolName(toolName);
            unifiedToolCall.setArgumentsJson(arguments);
            result.add(unifiedToolCall);
        }
        return result;
    }

    /**
     * 解析结构化输出配置
     *
     * @param responseFormat OpenAI 结构化输出配置
     * @return 统一结构化输出配置
     */
    private UnifiedResponseFormat parseResponseFormat(OpenAiChatCompletionRequest.OpenAiResponseFormat responseFormat) {
        if (responseFormat == null) {
            return null;
        }

        String type = responseFormat.getType();
        if (type == null || !RESPONSE_FORMAT_TYPES.contains(type)) {
            throw invalidRequest("response_format.type", "response_format.type must be one of text, json_object, json_schema");
        }

        UnifiedResponseFormat unifiedResponseFormat = new UnifiedResponseFormat();
        unifiedResponseFormat.setType(type);

        if (!"json_schema".equals(type)) {
            return unifiedResponseFormat;
        }

        OpenAiChatCompletionRequest.JsonSchemaSpec jsonSchema = responseFormat.getJsonSchema();
        if (jsonSchema == null) {
            throw invalidRequest("response_format.json_schema", "response_format.json_schema is required");
        }
        if (jsonSchema.getName() == null || jsonSchema.getName().isBlank()) {
            throw invalidRequest("response_format.json_schema.name", "response_format.json_schema.name is required");
        }
        if (jsonSchema.getSchema() == null || jsonSchema.getSchema().isEmpty()) {
            throw invalidRequest("response_format.json_schema.schema", "response_format.json_schema.schema is required");
        }

        unifiedResponseFormat.setName(jsonSchema.getName());
        unifiedResponseFormat.setStrict(jsonSchema.getStrict());
        unifiedResponseFormat.setSchema(jsonSchema.getSchema());
        return unifiedResponseFormat;
    }

    /**
     * 决定最终的最大输出 token 数
     *
     * @param maxTokens            旧字段
     * @param maxCompletionTokens  新字段
     * @return 最终值
     */
    private Integer resolveMaxOutputTokens(Integer maxTokens, Integer maxCompletionTokens) {
        return maxCompletionTokens != null ? maxCompletionTokens : maxTokens;
    }

    /**
     * 创建文本片段
     *
     * @param text 文本内容
     * @return 文本片段
     */
    private UnifiedPart createTextPart(String text) {
        UnifiedPart part = new UnifiedPart();
        part.setType("text");
        part.setText(text);
        return part;
    }

    /**
     * 创建无效请求异常
     *
     * @param param   参数路径
     * @param message 错误消息
     * @return 网关异常
     */
    private GatewayException invalidRequest(String param, String message) {
        return new GatewayException(ErrorCode.INVALID_REQUEST, message, param);
    }

    private record ParsedMessages(String systemPrompt, List<UnifiedMessage> messages) {
    }

    private record DataUrl(String mimeType, String base64Data) {
    }
}
