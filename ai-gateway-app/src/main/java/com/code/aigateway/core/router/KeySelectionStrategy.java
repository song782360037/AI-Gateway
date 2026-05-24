package com.code.aigateway.core.router;

/**
 * 提供商 API Key 选择策略
 */
public enum KeySelectionStrategy {
    /** 轮询（按计数器依次选择） */
    ROUND_ROBIN,
    /** 加权随机 */
    RANDOM,
    /** 按排序号降级（sortOrder 越小越优先） */
    FALLBACK;

    /**
     * 从字符串解析策略，null 或空白默认 ROUND_ROBIN。
     */
    public static KeySelectionStrategy from(String value) {
        if (value == null || value.isBlank()) {
            return ROUND_ROBIN;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ROUND_ROBIN;
        }
    }
}
