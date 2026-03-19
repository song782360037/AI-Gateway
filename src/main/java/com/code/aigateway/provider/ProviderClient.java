package com.code.aigateway.provider;

import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedResponse;
import com.code.aigateway.core.model.UnifiedStreamEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * AI 提供商客户端接口
 * <p>
 * 定义了与不同 AI 提供商（如 OpenAI、Anthropic、Gemini）交互的统一接口。
 * 各提供商需要实现此接口，处理统一的请求模型并返回统一的响应模型。
 * </p>
 *
 * @author sst
 */
public interface ProviderClient {

    /**
     * 获取该客户端支持的提供商类型
     *
     * @return 提供商类型枚举值
     */
    ProviderType getProviderType();

    /**
     * 发送非流式聊天请求
     *
     * @param request 统一的请求模型
     * @return 包含统一响应的 Mono
     */
    Mono<UnifiedResponse> chat(UnifiedRequest request);

    /**
     * 发送流式聊天请求
     *
     * @param request 统一的请求模型
     * @return 包含流式事件的 Flux
     */
    Flux<UnifiedStreamEvent> streamChat(UnifiedRequest request);
}
