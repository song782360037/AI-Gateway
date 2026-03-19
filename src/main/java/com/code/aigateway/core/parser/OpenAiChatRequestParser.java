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

@Component
public class OpenAiChatRequestParser implements RequestParser<OpenAiChatCompletionRequest, UnifiedRequest> {

    /**
     * 将OpenAI的请求转换为统一的请求
     *     // 支持 system prompt 提取，即 messages[0].role = system，会将 messages[0].content 提取为 system prompt
     *     // 支持 content 为 string 或 array，即 messages[].content 可以是 string 或 array
     *     // 支持 image_url 多模态，即 messages[].content 中包含 type = image_url
     *     // 支持 tools 和 tool_choice
     * @param request
     * @return
     */
    @Override
    public UnifiedRequest parse(OpenAiChatCompletionRequest request) {
        UnifiedRequest unifiedRequest = new UnifiedRequest();
        unifiedRequest.setRequestProtocol("openai-chat");
        unifiedRequest.setResponseProtocol("openai-chat");
        unifiedRequest.setModel(request.getModel());
        unifiedRequest.setStream(Boolean.TRUE.equals(request.getStream()));

        UnifiedGenerationConfig config = new UnifiedGenerationConfig();
        config.setTemperature(request.getTemperature());
        config.setTopP(request.getTopP());
        config.setMaxOutputTokens(request.getMaxTokens());
        config.setStopSequences(request.getStop());
        unifiedRequest.setGenerationConfig(config);

        List<UnifiedMessage> unifiedMessages = new ArrayList<>();
        String systemPrompt = null;

        for (OpenAiChatCompletionRequest.OpenAiMessage msg : request.getMessages()) {
            if ("system".equalsIgnoreCase(msg.getRole()) && msg.getContent() instanceof String str) {
                if (systemPrompt == null) {
                    systemPrompt = str;
                    continue;
                }
            }

            UnifiedMessage unifiedMessage = new UnifiedMessage();
            unifiedMessage.setRole(msg.getRole());
            unifiedMessage.setToolCallId(msg.getToolCallId());
            unifiedMessage.setParts(parseContent(msg.getContent()));
            unifiedMessages.add(unifiedMessage);
        }

        unifiedRequest.setSystemPrompt(systemPrompt);
        unifiedRequest.setMessages(unifiedMessages);
        unifiedRequest.setTools(parseTools(request.getTools()));
        unifiedRequest.setToolChoice(parseToolChoice(request.getToolChoice()));

        return unifiedRequest;
    }

    private List<UnifiedPart> parseContent(Object content) {
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

        if (content instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object type = map.get("type");
                    if ("text".equals(type)) {
                        UnifiedPart part = new UnifiedPart();
                        part.setType("text");
                        part.setText((String) map.get("text"));
                        parts.add(part);
                    } else if ("image_url".equals(type)) {
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

    private UnifiedToolChoice parseToolChoice(Object toolChoiceObj) {
        if (toolChoiceObj == null) {
            return null;
        }

        UnifiedToolChoice choice = new UnifiedToolChoice();

        if (toolChoiceObj instanceof String str) {
            choice.setType(str);
            return choice;
        }

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
