package com.code.aigateway.core;

/**
 * Gateway 请求 metadata key 常量。
 * 用于 UnifiedRequest.getMetadata() 的存取，统一管理避免拼写错误。
 */
public final class GatewayMetadataKeys {

    private GatewayMetadataKeys() {}

    /** 该 Provider 所有可用的 API Key 列表（List<ProviderKeyEntry>） */
    public static final String PROVIDER_KEY_ENTRIES = "providerKeyEntries";

    /** Key 选择策略（KeySelectionStrategy 枚举或字符串） */
    public static final String KEY_SELECTION_STRATEGY = "keySelectionStrategy";

    /** 本次请求选中的 API Key 脱敏标识（前8后4格式） */
    public static final String USED_API_KEY_PREFIX = "usedApiKeyPrefix";

    /** Thinking 兼容模式（"full" / "simplified"） */
    public static final String THINKING_COMPAT_MODE = "thinkingCompatMode";

    /** 请求统计上下文（RequestStatsContext） */
    public static final String STATS_CONTEXT = "statsContext";
}
