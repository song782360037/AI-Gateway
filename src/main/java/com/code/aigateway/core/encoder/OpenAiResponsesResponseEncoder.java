package com.code.aigateway.core.encoder;

import com.code.aigateway.api.response.OpenAiResponsesResponse;
import com.code.aigateway.core.model.UnifiedOutput;
import com.code.aigateway.core.model.UnifiedPart;
import com.code.aigateway.core.model.UnifiedResponse;
import com.code.aigateway.core.model.UnifiedToolCall;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI Responses API 响应编码器
 * <p>
 * 将 UnifiedResponse 转换为 Responses API 格式。
 * </p>
 */
@Component
public class OpenAiResponsesResponseEncoder {

    public OpenAiResponsesResponse encode(UnifiedResponse source) {
        List<OpenAiResponsesResponse.OutputItem> outputItems = new ArrayList<>();

        // 提取文本输出
        String text = extractText(source);
        if (!text.isEmpty()) {
            OpenAiResponsesResponse.ContentPart contentPart = OpenAiResponsesResponse.ContentPart.builder()
                    .type("output_text")
                    .text(text)
                    .build();
            outputItems.add(OpenAiResponsesResponse.OutputItem.builder()
                    .type("message")
                    .role("assistant")
                    .content(List.of(contentPart))
                    .status("completed")
                    .build());
        }

        // 提取工具调用输出
        List<UnifiedToolCall> toolCalls = extractToolCalls(source);
        for (UnifiedToolCall toolCall : toolCalls) {
            outputItems.add(OpenAiResponsesResponse.OutputItem.builder()
                    .type("function_call")
                    .callId(toolCall.getId())
                    .name(toolCall.getToolName())
                    .arguments(toolCall.getArgumentsJson())
                    .status("completed")
                    .build());
        }

        // 构建使用统计
        OpenAiResponsesResponse.Usage usage = null;
        if (source.getUsage() != null) {
            usage = OpenAiResponsesResponse.Usage.builder()
                    .inputTokens(source.getUsage().getInputTokens())
                    .outputTokens(source.getUsage().getOutputTokens())
                    .totalTokens(source.getUsage().getTotalTokens())
                    .build();
        }

        // 映射 finish_reason → status
        String status = mapStatus(source.getFinishReason());

        return OpenAiResponsesResponse.builder()
                .id(source.getId())
                .object("response")
                .createdAt(Instant.now().getEpochSecond())
                .model(source.getModel())
                .status(status)
                .output(outputItems)
                .usage(usage)
                .build();
    }

    private String extractText(UnifiedResponse source) {
        if (source.getOutputs() == null || source.getOutputs().isEmpty()) {
            return "";
        }
        UnifiedOutput output = source.getOutputs().get(0);
        if (output.getParts() == null) {
            return "";
        }
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

    private String mapStatus(String finishReason) {
        if (finishReason == null) {
            return "completed";
        }
        return switch (finishReason) {
            case "stop", "tool_calls" -> "completed";
            case "length" -> "incomplete";
            default -> "completed";
        };
    }
}
