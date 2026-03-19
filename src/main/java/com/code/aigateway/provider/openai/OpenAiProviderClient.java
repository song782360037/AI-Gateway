package com.code.aigateway.provider.openai;

import com.code.aigateway.core.model.UnifiedOutput;
import com.code.aigateway.core.model.UnifiedPart;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedResponse;
import com.code.aigateway.core.model.UnifiedStreamEvent;
import com.code.aigateway.core.model.UnifiedUsage;
import com.code.aigateway.provider.ProviderClient;
import com.code.aigateway.provider.ProviderType;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * OpenAI 提供商客户端
 * <p>
 * 实现 ProviderClient 接口，负责与 OpenAI API 进行交互。
 * 当前为 MVP 阶段的桩实现，返回模拟数据。
 * </p>
 *
 * @author sst
 */
@Component
public class OpenAiProviderClient implements ProviderClient {

    /**
     * 返回 OpenAI 提供商类型
     *
     * @return ProviderType.OPENAI
     */
    @Override
    public ProviderType getProviderType() {
        return ProviderType.OPENAI;
    }

    /**
     * 发送非流式聊天请求
     * <p>
     * 当前为桩实现，返回模拟的响应数据。
     * 实际实现应调用 OpenAI API。
     * </p>
     *
     * @param request 统一的请求模型
     * @return 包含模拟响应的 Mono
     */
    @Override
    public Mono<UnifiedResponse> chat(UnifiedRequest request) {
        // 构建模拟的文本内容
        UnifiedPart part = new UnifiedPart();
        part.setType("text");
        part.setText("OpenAI provider stub response for model: " + request.getModel());

        // 构建输出
        UnifiedOutput output = new UnifiedOutput();
        output.setRole("assistant");
        output.setParts(List.of(part));

        // 构建使用统计
        UnifiedUsage usage = new UnifiedUsage();
        usage.setInputTokens(10);
        usage.setOutputTokens(12);
        usage.setTotalTokens(22);

        // 构建响应
        UnifiedResponse response = new UnifiedResponse();
        response.setId("chatcmpl-" + UUID.randomUUID());
        response.setModel(request.getModel());
        response.setProvider("openai");
        response.setFinishReason("stop");
        response.setUsage(usage);
        response.setOutputs(List.of(output));

        return Mono.just(response);
    }

    /**
     * 发送流式聊天请求
     * <p>
     * 当前为桩实现，返回模拟的流式事件。
     * 实际实现应调用 OpenAI Streaming API。
     * </p>
     *
     * @param request 统一的请求模型
     * @return 包含模拟流式事件的 Flux
     */
    @Override
    public Flux<UnifiedStreamEvent> streamChat(UnifiedRequest request) {
        // 构建模拟的文本增量事件
        UnifiedStreamEvent event1 = new UnifiedStreamEvent();
        event1.setType("text_delta");
        event1.setTextDelta("OpenAI ");

        UnifiedStreamEvent event2 = new UnifiedStreamEvent();
        event2.setType("text_delta");
        event2.setTextDelta("provider ");

        UnifiedStreamEvent event3 = new UnifiedStreamEvent();
        event3.setType("text_delta");
        event3.setTextDelta("stub.");

        // 构建完成事件
        UnifiedStreamEvent done = new UnifiedStreamEvent();
        done.setType("done");
        done.setFinishReason("stop");

        return Flux.just(event1, event2, event3, done);
    }
}
