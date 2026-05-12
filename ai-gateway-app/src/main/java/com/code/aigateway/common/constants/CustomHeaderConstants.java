package com.code.aigateway.common.constants;

import java.util.Set;

/**
 * 自定义请求头相关常量。
 * <p>受保护头、合法键名正则等统一定义，避免多处重复导致维护遗漏。</p>
 */
public final class CustomHeaderConstants {

    private CustomHeaderConstants() {}

    /**
     * 受保护的认证相关请求头（小写），不允许通过自定义头设置。
     * <p>新增受保护头时只需修改此一处，所有使用方自动同步。</p>
     * <p><b>⚠️ 重要：修改此列表后，必须同步更新前端
     * {@code frontend-vue/src/constants/customHeaders.ts} 中的 PROTECTED_HEADERS，
     * 否则前端校验与后端不一致，用户可能提交被后端拒绝的头。</b></p>
     */
    public static final Set<String> PROTECTED_HEADERS = Set.of(
            "authorization",
            "x-api-key",
            "x-goog-api-key",
            "anthropic-version"
    );

    /**
     * 合法的 HTTP header name 正则：允许字母、数字、'-'、'_'、'.'。
     */
    public static final String VALID_HEADER_NAME_REGEX = "^[a-zA-Z0-9\\-_.]+$";
}
