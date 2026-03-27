package com.code.aigateway.core.router;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 路由配置快照（不可变）
 *
 * <p>在内存中维护所有路由规则的聚合视图，
 * 路由热路径通过读取此快照完成，无需访问 MySQL 或 Redis。</p>
 */
public final class RoutingConfigSnapshot {

    /** aliasName -> 按优先级排序后的候选规则列表 */
    private final Map<String, List<RouteCandidate>> aliasRouteMap;

    /** providerCode -> 提供商运行时配置 */
    private final Map<String, ProviderEntry> providerMap;

    /** 快照版本号，用于识别快照是否已刷新 */
    private final long version;

    /** 快照创建时间戳，单位毫秒 */
    private final long createdAt;

    /** 快照来源，例如 startup、manual-refresh */
    private final String source;

    public RoutingConfigSnapshot(Map<String, List<RouteCandidate>> aliasRouteMap,
                                 Map<String, ProviderEntry> providerMap,
                                 long version,
                                 String source) {
        // 对别名路由表做不可变包装，避免外部引用修改内部快照状态。
        this.aliasRouteMap = Collections.unmodifiableMap(
                aliasRouteMap.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> List.copyOf(entry.getValue())
                        ))
        );
        // 对提供商配置表做不可变包装，确保快照整体只读。
        this.providerMap = Collections.unmodifiableMap(Map.copyOf(providerMap));
        this.version = version;
        this.createdAt = System.currentTimeMillis();
        this.source = source;
    }

    /**
     * 获取指定别名对应的候选规则列表。
     *
     * @param aliasName 模型别名
     * @return 候选规则列表；若不存在则返回空列表，避免空指针
     */
    public List<RouteCandidate> getCandidates(String aliasName) {
        return aliasRouteMap.getOrDefault(aliasName, Collections.emptyList());
    }

    /**
     * 获取别名到候选规则列表的映射表。
     *
     * <p>暴露该 Getter 主要用于 JSON 序列化与远程快照分发。</p>
     */
    public Map<String, List<RouteCandidate>> getAliasRouteMap() {
        return aliasRouteMap;
    }

    /**
     * 获取提供商映射表。
     */
    public Map<String, ProviderEntry> getProviderMap() {
        return providerMap;
    }

    /**
     * 获取快照版本号。
     */
    public long getVersion() {
        return version;
    }

    /**
     * 获取快照创建时间。
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * 获取快照来源。
     */
    public String getSource() {
        return source;
    }

    /**
     * 获取当前快照中别名路由映射数量。
     */
    public int getCandidatesMapSize() {
        return aliasRouteMap.size();
    }

    /**
     * 提供商运行时配置条目。
     *
     * <p>该对象已经是聚合后的只读视图，
     * 其中 apiKey 为运行时解密后的明文，仅在内存快照中使用。</p>
     */
    public record ProviderEntry(
            String providerType,
            String providerCode,
            boolean enabled,
            String baseUrl,
            String apiKey,
            String apiVersion,
            int timeoutSeconds,
            int priority
    ) {
    }
}
