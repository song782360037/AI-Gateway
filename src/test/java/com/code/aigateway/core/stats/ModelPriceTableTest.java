package com.code.aigateway.core.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelPriceTableTest {

    @Test
    void estimateCost_exactModel_usesConfiguredPrice() {
        double cost = ModelPriceTable.estimateCost("gpt-4o", 100_000, 50_000);
        assertEquals(0.75, cost, 0.000001);
    }

    @Test
    void estimateCost_versionedModel_usesPrefixPrice() {
        double cost = ModelPriceTable.estimateCost("gpt-4o-2024-08-06", 100_000, 50_000);
        assertEquals(0.75, cost, 0.000001);
    }

    @Test
    void estimateCost_claudeSnapshot_usesPrefixPrice() {
        double cost = ModelPriceTable.estimateCost("claude-sonnet-4-20250601", 100_000, 50_000);
        assertEquals(1.05, cost, 0.000001);
    }

    @Test
    void estimateCost_unknownModel_usesDefaultPrice() {
        double cost = ModelPriceTable.estimateCost("unknown-model", 100_000, 50_000);
        assertEquals(0.25, cost, 0.000001);
    }

    @Test
    void estimateCost_withCachedInputTokens_appliesDiscountedInputPrice() {
        double cost = ModelPriceTable.estimateCost("gpt-4o", 100_000, 40_000, 50_000);
        assertEquals(0.66, cost, 0.000001);
    }

    @Test
    void estimateCost_withCachedInputTokensGreaterThanInput_clampsRegularInputToZero() {
        double cost = ModelPriceTable.estimateCost("gpt-4o", 10_000, 20_000, 0);
        assertEquals(0.0025, cost, 0.000001);
    }
}
