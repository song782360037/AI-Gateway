package com.code.aigateway.core.stats;

import java.util.Locale;
import java.util.Map;

/**
 * 模型价格表：基于常见模型的公开定价估算费用
 * <p>
 * 价格单位：USD / 百万 Token。
 * 未匹配的模型使用默认价格。
 * </p>
 */
public final class ModelPriceTable {

    /** 默认价格：输入 $1.00 / 百万 Token，缓存输入为输入价 1/10，输出 $3.00 / 百万 Token */
    private static final Price DEFAULT_PRICE = new Price(1.00, 0.10, 3.00);

    private static final Map<String, Price> PRICES = Map.ofEntries(
            Map.entry("gpt-4o", new Price(2.50, 0.25, 10.00)),
            Map.entry("gpt-4o-mini", new Price(0.15, 0.015, 0.60)),
            Map.entry("gpt-3.5-turbo", new Price(0.50, 0.05, 1.50)),
            Map.entry("gpt-4-turbo", new Price(10.00, 1.00, 30.00)),
            Map.entry("o1-mini", new Price(3.00, 0.30, 12.00)),
            Map.entry("claude-sonnet-4-20250514", new Price(3.00, 0.30, 15.00)),
            Map.entry("claude-haiku-4-20250506", new Price(0.80, 0.08, 4.00)),
            Map.entry("claude-opus-4-20250514", new Price(15.00, 1.50, 75.00)),
            Map.entry("deepseek-chat", new Price(0.14, 0.014, 0.28)),
            Map.entry("gemini-2.0-flash", new Price(0.10, 0.01, 0.40))
    );

    private static final Map<String, Price> PRICE_PREFIXES = Map.ofEntries(
            Map.entry("gpt-4o-mini", PRICES.get("gpt-4o-mini")),
            Map.entry("gpt-4o", PRICES.get("gpt-4o")),
            Map.entry("gpt-3.5-turbo", PRICES.get("gpt-3.5-turbo")),
            Map.entry("gpt-4-turbo", PRICES.get("gpt-4-turbo")),
            Map.entry("o1-mini", PRICES.get("o1-mini")),
            Map.entry("claude-sonnet-4", PRICES.get("claude-sonnet-4-20250514")),
            Map.entry("claude-haiku-4", PRICES.get("claude-haiku-4-20250506")),
            Map.entry("claude-opus-4", PRICES.get("claude-opus-4-20250514")),
            Map.entry("deepseek-chat", PRICES.get("deepseek-chat")),
            Map.entry("gemini-2.0-flash", PRICES.get("gemini-2.0-flash"))
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
        return estimateCost(targetModel, promptTokens, 0, completionTokens);
    }

    /**
     * 根据目标模型和 Token 用量估算费用（USD），支持缓存输入 Token 折扣价。
     *
     * @param targetModel       目标模型名称
     * @param inputTokens       总输入 Token 数
     * @param cachedInputTokens 输入中命中缓存的 Token 数
     * @param completionTokens  输出 Token 数
     * @return 估算费用（USD）
     */
    public static double estimateCost(String targetModel, int inputTokens, int cachedInputTokens, int completionTokens) {
        Price price = resolvePrice(targetModel);
        int safeInputTokens = Math.max(inputTokens, 0);
        // 缓存 token 不超过总输入 token
        int billableCachedInputTokens = Math.min(Math.max(cachedInputTokens, 0), safeInputTokens);
        int regularInputTokens = safeInputTokens - billableCachedInputTokens;
        int safeCompletionTokens = Math.max(completionTokens, 0);
        return (regularInputTokens * price.inputPrice()
                + billableCachedInputTokens * price.cachedInputPrice()
                + safeCompletionTokens * price.outputPrice()) / 1_000_000.0;
    }

    private static Price resolvePrice(String targetModel) {
        if (targetModel == null || targetModel.isBlank()) {
            return DEFAULT_PRICE;
        }

        String normalizedModel = targetModel.trim().toLowerCase(Locale.ROOT);
        Price exactPrice = PRICES.get(normalizedModel);
        if (exactPrice != null) {
            return exactPrice;
        }

        for (Map.Entry<String, Price> entry : PRICE_PREFIXES.entrySet()) {
            if (normalizedModel.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return DEFAULT_PRICE;
    }

    private record Price(double inputPrice, double cachedInputPrice, double outputPrice) {
    }
}
