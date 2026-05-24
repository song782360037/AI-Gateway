package com.code.aigateway.core.router;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 提供商 API Key 选择策略组件
 *
 * <p>支持三种策略：轮询（ROUND_ROBIN）、加权随机（RANDOM）、降级（FALLBACK）。
 * 轮询状态使用内存 AtomicInteger，不持久化，重启后重置。</p>
 */
@Slf4j
@Component
public class ProviderKeySelector {

    /** providerCode -> 轮询计数器 */
    private final ConcurrentHashMap<String, AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();

    /**
     * 根据策略从 Key 列表中选择一个 Key。
     */
    public ProviderKeyEntry select(String providerCode, List<ProviderKeyEntry> keys, KeySelectionStrategy strategy) {
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        if (keys.size() == 1) {
            return keys.get(0);
        }

        KeySelectionStrategy s = strategy != null ? strategy : KeySelectionStrategy.ROUND_ROBIN;
        return switch (s) {
            case ROUND_ROBIN -> selectRoundRobin(providerCode, keys);
            case RANDOM -> selectWeightedRandom(keys);
            case FALLBACK -> selectFallback(keys);
        };
    }

    private ProviderKeyEntry selectRoundRobin(String providerCode, List<ProviderKeyEntry> keys) {
        AtomicInteger counter = roundRobinCounters.computeIfAbsent(providerCode, k -> new AtomicInteger(0));
        // 使用 & Integer.MAX_VALUE 避免 Math.abs(Integer.MIN_VALUE) 仍为负数
        int index = (counter.getAndIncrement() & Integer.MAX_VALUE) % keys.size();
        return keys.get(index);
    }

    private ProviderKeyEntry selectWeightedRandom(List<ProviderKeyEntry> keys) {
        if (keys.size() == 1) {
            return keys.get(0);
        }
        int totalWeight = keys.stream().mapToInt(ProviderKeyEntry::weight).sum();
        if (totalWeight <= 0) {
            // 所有权重非正，退化为均匀随机
            return keys.get(ThreadLocalRandom.current().nextInt(keys.size()));
        }
        int r = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        for (ProviderKeyEntry key : keys) {
            cumulative += key.weight();
            if (r < cumulative) {
                return key;
            }
        }
        return keys.get(keys.size() - 1);
    }

    /**
     * 清理不再存在于快照中的 Provider 轮询计数器，避免内存泄漏。
     *
     * @param activeProviderCodes 当前快照中活跃的 Provider 编码集合
     */
    public void cleanupStaleCounters(java.util.Set<String> activeProviderCodes) {
        roundRobinCounters.keySet().retainAll(activeProviderCodes);
    }

    private ProviderKeyEntry selectFallback(List<ProviderKeyEntry> keys) {
        return keys.stream()
                .min(Comparator.comparingInt(ProviderKeyEntry::sortOrder))
                .orElse(keys.get(0));
    }
}
