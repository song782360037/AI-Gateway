package com.code.aigateway.sdk;

import com.code.aigateway.sdk.error.ErrorCode;
import com.code.aigateway.sdk.model.ProtocolType;
import com.code.aigateway.sdk.model.UnifiedRequest;
import com.code.aigateway.sdk.model.UnifiedResponse;
import com.code.aigateway.sdk.protocol.AnthropicProtocolAdapter;
import com.code.aigateway.sdk.protocol.GeminiProtocolAdapter;
import com.code.aigateway.sdk.protocol.OpenAiChatProtocolAdapter;
import com.code.aigateway.sdk.protocol.OpenAiResponsesProtocolAdapter;
import com.code.aigateway.sdk.protocol.ProtocolAdapter;
import com.code.aigateway.sdk.registry.ProtocolRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Objects;

/**
 * AI Gateway SDK 门面
 * <p>
 * 提供一行式 API 简化协议解析、响应编码和错误构建等操作。
 * </p>
 *
 * <h3>基本用法</h3>
 * <pre>
 *   // 1. 默认构造（自动注册四个协议适配器）
 *   AiGatewaySdk sdk = new AiGatewaySdk(new ObjectMapper());
 *
 *   // 2. 一行解析请求
 *   UnifiedRequest request = sdk.parse(ProtocolType.OPENAI_CHAT, json);
 *
 *   // 3. 一行编码响应
 *   String response = sdk.encodeResponse(ProtocolType.OPENAI_CHAT, unifiedResponse);
 *
 *   // 4. 一行构建错误
 *   String error = sdk.buildError(ProtocolType.ANTHROPIC, "model not found", ErrorCode.INVALID_MODEL);
 *
 *   // 5. 获取适配器（高级用法）
 *   ProtocolAdapter adapter = sdk.adapter(ProtocolType.OPENAI_CHAT);
 * </pre>
 *
 * <h3>自定义注册表</h3>
 * <pre>
 *   ProtocolRegistry registry = ProtocolRegistry.builder()
 *       .register(new OpenAiChatProtocolAdapter(objectMapper))
 *       .build();
 *   AiGatewaySdk sdk = new AiGatewaySdk(registry, objectMapper);
 * </pre>
 */
public class AiGatewaySdk {

    private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {};

    private final ProtocolRegistry registry;
    private final ObjectMapper objectMapper;

    /**
     * 使用默认注册表构造（注册全部四个协议适配器）
     */
    public AiGatewaySdk(ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.objectMapper = objectMapper;
        this.registry = ProtocolRegistry.builder()
                .register(new OpenAiChatProtocolAdapter(objectMapper))
                .register(new AnthropicProtocolAdapter(objectMapper))
                .register(new GeminiProtocolAdapter(objectMapper))
                .register(new OpenAiResponsesProtocolAdapter(objectMapper))
                .build();
    }

    /**
     * 使用自定义注册表构造
     */
    public AiGatewaySdk(ProtocolRegistry registry, ObjectMapper objectMapper) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * 一行解析请求：JSON → UnifiedRequest
     *
     * @param protocol 请求协议类型
     * @param rawJson  原始请求 JSON 字符串
     * @return 统一请求模型
     */
    public UnifiedRequest parse(ProtocolType protocol, String rawJson) {
        Objects.requireNonNull(protocol, "protocol must not be null");
        Objects.requireNonNull(rawJson, "rawJson must not be null");

        Map<String, Object> rawMap;
        try {
            rawMap = objectMapper.readValue(rawJson, MAP_TYPE_REF);
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to parse JSON: " + e.getMessage(), e);
        }
        return adapter(protocol).parse(rawMap);
    }

    /**
     * 一行解析请求：Map → UnifiedRequest
     */
    public UnifiedRequest parse(ProtocolType protocol, Map<String, Object> rawMap) {
        Objects.requireNonNull(protocol, "protocol must not be null");
        Objects.requireNonNull(rawMap, "rawMap must not be null");
        return adapter(protocol).parse(rawMap);
    }

    /**
     * 一行编码响应：UnifiedResponse → JSON 字符串
     */
    public String encodeResponse(ProtocolType protocol, UnifiedResponse response) {
        Objects.requireNonNull(protocol, "protocol must not be null");
        Objects.requireNonNull(response, "response must not be null");

        Object encoded = adapter(protocol).encodeResponse(response);
        try {
            return objectMapper.writeValueAsString(encoded);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize response: " + e.getMessage(), e);
        }
    }

    /**
     * 一行构建错误响应
     *
     * @param protocol  目标协议类型
     * @param message   错误消息
     * @param errorCode 错误码枚举
     * @return 协议格式的错误 JSON 字符串
     */
    public String buildError(ProtocolType protocol, String message, ErrorCode errorCode) {
        Objects.requireNonNull(protocol, "protocol must not be null");
        Objects.requireNonNull(errorCode, "errorCode must not be null");

        ProtocolAdapter adapter = adapter(protocol);
        Object errorBody = adapter.buildError(
                message,
                adapter.mapErrorType(errorCode),
                errorCode.name(),
                null
        );
        try {
            return objectMapper.writeValueAsString(errorBody);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize error: " + e.getMessage(), e);
        }
    }

    /**
     * 一行构建错误响应（自定义 errorType）
     */
    public String buildError(ProtocolType protocol, String message, String errorType, String code) {
        Objects.requireNonNull(protocol, "protocol must not be null");
        Objects.requireNonNull(errorType, "errorType must not be null");
        Objects.requireNonNull(code, "code must not be null");

        Object errorBody = adapter(protocol).buildError(message, errorType, code, null);
        try {
            return objectMapper.writeValueAsString(errorBody);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize error: " + e.getMessage(), e);
        }
    }

    /**
     * 获取指定协议适配器（高级用法）
     */
    public ProtocolAdapter adapter(ProtocolType protocol) {
        return registry.getAdapter(protocol);
    }

    /**
     * 获取底层注册表
     */
    public ProtocolRegistry registry() {
        return registry;
    }
}
