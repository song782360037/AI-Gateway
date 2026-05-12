package com.code.aigateway.sdk.protocol;

import com.code.aigateway.sdk.error.ErrorCode;
import com.code.aigateway.sdk.error.ProtocolException;
import com.code.aigateway.sdk.model.UnifiedPart;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 协议适配器共享工具方法
 * <p>
 * 提供 Map 转换、参数校验、JSON 处理、data URI 解析等基础能力，
 * 供所有 ProtocolAdapter 和 RequestParser 复用。
 * </p>
 */
final class ProtocolUtils {

    private static final Logger log = LoggerFactory.getLogger(ProtocolUtils.class);

    private ProtocolUtils() {}

    /**
     * 构建流式错误响应体，供 ProtocolAdapter 及其子类复用。
     * <p>将错误异常转换为协议特定的错误 Map，通过 {@link ProtocolAdapter#buildError} 生成。</p>
     *
     * @param throwable 异常
     * @param adapter   协议适配器（用于 buildError 和 mapErrorType）
     * @return 协议特定的错误响应对象
     */
    public static Object buildStreamErrorBody(Throwable throwable, ProtocolAdapter adapter) {
        ErrorCode errorCode = throwable instanceof ProtocolException pe
                ? pe.getErrorCode()
                : ErrorCode.INTERNAL_ERROR;
        String message = throwable.getMessage() == null || throwable.getMessage().isBlank()
                ? "internal server error"
                : throwable.getMessage();
        String param = throwable instanceof ProtocolException pe ? pe.getParam() : null;
        return adapter.buildError(message, adapter.mapErrorType(errorCode), errorCode.name(), param);
    }

    /** 安全地将 Map<?, ?> 转为 Map<String, Object> */
    static Map<String, Object> toStringMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((k, v) -> { if (k instanceof String key) result.put(key, v); });
        return result;
    }

    /** 将任意对象转为 Map，支持 Map 输入和 Jackson convertValue */
    static Map<String, Object> toMap(ObjectMapper objectMapper, Object obj, String paramName) {
        if (obj instanceof Map<?, ?> map) {
            return toStringMap(map);
        }
        try {
            return objectMapper.convertValue(obj, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            throw new ProtocolException(ErrorCode.INVALID_REQUEST, paramName + " must be a map/object", paramName);
        }
    }

    /** 获取必需的字符串值，缺失或空白时抛出异常 */
    static String requireString(Map<String, Object> map, String key, String message) {
        Object value = map.get(key);
        if (!(value instanceof String str) || str.isBlank()) {
            throw new ProtocolException(ErrorCode.INVALID_REQUEST, message, key);
        }
        return str;
    }

    /** 获取必需的 List<Map> 值 */
    static List<Map<String, Object>> requireList(Map<String, Object> map, String key, String message) {
        Object value = map.get(key);
        if (!(value instanceof List<?> list)) {
            throw new ProtocolException(ErrorCode.INVALID_REQUEST, message, key);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                result.add(toStringMap(m));
            }
        }
        return result;
    }

    /** 创建文本类型的 UnifiedPart */
    static UnifiedPart textPart(String text) {
        UnifiedPart part = new UnifiedPart();
        part.setType("text");
        part.setText(text);
        return part;
    }

    /** 将对象序列化为 JSON 字符串，失败时返回 "{}" 并记录警告日志 */
    static String stringify(ObjectMapper objectMapper, Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON 序列化失败: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 解析 data: URI 格式的图片 URL
     * <p>
     * 支持 data:mimeType;base64,xxx 格式，自动提取 mimeType 和 base64Data。
     * 非 data: URI 直接设置 url 字段。
     * </p>
     */
    static UnifiedPart parseDataUri(String url) {
        UnifiedPart part = new UnifiedPart();
        part.setType("image");
        if (url != null && url.startsWith("data:")) {
            int commaIdx = url.indexOf(',');
            int semiIdx = url.indexOf(';');
            if (commaIdx > 0 && semiIdx > 0 && semiIdx < commaIdx) {
                part.setMimeType(url.substring(5, semiIdx));
                part.setBase64Data(url.substring(commaIdx + 1));
            } else {
                part.setUrl(url);
            }
        } else {
            part.setUrl(url);
        }
        return part;
    }
}
