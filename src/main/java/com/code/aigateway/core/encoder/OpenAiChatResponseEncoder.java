package com.code.aigateway.core.encoder;

import com.code.aigateway.api.response.OpenAiChatCompletionResponse;
import com.code.aigateway.core.model.UnifiedOutput;
import com.code.aigateway.core.model.UnifiedPart;
import com.code.aigateway.core.model.UnifiedResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * OpenAI 聊天响应编码器
 * <p>
 * 将统一的响应模型转换为 OpenAI 格式的聊天完成响应。
 * </p>
 *
 * @author sst
 */
@Component
public class OpenAiChatResponseEncoder implements ResponseEncoder<UnifiedResponse, OpenAiChatCompletionResponse> {

    /**
     * 将统一响应编码为 OpenAI 格式
     * <p>
     * 转换内容包括：
     * <ul>
     *   <li>提取输出中的文本内容</li>
     *   <li>转换 token 使用统计</li>
     *   <li>构建标准的 OpenAI 响应结构</li>
     * </ul>
     * </p>
     *
     * @param source 统一格式的响应
     * @return OpenAI 格式的聊天完成响应
     */
    @Override
    public OpenAiChatCompletionResponse encode(UnifiedResponse source) {
        // 提取输出内容
        String content = "";
        if (source.getOutputs() != null && !source.getOutputs().isEmpty()) {
            UnifiedOutput output = source.getOutputs().get(0);
            content = extractText(output.getParts());
        }

        // 转换使用统计
        OpenAiChatCompletionResponse.Usage usage = null;
        if (source.getUsage() != null) {
            usage = OpenAiChatCompletionResponse.Usage.builder()
                    .promptTokens(source.getUsage().getInputTokens())
                    .completionTokens(source.getUsage().getOutputTokens())
                    .totalTokens(source.getUsage().getTotalTokens())
                    .build();
        }

        // 构建 OpenAI 格式响应
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

    /**
     * 从内容部分列表中提取文本
     * <p>
     * 合并所有类型为 text 的内容部分
     * </p>
     *
     * @param parts 内容部分列表
     * @return 合并后的文本内容
     */
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
