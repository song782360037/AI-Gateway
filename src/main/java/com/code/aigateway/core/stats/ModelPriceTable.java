package com.code.aigateway.core.stats;

import java.util.Map;

/**
 * 模型价格表：基于常见模型的公开定价估算费用
 * <p>
 * 价格单位：USD / 百万 Token。数组格式为 [input_price, output_price]。
 * 未匹配的模型使用默认价格。
 * </p>
 */
public final class ModelPriceTable {

    /** 默认价格：输入 $1.00 / 百万 Token，输出 $3.00 / 百万 Token */
    private static final double[] DEFAULT_PRICE = {1.00, 3.00};

    private static final Map<String, double[]> PRICES = Map.ofEntries(
            Map.entry("gpt-4o", new double[]{2.50, 10.00}),
            Map.entry("gpt-4o-mini", new double[]{0.15, 0.60}),
            Map.entry("gpt-3.5-turbo", new double[]{0.50, 1.50}),
            Map.entry("gpt-4-turbo", new double[]{10.00, 30.00}),
            Map.entry("o1-mini", new double[]{3.00, 12.00}),
            Map.entry("claude-sonnet-4-20250514", new double[]{3.00, 15.00}),
            Map.entry("claude-haiku-4-20250506", new double[]{0.80, 4.00}),
            Map.entry("claude-opus-4-20250514", new double[]{15.00, 75.00}),
            Map.entry("deepseek-chat", new double[]{0.14, 0.28}),
            Map.entry("gemini-2.0-flash", new double[]{0.10, 0.40})
    );

    private ModelPriceTable() {
    }

    /**
     * 根据目标模型和 Token 用量估算费用（USD）
     *
     * @param targetModel      目标模型名称
     * @param promptTokens     输入 Token 数
     * @param completionTokens 输出 Token 数
     * @return 估算费用（USD）
     */
    public static double estimateCost(String targetModel, int promptTokens, int completionTokens) {
        double[] price = PRICES.getOrDefault(targetModel, DEFAULT_PRICE);
        return (promptTokens * price[0] + completionTokens * price[1]) / 1_000_000.0;
    }
}
