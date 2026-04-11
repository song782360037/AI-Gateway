package com.code.aigateway.core.encoder;

import com.code.aigateway.api.response.OpenAiResponsesResponse;
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

        // Responses 非流式 output content 只允许 output_text / refusal。
        // Unified 中的 thinking 需要编码为独立的 reasoning output item，不能混入 message.content。
        List<UnifiedPart> thinkingParts = source.collectThinkingParts();
        for (UnifiedPart thinkingPart : thinkingParts) {
            OpenAiResponsesResponse.ContentPart summaryPart = OpenAiResponsesResponse.ContentPart.builder()
                    .type("summary_text")
                    .text(thinkingPart.getText())
                    .build();
            outputItems.add(OpenAiResponsesResponse.OutputItem.builder()
                    .type("reasoning")
                    .role("assistant")
                    .summary(List.of(summaryPart))
                    .status("completed")
                    .build());
        }

        // 文本内容保持为 message + output_text，与 Responses API 非流式格式一致。
        String text = source.collectText();
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

        // 工具调用输出单独编码为 function_call output item。
        List<UnifiedToolCall> toolCalls = source.collectToolCalls();
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
