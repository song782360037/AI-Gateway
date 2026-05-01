package com.code.aigateway.core.router.auto;

import com.code.aigateway.core.model.UnifiedGenerationConfig;
import com.code.aigateway.core.model.UnifiedMessage;
import com.code.aigateway.core.model.UnifiedPart;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedResponseFormat;
import com.code.aigateway.core.model.UnifiedToolChoice;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Auto 路由请求画像分类器。
 */
@Component
public class AutoRouteRequestClassifier {

    public AutoRequestProfile classify(UnifiedRequest request) {
        int estimatedInputTokens = estimateInputTokens(request);
        boolean visionRequired = hasImage(request);
        boolean toolsRequired = request.getTools() != null && !request.getTools().isEmpty();
        boolean toolChoiceRequired = isToolChoiceRequired(request.getToolChoice());
        boolean reasoningRequired = isReasoningRequired(request.getGenerationConfig());
        boolean jsonRequired = isJsonRequired(request.getResponseFormat());
        boolean streamRequired = Boolean.TRUE.equals(request.getStream());
        Integer requestedOutputTokens = request.getGenerationConfig() == null
                ? null : request.getGenerationConfig().getMaxOutputTokens();
        AutoRequestProfile.Intent intent = resolveIntent(request.getMetadata(), visionRequired, toolChoiceRequired, toolsRequired);
        AutoRequestProfile.Complexity complexity = resolveComplexity(estimatedInputTokens, visionRequired, toolsRequired,
                reasoningRequired, jsonRequired);

        return new AutoRequestProfile(
                visionRequired,
                toolsRequired,
                toolChoiceRequired,
                reasoningRequired,
                jsonRequired,
                streamRequired,
                estimatedInputTokens,
                requestedOutputTokens,
                complexity,
                intent);
    }

    private int estimateInputTokens(UnifiedRequest request) {
        int characters = length(request.getSystemPrompt());
        List<UnifiedMessage> messages = request.getMessages();
        if (messages != null) {
            for (UnifiedMessage message : messages) {
                characters += estimateMessageCharacters(message);
            }
        }
        return Math.max(1, (int) Math.ceil(characters / 4.0));
    }

    private int estimateMessageCharacters(UnifiedMessage message) {
        int characters = length(message.getRole()) + length(message.getToolName()) + length(message.getToolCallId());
        if (message.getParts() == null) {
            return characters;
        }
        for (UnifiedPart part : message.getParts()) {
            characters += length(part.getText()) + length(part.getUrl()) + length(part.getBase64Data());
        }
        return characters;
    }

    private boolean hasImage(UnifiedRequest request) {
        if (request.getMessages() == null) {
            return false;
        }
        return request.getMessages().stream()
                .filter(message -> message.getParts() != null)
                .flatMap(message -> message.getParts().stream())
                .anyMatch(part -> UnifiedPart.TYPE_IMAGE.equalsIgnoreCase(part.getType()));
    }

    private boolean isToolChoiceRequired(UnifiedToolChoice toolChoice) {
        if (toolChoice == null || toolChoice.getType() == null) {
            return false;
        }
        String type = toolChoice.getType().toLowerCase(Locale.ROOT);
        return "required".equals(type) || "specific".equals(type);
    }

    private boolean isReasoningRequired(UnifiedGenerationConfig generationConfig) {
        if (generationConfig == null) {
            return false;
        }
        if (generationConfig.getReasoning() != null && Boolean.TRUE.equals(generationConfig.getReasoning().getEnabled())) {
            return true;
        }
        if (generationConfig.getThinkingBudgetTokens() != null && generationConfig.getThinkingBudgetTokens() > 0) {
            return true;
        }
        return generationConfig.getReasoningEffort() != null && !generationConfig.getReasoningEffort().isBlank();
    }

    private boolean isJsonRequired(UnifiedResponseFormat responseFormat) {
        return responseFormat != null
                && responseFormat.getType() != null
                && responseFormat.getType().toLowerCase(Locale.ROOT).startsWith("json");
    }

    private AutoRequestProfile.Intent resolveIntent(Map<String, Object> metadata,
                                                    boolean visionRequired,
                                                    boolean toolChoiceRequired,
                                                    boolean toolsRequired) {
        Object rawIntent = metadata == null ? null : metadata.get("intent");
        if (rawIntent instanceof String intentText) {
            try {
                return AutoRequestProfile.Intent.valueOf(intentText.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return AutoRequestProfile.Intent.BALANCED;
            }
        }
        if (visionRequired) {
            return AutoRequestProfile.Intent.VISION;
        }
        if (toolChoiceRequired || toolsRequired) {
            return AutoRequestProfile.Intent.AGENT;
        }
        return AutoRequestProfile.Intent.BALANCED;
    }

    private AutoRequestProfile.Complexity resolveComplexity(int estimatedInputTokens,
                                                            boolean visionRequired,
                                                            boolean toolsRequired,
                                                            boolean reasoningRequired,
                                                            boolean jsonRequired) {
        if (estimatedInputTokens > 6000 || reasoningRequired || (visionRequired && toolsRequired)) {
            return AutoRequestProfile.Complexity.COMPLEX;
        }
        if (estimatedInputTokens > 1000 || visionRequired || toolsRequired || jsonRequired) {
            return AutoRequestProfile.Complexity.NORMAL;
        }
        return AutoRequestProfile.Complexity.SIMPLE;
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }
}
