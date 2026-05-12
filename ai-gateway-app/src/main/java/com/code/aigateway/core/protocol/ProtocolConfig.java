package com.code.aigateway.core.protocol;

import com.code.aigateway.sdk.AiGatewaySdk;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SDK 协议适配器 Bean 配置
 * <p>
 * 将无状态的 SDK 适配器实例声明为 Spring Bean，统一注入到 App 层适配器中。
 * 同时暴露 {@link AiGatewaySdk} 门面 Bean，供全局异常处理器等处一行式使用。
 * </p>
 */
@Configuration
public class ProtocolConfig {

    @Bean
    public com.code.aigateway.sdk.protocol.OpenAiChatProtocolAdapter openAiChatSdkAdapter(ObjectMapper objectMapper) {
        return new com.code.aigateway.sdk.protocol.OpenAiChatProtocolAdapter(objectMapper);
    }

    @Bean
    public com.code.aigateway.sdk.protocol.OpenAiResponsesProtocolAdapter openAiResponsesSdkAdapter(ObjectMapper objectMapper) {
        return new com.code.aigateway.sdk.protocol.OpenAiResponsesProtocolAdapter(objectMapper);
    }

    @Bean
    public com.code.aigateway.sdk.protocol.AnthropicProtocolAdapter anthropicSdkAdapter(ObjectMapper objectMapper) {
        return new com.code.aigateway.sdk.protocol.AnthropicProtocolAdapter(objectMapper);
    }

    @Bean
    public com.code.aigateway.sdk.protocol.GeminiProtocolAdapter geminiSdkAdapter(ObjectMapper objectMapper) {
        return new com.code.aigateway.sdk.protocol.GeminiProtocolAdapter(objectMapper);
    }

    @Bean
    public AiGatewaySdk aiGatewaySdk(
            com.code.aigateway.sdk.protocol.OpenAiChatProtocolAdapter openAiChatSdkAdapter,
            com.code.aigateway.sdk.protocol.OpenAiResponsesProtocolAdapter openAiResponsesSdkAdapter,
            com.code.aigateway.sdk.protocol.AnthropicProtocolAdapter anthropicSdkAdapter,
            com.code.aigateway.sdk.protocol.GeminiProtocolAdapter geminiSdkAdapter,
            ObjectMapper objectMapper) {
        return new AiGatewaySdk(
                com.code.aigateway.sdk.registry.ProtocolRegistry.builder()
                        .register(openAiChatSdkAdapter)
                        .register(openAiResponsesSdkAdapter)
                        .register(anthropicSdkAdapter)
                        .register(geminiSdkAdapter)
                        .build(),
                objectMapper);
    }
}
