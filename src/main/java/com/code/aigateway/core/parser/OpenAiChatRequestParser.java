package com.code.aigateway.core.parser;

import com.code.aigateway.api.request.OpenAiChatCompletionRequest;
import com.code.aigateway.core.model.UnifiedGenerationConfig;
import com.code.aigateway.core.model.UnifiedMessage;
import com.code.aigateway.core.model.UnifiedPart;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedTool;
import com.code.aigateway.core.model.UnifiedToolChoice;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 聊天请求解析器
 * <p>
 * 将 OpenAI 格式的聊天请求转换为统一的请求模型，支持以下特性：
 * <ul>
 *   <li>System Prompt 提取：将 messages 中 role=system 的消息提取为 systemPrompt</li>
 *   <li>多模态内容：支持 content 为字符串或数组格式</li>
 *   <li>图片输入：支持 image_url 类型的多模态内容</li>
 *   <li>工具调用：支持 tools 和 tool_choice 参数</li>
 * </ul>
 * </p>
 *
 * @author sst
 */
@Component
public class OpenAiChatRequestParser implements RequestParser<OpenAiChatCompletionRequest, UnifiedRequest> {

    /**
     * 将 OpenAI 格式的请求转换为统一格式
     * <p>
     * 转换内容包括：
     * <ul>
     *   <li>设置请求/响应协议为 openai-chat</li>
     *   <li>提取模型名称和流式标志</li>
     *   <li>转换生成配置（temperature、topP、maxTokens、stop）</li>
     *   <li>提取 system prompt（第一条 role=system 的消息）</li>
     *   <li>转换消息列表（支持多模态内容）</li>
     *   <li>转换工具定义和工具选择配置</li>
     * </ul>
     * </p>
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

        // 转换生成配置
        UnifiedGenerationConfig config = new UnifiedGenerationConfig();
        config.setTemperature(request.getTemperature());
        config.setTopP(request.getTopP());
        config.setMaxOutputTokens(request.getMaxTokens());
        config.setStopSequences(request.getStop());
        unifiedRequest.setGenerationConfig(config);

        // 处理消息列表
        List<UnifiedMessage> unifiedMessages = new ArrayList<>();
        String systemPrompt = null;

        for (OpenAiChatCompletionRequest.OpenAiMessage msg : request.getMessages()) {
            // 提取第一条 system 消息作为 systemPrompt
            if ("system".equalsIgnoreCase(msg.getRole()) && msg.getContent() instanceof String str) {
                if (systemPrompt == null) {
                    systemPrompt = str;
                    continue;
                }
            }

            // 转换普通消息
            UnifiedMessage unifiedMessage = new UnifiedMessage();
            unifiedMessage.setRole(msg.getRole());
            unifiedMessage.setToolCallId(msg.getToolCallId());
            unifiedMessage.setParts(parseContent(msg.getContent()));
            unifiedMessages.add(unifiedMessage);
        }

        unifiedRequest.setSystemPrompt(systemPrompt);
        unifiedRequest.setMessages(unifiedMessages);

        // 转换工具配置
        unifiedRequest.setTools(parseTools(request.getTools()));
        unifiedRequest.setToolChoice(parseToolChoice(request.getToolChoice()));

        return unifiedRequest;
    }

    /**
     * 解析消息内容
     * <p>
     * 支持两种格式：
     * <ul>
     *   <li>字符串：直接作为文本内容</li>
     *   <li>数组：支持 text 和 image_url 两种类型</li>
     * </ul>
     * </p>
     *
     * @param content 消息内容（字符串或数组）
     * @return 统一的内容部分列表
     */
    private List<UnifiedPart> parseContent(Object content) {
        List<UnifiedPart> parts = new ArrayList<>();
        if (content == null) {
            return parts;
        }

        // 处理字符串格式内容
        if (content instanceof String str) {
            UnifiedPart part = new UnifiedPart();
            part.setType("text");
            part.setText(str);
            parts.add(part);
            return parts;
        }

        // 处理数组格式内容（多模态）
        if (content instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object type = map.get("type");

                    // 文本内容
                    if ("text".equals(type)) {
                        UnifiedPart part = new UnifiedPart();
                        part.setType("text");
                        part.setText((String) map.get("text"));
                        parts.add(part);
                    }
                    // 图片 URL
                    else if ("image_url".equals(type)) {
                        UnifiedPart part = new UnifiedPart();
                        part.setType("image");
                        Object imageUrlObj = map.get("image_url");
                        if (imageUrlObj instanceof Map<?, ?> imageMap) {
                            Object url = imageMap.get("url");
                            if (url != null) {
                                part.setUrl(String.valueOf(url));
                            }
                        }
                        parts.add(part);
                    }
                }
            }
        }

        return parts;
    }

    /**
     * 解析工具定义列表
     *
     * @param tools OpenAI 格式的工具列表
     * @return 统一格式的工具列表
     */
    private List<UnifiedTool> parseTools(List<OpenAiChatCompletionRequest.OpenAiTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }

        List<UnifiedTool> result = new ArrayList<>();
        for (OpenAiChatCompletionRequest.OpenAiTool tool : tools) {
            if (tool.getFunction() == null) {
                continue;
            }

            UnifiedTool unifiedTool = new UnifiedTool();
            unifiedTool.setName(tool.getFunction().getName());
            unifiedTool.setDescription(tool.getFunction().getDescription());
            unifiedTool.setInputSchema(tool.getFunction().getParameters());
            result.add(unifiedTool);
        }
        return result;
    }

    /**
     * 解析工具选择配置
     * <p>
     * 支持两种格式：
     * <ul>
     *   <li>字符串：如 "auto"、"none"、"required"</li>
     *   <li>对象：指定特定工具，如 {"type": "function", "function": {"name": "xxx"}}</li>
     * </ul>
     * </p>
     *
     * @param toolChoiceObj 工具选择配置
     * @return 统一格式的工具选择
     */
    private UnifiedToolChoice parseToolChoice(Object toolChoiceObj) {
        if (toolChoiceObj == null) {
            return null;
        }

        UnifiedToolChoice choice = new UnifiedToolChoice();

        // 字符串格式：auto、none、required
        if (toolChoiceObj instanceof String str) {
            choice.setType(str);
            return choice;
        }

        // 对象格式：指定特定工具
        if (toolChoiceObj instanceof Map<?, ?> map) {
            Object type = map.get("type");
            choice.setType(type == null ? "specific" : String.valueOf(type));

            Object functionObj = map.get("function");
            if (functionObj instanceof Map<?, ?> functionMap) {
                Object name = functionMap.get("name");
                if (name != null) {
                    choice.setToolName(String.valueOf(name));
                }
            }
        }

        return choice;
    }
}
