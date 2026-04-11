package com.code.aigateway.core.encoder;

import com.code.aigateway.api.response.GeminiGenerateContentResponse;
import com.code.aigateway.core.model.UnifiedResponse;
import com.code.aigateway.core.model.UnifiedToolCall;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gemini API 响应编码器
 * <p>
 * 将 UnifiedResponse 转换为 Gemini generateContent 格式。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class GeminiResponseEncoder {

    private final ObjectMapper objectMapper;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    public GeminiGenerateContentResponse encode(UnifiedResponse source) {
        // 构建 candidates
        List<GeminiGenerateContentResponse.Candidate> candidates = new ArrayList<>();
        candidates.add(buildCandidate(source));

        // 使用统计
        GeminiGenerateContentResponse.UsageMetadata usage = null;
        if (source.getUsage() != null) {
            usage = GeminiGenerateContentResponse.UsageMetadata.builder()
                    .promptTokenCount(source.getUsage().getInputTokens())
                    .candidatesTokenCount(source.getUsage().getOutputTokens())
                    .totalTokenCount(source.getUsage().getTotalTokens())
                    .build();
        }

        return GeminiGenerateContentResponse.builder()
                .candidates(candidates)
                .usageMetadata(usage)
                .modelVersion(source.getModel())
                .build();
    }

    /**
     * 编码为 JSON 字符串（用于流式 JSON 数组模式）
     */
    public String encodeChunk(UnifiedResponse source) {
        GeminiGenerateContentResponse response = encode(source);
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize gemini chunk", e);
        }
    }

    private GeminiGenerateContentResponse.Candidate buildCandidate(UnifiedResponse source) {
        List<Map<String, Object>> parts = new ArrayList<>();

        // 文本部分
        String text = source.collectText();
        if (!text.isEmpty()) {
            Map<String, Object> textPart = new LinkedHashMap<>();
            textPart.put("text", text);
            parts.add(textPart);
        }

        // 工具调用部分
        List<UnifiedToolCall> toolCalls = source.collectToolCalls();
        for (UnifiedToolCall toolCall : toolCalls) {
            Map<String, Object> fcPart = new LinkedHashMap<>();
            Map<String, Object> functionCall = new LinkedHashMap<>();
            functionCall.put("name", toolCall.getToolName());
            functionCall.put("args", parseArguments(toolCall.getArgumentsJson()));
            fcPart.put("functionCall", functionCall);
            parts.add(fcPart);
        }

        GeminiGenerateContentResponse.Candidate.Content content = GeminiGenerateContentResponse.Candidate.Content.builder()
                .parts(parts)
                .role("model")
                .build();

        return GeminiGenerateContentResponse.Candidate.builder()
                .content(content)
                .finishReason(mapFinishReason(source.getFinishReason()))
                .build();
    }

    private String mapFinishReason(String finishReason) {
        if (finishReason == null) return "STOP";
        return switch (finishReason) {
            case "stop" -> "STOP";
            case "length" -> "MAX_TOKENS";
            case "tool_calls" -> "FUNCTION_CALL";
            default -> "STOP";
        };
    }

    /**
     * 将 JSON 字符串参数解析为 Map（用于 Gemini functionCall.args）
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
