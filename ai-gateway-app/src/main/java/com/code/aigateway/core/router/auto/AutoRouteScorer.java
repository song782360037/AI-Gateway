package com.code.aigateway.core.router.auto;

import com.code.aigateway.core.router.RouteCandidate;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Auto 路由候选过滤与评分器。
 */
@Component
public class AutoRouteScorer {

    private static final int DEFAULT_SCORE = 50;
    private static final int DEFAULT_WEIGHT = 100;
    private static final int MAX_SCORE = 100;

    // ==================== 评分权重常量 ====================
    /** 策略匹配权重 */
    private static final double WEIGHT_POLICY_FIT = 0.30;
    /** 任务匹配权重 */
    private static final double WEIGHT_TASK_FIT = 0.35;
    /** 能力匹配权重 */
    private static final double WEIGHT_CAPABILITY = 0.15;
    /** 优先级权重 */
    private static final double WEIGHT_PRIORITY = 0.10;
    /** 可靠性权重 */
    private static final double WEIGHT_RELIABILITY = 0.10;

    public List<RouteCandidate> rank(List<RouteCandidate> candidates, AutoRequestProfile profile, String requestProtocol) {
        return candidates.stream()
                .filter(candidate -> rejectReason(candidate, profile, requestProtocol) == null)
                .sorted(Comparator
                        .comparingDouble((RouteCandidate candidate) -> score(candidate, profile).totalScore()).reversed()
                        .thenComparing(Comparator.comparingInt(this::safeWeight).reversed())
                        .thenComparing(Comparator.comparingInt(this::safePriority).reversed())
                        .thenComparing(RouteCandidate::getProviderCode, Comparator.nullsLast(String::compareTo))
                        .thenComparing(RouteCandidate::getTargetModel, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    public String rejectReason(RouteCandidate candidate, AutoRequestProfile profile, String requestProtocol) {
        if (!candidate.supportsProtocol(requestProtocol)) {
            return "请求协议不匹配";
        }
        if (profile.visionRequired() && !enabled(candidate.getSupportsVision())) {
            return "候选模型不支持视觉输入";
        }
        if (profile.toolsRequired() && !enabled(candidate.getSupportsTools())) {
            return "候选模型不支持工具调用";
        }
        if (profile.toolChoiceRequired() && !enabled(candidate.getSupportsToolChoiceRequired())) {
            return "候选模型不支持强制工具调用";
        }
        if (profile.reasoningRequired() && !enabled(candidate.getSupportsReasoning())) {
            return "候选模型不支持推理能力";
        }
        if (profile.jsonRequired() && !enabled(candidate.getSupportsJson())) {
            return "候选模型不支持 JSON 输出";
        }
        if (profile.streamRequired() && Boolean.FALSE.equals(candidate.getSupportsStream())) {
            return "候选模型不支持流式输出";
        }
        if (candidate.getMaxInputTokens() != null && candidate.getMaxInputTokens() > 0
                && profile.estimatedInputTokens() > candidate.getMaxInputTokens()) {
            return "输入 Token 超过候选模型上限";
        }
        if (candidate.getMaxOutputTokens() != null && candidate.getMaxOutputTokens() > 0
                && profile.requestedOutputTokens() != null
                && profile.requestedOutputTokens() > candidate.getMaxOutputTokens()) {
            return "输出 Token 超过候选模型上限";
        }
        return null;
    }

    public AutoRouteScore score(RouteCandidate candidate, AutoRequestProfile profile) {
        double policyFitScore = policyFitScore(candidate, profile);
        double taskFitScore = taskFitScore(candidate, profile);
        double capabilityScore = capabilityScore(candidate, profile);
        double priorityScore = normalize(safePriority(candidate), 9999);
        double reliabilityScore = safeScore(candidate.getReliabilityScore());
        double totalScore = policyFitScore * WEIGHT_POLICY_FIT
                + taskFitScore * WEIGHT_TASK_FIT
                + capabilityScore * WEIGHT_CAPABILITY
                + priorityScore * WEIGHT_PRIORITY
                + reliabilityScore * WEIGHT_RELIABILITY
                + safeBias(candidate);
        return new AutoRouteScore(totalScore, policyFitScore, taskFitScore, capabilityScore, priorityScore, reliabilityScore);
    }

    private double policyFitScore(RouteCandidate candidate, AutoRequestProfile profile) {
        return switch (profile.intent()) {
            case FAST -> safeScore(candidate.getLatencyScore());
            case CHEAP -> safeScore(candidate.getCostScore());
            case QUALITY -> safeScore(candidate.getQualityScore());
            case AGENT -> average(safeScore(candidate.getToolScore()), safeScore(candidate.getQualityScore()));
            case VISION -> average(safeScore(candidate.getVisionScore()), safeScore(candidate.getQualityScore()));
            case BALANCED -> average(safeScore(candidate.getQualityScore()), safeScore(candidate.getLatencyScore()),
                    safeScore(candidate.getCostScore()));
        };
    }

    private double taskFitScore(RouteCandidate candidate, AutoRequestProfile profile) {
        double score = safeScore(candidate.getQualityScore());
        if (profile.toolsRequired()) {
            score = average(score, safeScore(candidate.getToolScore()));
        }
        if (profile.visionRequired()) {
            score = average(score, safeScore(candidate.getVisionScore()));
        }
        if (profile.reasoningRequired() || profile.complexity() == AutoRequestProfile.Complexity.COMPLEX) {
            score = average(score, safeScore(candidate.getReasoningScore()));
        }
        return score;
    }

    private double capabilityScore(RouteCandidate candidate, AutoRequestProfile profile) {
        int matched = 0;
        int total = 0;
        total++;
        if (Boolean.FALSE.equals(candidate.getSupportsStream())) {
            matched += profile.streamRequired() ? 0 : 1;
        } else {
            matched++;
        }
        if (profile.jsonRequired()) {
            total++;
            matched += enabled(candidate.getSupportsJson()) ? 1 : 0;
        }
        if (profile.toolsRequired()) {
            total++;
            matched += enabled(candidate.getSupportsTools()) ? 1 : 0;
        }
        if (profile.visionRequired()) {
            total++;
            matched += enabled(candidate.getSupportsVision()) ? 1 : 0;
        }
        if (profile.reasoningRequired()) {
            total++;
            matched += enabled(candidate.getSupportsReasoning()) ? 1 : 0;
        }
        return MAX_SCORE * (matched / (double) total);
    }

    private boolean enabled(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    private int safeScore(Integer value) {
        if (value == null) {
            return DEFAULT_SCORE;
        }
        return Math.max(0, Math.min(MAX_SCORE, value));
    }

    private int safePriority(RouteCandidate candidate) {
        return candidate.getProviderPriority() == null ? 0 : Math.max(0, candidate.getProviderPriority());
    }

    private int safeWeight(RouteCandidate candidate) {
        return candidate.getWeight() == null ? DEFAULT_WEIGHT : Math.max(1, candidate.getWeight());
    }

    private int safeBias(RouteCandidate candidate) {
        return candidate.getScoreBias() == null ? 0 : candidate.getScoreBias();
    }

    private double normalize(int value, int max) {
        if (max <= 0) {
            return 0;
        }
        return MAX_SCORE * Math.min(value, max) / (double) max;
    }

    private double average(double... values) {
        double total = 0;
        for (double value : values) {
            total += value;
        }
        return total / values.length;
    }
}
