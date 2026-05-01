package com.code.aigateway.core.router.auto;

/**
 * Auto 路由候选评分结果。
 */
public record AutoRouteScore(
        double totalScore,
        double policyFitScore,
        double taskFitScore,
        double capabilityScore,
        double priorityScore,
        double reliabilityScore
) {
}
