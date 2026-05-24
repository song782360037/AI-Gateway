package com.code.aigateway.core.router;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ProviderKeySelector 单元测试
 *
 * <p>覆盖三种策略：ROUND_ROBIN、RANDOM、FALLBACK，以及边界条件。</p>
 */
class ProviderKeySelectorTest {

    private ProviderKeySelector selector;

    @BeforeEach
    void setUp() {
        selector = new ProviderKeySelector();
    }

    // ==================== 辅助方法 ====================

    private ProviderKeyEntry buildKey(Long id, int weight, int sortOrder) {
        return new ProviderKeyEntry(id, "sk-key-" + id, "sk-key" + id + "****", weight, sortOrder);
    }

    // ==================== null / 空列表 ====================

    @Nested
    @DisplayName("边界条件：null 和空列表")
    class EdgeCases {

        @Test
        @DisplayName("keys 为 null 时返回 null")
        void shouldReturnNullForNullKeys() {
            assertNull(selector.select("provider", null, KeySelectionStrategy.ROUND_ROBIN));
        }

        @Test
        @DisplayName("keys 为空列表时返回 null")
        void shouldReturnNullForEmptyKeys() {
            assertNull(selector.select("provider", List.of(), KeySelectionStrategy.ROUND_ROBIN));
        }

        @Test
        @DisplayName("keys 仅一个元素时直接返回该元素（不依赖策略）")
        void shouldReturnSingleKeyDirectly() {
            ProviderKeyEntry key = buildKey(1L, 100, 0);
            assertEquals(key, selector.select("provider", List.of(key), KeySelectionStrategy.RANDOM));
        }

        @Test
        @DisplayName("strategy 为 null 时默认 ROUND_ROBIN")
        void shouldDefaultToRoundRobinWhenStrategyIsNull() {
            ProviderKeyEntry key1 = buildKey(1L, 100, 0);
            ProviderKeyEntry key2 = buildKey(2L, 100, 1);
            List<ProviderKeyEntry> keys = List.of(key1, key2);

            // 多次调用应轮询返回
            Set<Long> selectedIds = new HashSet<>();
            for (int i = 0; i < 10; i++) {
                selectedIds.add(selector.select("provider", keys, null).id());
            }
            assertEquals(2, selectedIds.size(), "默认策略应轮询返回两个 Key");
        }
    }

    // ==================== ROUND_ROBIN ====================

    @Nested
    @DisplayName("ROUND_ROBIN 策略")
    class RoundRobin {

        @Test
        @DisplayName("轮询依次返回各 Key")
        void shouldCycleThroughKeys() {
            ProviderKeyEntry key1 = buildKey(1L, 100, 0);
            ProviderKeyEntry key2 = buildKey(2L, 100, 1);
            ProviderKeyEntry key3 = buildKey(3L, 100, 2);
            List<ProviderKeyEntry> keys = List.of(key1, key2, key3);

            assertEquals(1L, selector.select("rr-provider", keys, KeySelectionStrategy.ROUND_ROBIN).id());
            assertEquals(2L, selector.select("rr-provider", keys, KeySelectionStrategy.ROUND_ROBIN).id());
            assertEquals(3L, selector.select("rr-provider", keys, KeySelectionStrategy.ROUND_ROBIN).id());
            // 循环回到第一个
            assertEquals(1L, selector.select("rr-provider", keys, KeySelectionStrategy.ROUND_ROBIN).id());
        }

        @Test
        @DisplayName("不同 provider 各自独立轮询")
        void shouldMaintainSeparateCountersPerProvider() {
            ProviderKeyEntry key1 = buildKey(1L, 100, 0);
            ProviderKeyEntry key2 = buildKey(2L, 100, 1);
            List<ProviderKeyEntry> keys = List.of(key1, key2);

            assertEquals(1L, selector.select("provider-a", keys, KeySelectionStrategy.ROUND_ROBIN).id());
            assertEquals(1L, selector.select("provider-b", keys, KeySelectionStrategy.ROUND_ROBIN).id());
            assertEquals(2L, selector.select("provider-a", keys, KeySelectionStrategy.ROUND_ROBIN).id());
        }
    }

    // ==================== RANDOM ====================

    @Nested
    @DisplayName("RANDOM 策略")
    class WeightedRandom {

        @Test
        @DisplayName("加权随机：权重越高被选中概率越大")
        void shouldRespectWeights() {
            // key1 权重 100，key2 权重 0
            ProviderKeyEntry key1 = buildKey(1L, 100, 0);
            ProviderKeyEntry key2 = buildKey(2L, 0, 1);
            List<ProviderKeyEntry> keys = List.of(key1, key2);

            // 权重 100 vs 0，key1 应始终被选中
            for (int i = 0; i < 100; i++) {
                assertEquals(1L, selector.select("rand-provider", keys, KeySelectionStrategy.RANDOM).id());
            }
        }

        @Test
        @DisplayName("所有权重为 0 时退化为均匀随机")
        void shouldFallbackToUniformRandomWhenAllWeightsZero() {
            ProviderKeyEntry key1 = buildKey(1L, 0, 0);
            ProviderKeyEntry key2 = buildKey(2L, 0, 1);
            List<ProviderKeyEntry> keys = List.of(key1, key2);

            Set<Long> selectedIds = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                selectedIds.add(selector.select("zero-weight", keys, KeySelectionStrategy.RANDOM).id());
            }
            assertEquals(2, selectedIds.size(), "两个 Key 都应有概率被选中");
        }
    }

    // ==================== FALLBACK ====================

    @Nested
    @DisplayName("FALLBACK 策略")
    class Fallback {

        @Test
        @DisplayName("始终返回 sortOrder 最小的 Key")
        void shouldAlwaysSelectLowestSortOrder() {
            ProviderKeyEntry key1 = buildKey(1L, 100, 5);
            ProviderKeyEntry key2 = buildKey(2L, 100, 1);
            ProviderKeyEntry key3 = buildKey(3L, 100, 10);
            List<ProviderKeyEntry> keys = List.of(key1, key2, key3);

            for (int i = 0; i < 10; i++) {
                assertEquals(2L, selector.select("fb-provider", keys, KeySelectionStrategy.FALLBACK).id(),
                        "FALLBACK 应始终选择 sortOrder 最小的 Key");
            }
        }

        @Test
        @DisplayName("sortOrder 相同时选择列表中第一个")
        void shouldSelectFirstWhenSortOrderEqual() {
            ProviderKeyEntry key1 = buildKey(1L, 100, 0);
            ProviderKeyEntry key2 = buildKey(2L, 100, 0);
            List<ProviderKeyEntry> keys = List.of(key1, key2);

            assertEquals(1L, selector.select("fb-equal", keys, KeySelectionStrategy.FALLBACK).id());
        }
    }

    // ==================== cleanupStaleCounters ====================

    @Nested
    @DisplayName("cleanupStaleCounters")
    class Cleanup {

        @Test
        @DisplayName("清理后只保留活跃 provider 的计数器")
        void shouldRemoveStaleCounters() {
            ProviderKeyEntry key1 = buildKey(1L, 100, 0);
            ProviderKeyEntry key2 = buildKey(2L, 100, 1);
            List<ProviderKeyEntry> keys = List.of(key1, key2);

            // 创建两个 provider 的计数器（使用 2 个 Key 确保轮询计数器递增）
            selector.select("active-provider", keys, KeySelectionStrategy.ROUND_ROBIN);
            selector.select("stale-provider", keys, KeySelectionStrategy.ROUND_ROBIN);

            // 只保留 active-provider
            selector.cleanupStaleCounters(Set.of("active-provider"));

            // active-provider 的计数器应继续工作（已调用 1 次，下次返回第 2 个 Key）
            assertEquals(2L, selector.select("active-provider", keys, KeySelectionStrategy.ROUND_ROBIN).id());

            // stale-provider 的计数器被清理，应重新从头开始
            assertEquals(1L, selector.select("stale-provider", keys, KeySelectionStrategy.ROUND_ROBIN).id());
        }
    }
}
