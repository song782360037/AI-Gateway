package com.code.aigateway.core.encoder;

import com.code.aigateway.api.response.AnthropicMessagesResponse;
import com.code.aigateway.core.model.UnifiedOutput;
import com.code.aigateway.core.model.UnifiedPart;
import com.code.aigateway.core.model.UnifiedResponse;
import com.code.aigateway.core.model.UnifiedToolCall;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages API 响应编码器
 * <p>
 * 将 UnifiedResponse 转换为 Anthropic Messages 格式。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class AnthropicResponseEncoder {

    private final ObjectMapper objectMapper;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    public AnthropicMessagesResponse encode(UnifiedResponse source) {
        List<AnthropicMessagesResponse.ContentBlock> contentBlocks = new ArrayList<>();

        // 提取文本内容
        String text = extractText(source);
        if (!text.isEmpty()) {
            contentBlocks.add(AnthropicMessagesResponse.ContentBlock.builder()
                    .type("text")
                    .text(text)
                    .build());
        }

        // 提取工具调用
        List<UnifiedToolCall> toolCalls = extractToolCalls(source);
        for (UnifiedToolCall toolCall : toolCalls) {
            contentBlocks.add(AnthropicMessagesResponse.ContentBlock.builder()
                    .type("tool_use")
                    .id(toolCall.getId())
                    .name(toolCall.getToolName())
                    // arguments 需要从 JSON string 解析为 Object
                    .input(parseArguments(toolCall.getArgumentsJson()))
                    .build());
        }

        // 使用统计
        AnthropicMessagesResponse.Usage usage = null;
        if (source.getUsage() != null) {
            usage = AnthropicMessagesResponse.Usage.builder()
                    .inputTokens(source.getUsage().getInputTokens())
                    .outputTokens(source.getUsage().getOutputTokens())
                    .build();
        }

        // 映射 finish_reason
        String stopReason = mapStopReason(source.getFinishReason());

        return AnthropicMessagesResponse.builder()
                .id(source.getId())
                .type("message")
                .role("assistant")
                .content(contentBlocks)
                .model(source.getModel())
                .stopReason(stopReason)
                .usage(usage)
                .build();
    }

    private String extractText(UnifiedResponse source) {
        if (source.getOutputs() == null || source.getOutputs().isEmpty()) {
            return "";
        }
        UnifiedOutput output = source.getOutputs().get(0);
        if (output.getParts() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (UnifiedPart part : output.getParts()) {
            if ("text".equals(part.getType()) && part.getText() != null) {
                sb.append(part.getText());
            }
        }
        return sb.toString();
    }

    private List<UnifiedToolCall> extractToolCalls(UnifiedResponse source) {
        if (source.getOutputs() == null || source.getOutputs().isEmpty()) {
            return List.of();
        }
        UnifiedOutput output = source.getOutputs().get(0);
        return output.getToolCalls() != null ? output.getToolCalls() : List.of();
    }

    private String mapStopReason(String finishReason) {
        if (finishReason == null) return "end_turn";
        return switch (finishReason) {
            case "stop" -> "end_turn";
            case "length" -> "max_tokens";
            case "tool_calls" -> "tool_use";
            default -> "end_turn";
        };
    }

    /**
     * 将 JSON 字符串参数解析为 Map（用于 Anthropic tool_use.input）
     */
    private Object parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(argumentsJson, MAP_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
