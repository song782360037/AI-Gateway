package com.code.aigateway.core.encoder;

import com.code.aigateway.api.response.OpenAiChatCompletionResponse;
import com.code.aigateway.core.model.UnifiedResponse;
import com.code.aigateway.core.model.UnifiedToolCall;
import org.springframework.stereotype.Component;

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
        // 聚合所有 output 的 text 和 tool_calls（跨 output 合并）
        String content = source.collectText();
        List<OpenAiChatCompletionResponse.ToolCall> toolCalls = encodeToolCalls(source.collectToolCalls());

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
        OpenAiChatCompletionResponse.Message.MessageBuilder messageBuilder = OpenAiChatCompletionResponse.Message.builder()
                .role("assistant")
                .content(content);
        if (!toolCalls.isEmpty()) {
            messageBuilder.toolCalls(toolCalls);
        }

        return OpenAiChatCompletionResponse.builder()
                .id(source.getId())
                .object("chat.completion")
                .created(resolveCreated(source))
                .model(source.getModel())
                .choices(List.of(
                        OpenAiChatCompletionResponse.Choice.builder()
                                .index(0)
                                .message(messageBuilder.build())
                                .finishReason(source.getFinishReason())
                                .build()
                ))
                .usage(usage)
                .build();
    }

    /**
     * 优先透传统一响应中的创建时间；若上游未提供，则回退为当前时间
     */
    private long resolveCreated(UnifiedResponse source) {
        return source.getCreated() != null ? source.getCreated() : System.currentTimeMillis() / 1000;
    }

    /**
     * 将统一工具调用列表编码为 OpenAI 格式
     *
     * @param toolCalls 统一工具调用列表
     * @return OpenAI 格式工具调用列表
     */
    private List<OpenAiChatCompletionResponse.ToolCall> encodeToolCalls(List<UnifiedToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }
        return toolCalls.stream()
                .map(tc -> OpenAiChatCompletionResponse.ToolCall.builder()
                        .id(tc.getId())
                        .type(tc.getType() == null ? "function" : tc.getType())
                        .function(OpenAiChatCompletionResponse.FunctionCall.builder()
                                .name(tc.getToolName())
                                .arguments(tc.getArgumentsJson() == null ? "{}" : tc.getArgumentsJson())
                                .build())
                        .build())
                .toList();
    }
}
