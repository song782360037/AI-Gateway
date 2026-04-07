package com.code.aigateway.core.router;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 路由配置快照（不可变）
 *
 * <p>在内存中维护所有路由规则的聚合视图，
 * 路由热路径通过读取此快照完成，无需访问 MySQL 或 Redis。</p>
 */
public final class RoutingConfigSnapshot {

    /** aliasName -> 按优先级排序后的候选规则列表（仅 EXACT 精确匹配） */
    private final Map<String, List<RouteCandidate>> aliasRouteMap;

    /** GLOB / REGEX 模式匹配规则列表（预编译），按匹配类型分组 */
    private final List<PatternRoute> patternRoutes;

    /** providerCode -> 提供商运行时配置 */
    private final Map<String, ProviderEntry> providerMap;

    /** 快照版本号，用于识别快照是否已刷新 */
    private final long version;

    /** 快照创建时间戳，单位毫秒 */
    private final long createdAt;

    /** 快照来源，例如 startup、manual-refresh */
    private final String source;

    public RoutingConfigSnapshot(Map<String, List<RouteCandidate>> aliasRouteMap,
                                 List<PatternRoute> patternRoutes,
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
        // 对模式匹配规则做不可变包装
        this.patternRoutes = List.copyOf(patternRoutes);
        // 对提供商配置表做不可变包装，确保快照整体只读。
        this.providerMap = Collections.unmodifiableMap(Map.copyOf(providerMap));
        this.version = version;
        this.createdAt = System.currentTimeMillis();
        this.source = source;
    }

    /**
     * 获取指定别名对应的候选规则列表（精确匹配）。
     *
     * @param aliasName 模型别名
     * @return 候选规则列表；若不存在则返回空列表，避免空指针
     */
    public List<RouteCandidate> getCandidates(String aliasName) {
        return aliasRouteMap.getOrDefault(aliasName, Collections.emptyList());
    }

    /**
     * 获取模式匹配规则列表（GLOB / REGEX）。
     *
     * <p>返回预编译的模式匹配规则，用于在精确匹配未命中时遍历匹配。</p>
     */
    public List<PatternRoute> getPatternRoutes() {
        return patternRoutes;
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
     * 获取全部已启用提供商，按优先级降序排列。
     * <p>
     * 用于无路由规则时的模型名透传场景：请求模型未命中任何别名映射，
     * 但仍需将请求按 Provider 优先级发送至下游。
     * </p>
     */
    public List<ProviderEntry> getAllProvidersByPriority() {
        return providerMap.values().stream()
                .filter(ProviderEntry::enabled)
                .sorted(Comparator.comparingInt(ProviderEntry::priority).reversed())
                .toList();
    }

    /**
     * 模式匹配路由规则（GLOB / REGEX）。
     *
     * <p>使用普通不可变类而非 record，以便通过 {@link JsonIgnore} 排除 {@link #compiledPattern}
     * 不参与 JSON 序列化（Pattern 对象不可序列化，Redis 缓存只需 regex 字符串）。</p>
     */
    public static final class PatternRoute {
        private final MatchType matchType;
        private final String regex;
        private final String originalPattern;
        private final List<RouteCandidate> candidates;

        /** 惰性编译并缓存的 Pattern */
        private volatile Pattern compiledPattern;

        public PatternRoute(MatchType matchType, String regex, String originalPattern, List<RouteCandidate> candidates) {
            this.matchType = matchType;
            this.regex = regex;
            this.originalPattern = originalPattern;
            this.candidates = List.copyOf(candidates);
        }

        public MatchType matchType() { return matchType; }
        public String regex() { return regex; }
        public String originalPattern() { return originalPattern; }
        public List<RouteCandidate> candidates() { return candidates; }

        /** 获取预编译的 Pattern，首次调用时惰性编译并缓存 */
        @JsonIgnore
        public Pattern compiledPattern() {
            if (compiledPattern == null) {
                compiledPattern = Pattern.compile(regex);
            }
            return compiledPattern;
        }
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
            int timeoutSeconds,
            int priority,
            List<String> supportedProtocols
    ) {
    }
}
