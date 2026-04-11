package com.code.aigateway.core.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一的响应模型
 * <p>
 * 作为不同 AI 提供商响应格式的中间表示，解耦 API 层与提供商层。
 * 包含聊天响应的所有核心要素。
 * </p>
 *
 * @author sst
 */
@Data
public class UnifiedResponse {

    /**
     * 响应唯一标识
     */
    private String id;

    /**
     * 使用的模型名称
     */
    private String model;

    /**
     * 提供商名称
     */
    private String provider;

    /**
     * 完成原因（如 stop、length、tool_calls）
     */
    private String finishReason;

    /**
     * 响应创建时间（Unix 时间戳，秒）
     */
    private Long created;

    /**
     * Token 使用统计
     */
    private UnifiedUsage usage;

    /**
     * 输出列表（通常只有一个输出，但在 reasoning/tool call 场景下可能拆分为多个 output）
     */
    private List<UnifiedOutput> outputs;

    /**
     * 从所有 output 中按原始 output/part 顺序聚合文本内容
     */
    public String collectText() {
        if (outputs == null || outputs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (UnifiedOutput output : outputs) {
            if (output.getParts() == null || output.getParts().isEmpty()) {
                continue;
            }
            for (UnifiedPart part : output.getParts()) {
                if (UnifiedPart.TYPE_TEXT.equals(part.getType()) && part.getText() != null) {
                    sb.append(part.getText());
                }
            }
        }
        return sb.toString();
    }

    /**
     * 从所有 output 中按原始 output/part 顺序聚合 thinking 内容
     */
    public List<UnifiedPart> collectThinkingParts() {
        if (outputs == null || outputs.isEmpty()) {
            return List.of();
        }
        List<UnifiedPart> result = new ArrayList<>();
        for (UnifiedOutput output : outputs) {
            if (output.getParts() == null || output.getParts().isEmpty()) {
                continue;
            }
            for (UnifiedPart part : output.getParts()) {
                if (UnifiedPart.TYPE_THINKING.equals(part.getType()) && part.getText() != null) {
                    result.add(part);
                }
            }
        }
        return result;
    }

    /**
     * 从所有 output 中按原始 output 顺序聚合工具调用
     */
    public List<UnifiedToolCall> collectToolCalls() {
        if (outputs == null || outputs.isEmpty()) {
            return List.of();
        }
        List<UnifiedToolCall> result = new ArrayList<>();
        for (UnifiedOutput output : outputs) {
            if (output.getToolCalls() != null && !output.getToolCalls().isEmpty()) {
                result.addAll(output.getToolCalls());
            }
        }
        return result;
    }
}
