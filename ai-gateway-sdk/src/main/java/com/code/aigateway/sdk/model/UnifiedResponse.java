package com.code.aigateway.sdk.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一的响应模型
 * <p>
 * 协议无关的响应中间表示，各协议的 Encoder 将此结构编码为协议特定格式。
 * </p>
 */
@Data
public class UnifiedResponse {

    /** 响应唯一标识 */
    private String id;

    /** 使用的模型名称 */
    private String model;

    /** 提供商名称 */
    private String provider;

    /** 完成原因：stop / length / tool_calls */
    private String finishReason;

    /** 创建时间（Unix 秒） */
    private Long created;

    /** Token 使用统计 */
    private UnifiedUsage usage;

    /** 输出列表 */
    private List<UnifiedOutput> outputs;

    /** 从所有 output 中聚合文本内容 */
    public String collectText() {
        if (outputs == null || outputs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (UnifiedOutput output : outputs) {
            if (output.getParts() == null) continue;
            for (UnifiedPart part : output.getParts()) {
                if (UnifiedPart.TYPE_TEXT.equals(part.getType()) && part.getText() != null) {
                    sb.append(part.getText());
                }
            }
        }
        return sb.toString();
    }

    /** 从所有 output 中聚合 thinking 内容 */
    public List<UnifiedPart> collectThinkingParts() {
        if (outputs == null || outputs.isEmpty()) {
            return List.of();
        }
        List<UnifiedPart> result = new ArrayList<>();
        for (UnifiedOutput output : outputs) {
            if (output.getParts() == null) continue;
            for (UnifiedPart part : output.getParts()) {
                if (UnifiedPart.TYPE_THINKING.equals(part.getType()) && part.getText() != null) {
                    result.add(part);
                }
            }
        }
        return result;
    }

    /** 从所有 output 中聚合工具调用 */
    public List<UnifiedToolCall> collectToolCalls() {
        if (outputs == null || outputs.isEmpty()) {
            return List.of();
        }
        List<UnifiedToolCall> result = new ArrayList<>();
        for (UnifiedOutput output : outputs) {
            if (output.getToolCalls() != null) {
                result.addAll(output.getToolCalls());
            }
        }
        return result;
    }
}
