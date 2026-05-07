package com.code.aigateway.common.util;

import com.code.aigateway.common.constants.CustomHeaderConstants;
import com.code.aigateway.common.exception.BizException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自定义请求头工具类，提供校验、合并、解析与应用能力。
 * <p>统一管理自定义请求头的业务逻辑，避免多处重复实现。</p>
 */
@Slf4j
public final class CustomHeaderUtils {

    private CustomHeaderUtils() {}

    /** 内部共享的 ObjectMapper，用于 JSON 解析 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ==================== 解析（JSON ↔ Map） ====================

    /**
     * 将 JSON 格式的请求头字符串解析为 Map。
     * <p>解析失败时返回空 Map 而非抛异常，用于从数据库加载脏数据的容错场景。</p>
     *
     * @param json JSON 格式的请求头字符串
     * @return 解析后的 Map，空或失败时返回空 Map
     */
    public static Map<String, String> parseHeadersJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, String> result = OBJECT_MAPPER.readValue(json,
                    new TypeReference<LinkedHashMap<String, String>>() {});
            return result != null && !result.isEmpty() ? result : Map.of();
        } catch (Exception e) {
            log.warn("[自定义请求头] 解析 JSON 失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 将请求头 Map 序列化为 JSON 字符串。
     *
     * @param headers 请求头 Map
     * @return JSON 字符串，空或 null 时返回 "{}"
     * @throws BizException 序列化失败时抛出
     */
    public static String serializeHeadersToJson(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "{}";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(headers);
        } catch (Exception e) {
            throw new BizException("INVALID_HEADER", "自定义请求头序列化失败");
        }
    }

    // ==================== 校验（管理入口，校验失败抛异常） ====================

    /**
     * 校验自定义请求头：键名合法性 + 非受保护头 + 值无 CRLF。
     * <p>校验失败时抛出 BizException，用于管理端写入接口。</p>
     *
     * @param headers 待校验的自定义请求头
     */
    public static void validateCustomHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null) {
                throw new BizException("INVALID_HEADER", "自定义请求头键不能为 null");
            }
            String trimmed = key.trim();
            if (trimmed.isEmpty()) {
                throw new BizException("INVALID_HEADER", "自定义请求头键不能为空白");
            }
            if (!trimmed.matches(CustomHeaderConstants.VALID_HEADER_NAME_REGEX)) {
                throw new BizException("INVALID_HEADER",
                        "自定义请求头键包含非法字符: " + key + "（只允许字母、数字、连字符、下划线和点号）");
            }
            // 校验值：禁止 CRLF 字符防止 HTTP 头注入
            if (value != null && (value.contains("\r") || value.contains("\n"))) {
                throw new BizException("INVALID_HEADER",
                        "自定义请求头值不允许包含换行符: " + trimmed);
            }
            if (CustomHeaderConstants.PROTECTED_HEADERS.contains(trimmed.toLowerCase())) {
                throw new BizException("INVALID_HEADER",
                        "不允许在自定义请求头中设置认证相关头: " + trimmed +
                                "（受保护头: Authorization, x-api-key, x-goog-api-key, anthropic-version）");
            }
        }
    }

    // ==================== 合并（运行时，脏数据静默过滤） ====================

    /**
     * 合并全局和提供商级别的自定义请求头。
     * <p>提供商级别的同名头会覆盖全局头。同时过滤掉受保护头、空白键、null 值、非法字符键名和含 CRLF 的值，防止脏数据。</p>
     *
     * @param globalHeaders   全局自定义请求头
     * @param providerHeaders 提供商级别自定义请求头
     * @param logPrefix       日志前缀，用于标识调用来源
     * @return 合并后的不可变请求头 Map
     */
    public static Map<String, String> mergeCustomHeaders(Map<String, String> globalHeaders,
                                                          Map<String, String> providerHeaders,
                                                          String logPrefix) {
        if ((globalHeaders == null || globalHeaders.isEmpty())
                && (providerHeaders == null || providerHeaders.isEmpty())) {
            return Map.of();
        }
        Map<String, String> merged = new HashMap<>();
        if (globalHeaders != null) {
            merged.putAll(globalHeaders);
        }
        if (providerHeaders != null) {
            merged.putAll(providerHeaders);
        }
        // 过滤脏数据：移除 null/空白键、null 值、非法字符键名、受保护头和含 CRLF 的值
        merged.entrySet().removeIf(entry -> {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isBlank() || value == null) {
                return true;
            }
            String trimmed = key.trim();
            if (!trimmed.matches(CustomHeaderConstants.VALID_HEADER_NAME_REGEX)) {
                log.warn("[{}] 跳过非法自定义请求头: {}", logPrefix, key);
                return true;
            }
            if (CustomHeaderConstants.PROTECTED_HEADERS.contains(trimmed.toLowerCase())) {
                log.warn("[{}] 跳过受保护的自定义请求头: {}", logPrefix, trimmed);
                return true;
            }
            // 过滤含 CRLF 的值，防止 HTTP 头注入
            if (value.contains("\r") || value.contains("\n")) {
                log.warn("[{}] 跳过包含换行符的自定义请求头值: {}", logPrefix, trimmed);
                return true;
            }
            return false;
        });
        return Map.copyOf(merged);
    }

    // ==================== 应用到 WebClient（运行时最终防线） ====================

    /**
     * 将自定义请求头应用到 WebClient.Builder。
     * <p>运行时最终防线：过滤掉受保护的认证相关头、空白/非法键名、null 值和含 CRLF 的值，
     * 防止数据库脏数据导致认证被覆盖、WebClient 抛异常或 HTTP 头注入。</p>
     *
     * @param builder       WebClient.Builder
     * @param customHeaders 自定义请求头
     * @param logPrefix     日志前缀
     */
    public static void applyCustomHeaders(org.springframework.web.reactive.function.client.WebClient.Builder builder,
                                           Map<String, String> customHeaders,
                                           String logPrefix) {
        if (customHeaders == null || customHeaders.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            // 跳过 null/空白键名和 null 值
            if (key == null || key.isBlank() || value == null) {
                continue;
            }
            String trimmedKey = key.trim();
            // 跳过非法字符键名
            if (!trimmedKey.matches(CustomHeaderConstants.VALID_HEADER_NAME_REGEX)) {
                log.warn("[{}] 跳过非法自定义请求头: {}", logPrefix, key);
                continue;
            }
            // 跳过受保护的认证头
            if (CustomHeaderConstants.PROTECTED_HEADERS.contains(trimmedKey.toLowerCase())) {
                continue;
            }
            // 跳过含 CRLF 的值，防止 HTTP 头注入
            if (value.contains("\r") || value.contains("\n")) {
                log.warn("[{}] 跳过包含换行符的自定义请求头值: {}", logPrefix, trimmedKey);
                continue;
            }
            builder.defaultHeader(trimmedKey, value);
        }
    }
}
