package com.code.aigateway.config;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Jackson 全局配置
 * <p>
 * 处理客户端发送的非标准值（如 JavaScript undefined 被序列化为 "[undefined]" 字符串），
 * 避免反序列化失败导致 "Failed to read HTTP message" 错误。
 * </p>
 */
@Configuration
public class JacksonConfig {

    /** JavaScript 客户端可能将 undefined 转为的字符串标记 */
    private static final String UNDEFINED_MARKER = "[undefined]";

    /**
     * 定制 ObjectMapper，注册全局反序列化问题处理器
     * <p>
     * 当 Jackson 遇到类型不匹配的字符串值时（如 "[undefined]" 无法转为 Double），
     * 将其视为 null 而非抛出异常。这样网关对不规范客户端更健壮。
     * </p>
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer undefinedStringHandlerCustomizer() {
        return builder -> builder.postConfigurer(objectMapper ->
                objectMapper.addHandler(new UndefinedStringHandler())
        );
    }

    /**
     * 全局反序列化问题处理器
     * <p>
     * 处理场景：
     * <ul>
     *   <li>字符串 "[undefined]" 传入 Double/Integer/Boolean/List 等非 String 字段 → 返回 null</li>
     *   <li>其他类型不匹配 → 走 Jackson 默认行为（抛异常）</li>
     * </ul>
     * </p>
     */
    static class UndefinedStringHandler extends DeserializationProblemHandler {

        @Override
        public Object handleWeirdStringValue(DeserializationContext ctxt,
                                              Class<?> targetType,
                                              String valueToConvert,
                                              String failureMsg) throws IOException {
            // 客户端将 JS undefined 序列化为 "[undefined]" 字符串，视为 null
            if (UNDEFINED_MARKER.equals(valueToConvert)) {
                return null;
            }
            // 其他字符串类型不匹配走默认处理（抛异常）
            return super.handleWeirdStringValue(ctxt, targetType, valueToConvert, failureMsg);
        }
    }
}
