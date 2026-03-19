package com.code.aigateway.core.encoder;

import com.code.aigateway.api.response.OpenAiChatCompletionResponse;
import com.code.aigateway.core.model.UnifiedOutput;
import com.code.aigateway.core.model.UnifiedPart;
import com.code.aigateway.core.model.UnifiedResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class OpenAiChatResponseEncoder implements ResponseEncoder<UnifiedResponse, OpenAiChatCompletionResponse> {

    @Override
    public OpenAiChatCompletionResponse encode(UnifiedResponse source) {
        String content = "";
        if (source.getOutputs() != null && !source.getOutputs().isEmpty()) {
            UnifiedOutput output = source.getOutputs().get(0);
            content = extractText(output.getParts());
        }

        OpenAiChatCompletionResponse.Usage usage = null;
        if (source.getUsage() != null) {
            usage = OpenAiChatCompletionResponse.Usage.builder()
                    .promptTokens(source.getUsage().getInputTokens())
                    .completionTokens(source.getUsage().getOutputTokens())
                    .totalTokens(source.getUsage().getTotalTokens())
                    .build();
        }

        return OpenAiChatCompletionResponse.builder()
                .id(source.getId())
                .object("chat.completion")
                .created(Instant.now().getEpochSecond())
                .model(source.getModel())
                .choices(List.of(
                        OpenAiChatCompletionResponse.Choice.builder()
                                .index(0)
                                .message(OpenAiChatCompletionResponse.Message.builder()
                                        .role("assistant")
                                        .content(content)
                                        .build())
                                .finishReason(source.getFinishReason())
                                .build()
                ))
                .usage(usage)
                .build();
    }

    private String extractText(List<UnifiedPart> parts) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (UnifiedPart part : parts) {
            if ("text".equals(part.getType()) && part.getText() != null) {
                sb.append(part.getText());
            }
        }
        return sb.toString();
    }
}
