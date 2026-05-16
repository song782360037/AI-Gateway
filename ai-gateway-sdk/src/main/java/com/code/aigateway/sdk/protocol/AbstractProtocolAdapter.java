package com.code.aigateway.sdk.protocol;

import com.code.aigateway.sdk.error.ErrorCode;
import com.code.aigateway.sdk.error.ProtocolException;
import com.code.aigateway.sdk.model.UnifiedPart;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 协议适配器抽象基类
 * <p>
 * 提供所有协议适配器共享的工具方法：Map 转换、参数校验、JSON 处理等。
 * </p>
 */
public abstract class AbstractProtocolAdapter implements ProtocolAdapter {

    private static final Logger log = LoggerFactory.getLogger(AbstractProtocolAdapter.class);

    protected final ObjectMapper objectMapper;

    protected AbstractProtocolAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 安全地将 Map<?, ?> 转为 Map<String, Object> */
    protected static Map<String, Object> toStringMap(Map<?, ?> map) {
        return ProtocolUtils.toStringMap(map);
    }

    /** 将任意对象转为 Map，支持 Map 输入和 Jackson convertValue */
    protected Map<String, Object> toMap(Object obj, String paramName) {
        return ProtocolUtils.toMap(objectMapper, obj, paramName);
    }

    /** 获取必需的字符串值，缺失或空白时抛出异常 */
    protected String requireString(Map<String, Object> map, String key, String message) {
        return ProtocolUtils.requireString(map, key, message);
    }

    /** 获取必需的 List<Map> 值 */
    protected List<Map<String, Object>> requireList(Map<String, Object> map, String key, String message) {
        return ProtocolUtils.requireList(map, key, message);
    }

    /** 创建文本类型的 UnifiedPart */
    protected static UnifiedPart textPart(String text) {
        return ProtocolUtils.textPart(text);
    }

    /** 将对象序列化为 JSON 字符串，失败时返回 "{}" */
    protected String stringify(Object obj) {
        return ProtocolUtils.stringify(objectMapper, obj);
    }

    /** 将 JSON 字符串参数解析为 Map，失败时返回空 Map */
    protected Object parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.debug("[ProtocolSDK] parseArguments failed, returning empty map", e);
            return Map.of();
        }
    }

    /**
     * OpenAI 家族共享的错误响应格式：{error: {message, type, code, param}}
     * <p>Embedding、Rerank 等使用此 OpenAI 格式的适配器直接继承，无需覆写。</p>
     */
    @Override
    public Object buildError(String message, String errorType, String code, String param) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("message", message);
        error.put("type", errorType);
        error.put("code", code);
        if (param != null) {
            error.put("param", param);
        }
        return Map.of("error", error);
    }

    /**
     * OpenAI 家族共享的 ErrorCode → 错误类型映射。
     * <p>Anthropic / Gemini 等使用不同错误格式的适配器需覆写此方法。</p>
     */
    @Override
    public String mapErrorType(ErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_REQUEST, MODEL_NOT_FOUND, CAPABILITY_NOT_SUPPORTED -> "invalid_request_error";
            case AUTH_FAILED, PROVIDER_AUTH_ERROR -> "authentication_error";
            case RATE_LIMITED, PROVIDER_RATE_LIMIT -> "rate_limit_error";
            case PROVIDER_BAD_REQUEST -> "invalid_request_error";
            case PROVIDER_RESOURCE_NOT_FOUND, PROVIDER_NOT_FOUND -> "invalid_request_error";
            case PROVIDER_TIMEOUT -> "server_error";
            case PROVIDER_CIRCUIT_OPEN, PROVIDER_DISABLED -> "server_error";
            case PROVIDER_ERROR, PROVIDER_SERVER_ERROR -> "server_error";
            default -> "server_error";
        };
    }
}
