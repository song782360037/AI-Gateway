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

    /** Embedding 数据列表，非 Embedding 响应为 null */
    private List<EmbeddingData> embeddingData;

    /** Rerank 结果列表，非 Rerank 响应为 null */
    private List<RerankResult> rerankResults;

    /**
     * Embedding 向量值的类型安全封装。
     * <p>支持两种编码格式：float 数组（默认）和 base64 字符串。</p>
     */
    public sealed interface EmbeddingValue {
        /** 返回原始值，用于 JSON 序列化 */
        Object raw();

        /** double 数组格式的 Embedding 向量，保留上游原始精度 */
        record FloatArray(double[] values) implements EmbeddingValue {
            @Override
            public Object raw() { return values; }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (!(obj instanceof FloatArray other)) return false;
                return java.util.Arrays.equals(values, other.values);
            }

            @Override
            public int hashCode() {
                return java.util.Arrays.hashCode(values);
            }
        }

        /** base64 编码格式的 Embedding 向量 */
        record Base64String(String encoded) implements EmbeddingValue {
            @Override
            public Object raw() { return encoded; }
        }
    }

    /**
     * Embedding 数据项
     */
    @Data
    public static class EmbeddingData {
        /** 在输入数组中的索引 */
        private Integer index;
        /** 向量数据（float 数组或 base64 字符串） */
        private EmbeddingValue embedding;
    }

    /**
     * Rerank 结果项
     */
    @Data
    public static class RerankResult {
        /** 在原始文档数组中的索引 */
        private Integer index;
        /** 相关性分数（0-1） */
        private Double relevanceScore;
        /** 文档原文（仅当 return_documents=true 时有值） */
        private String document;
    }

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
