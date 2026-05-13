package com.code.aigateway.core.protocol;

import com.code.aigateway.core.model.StreamContext;
import com.code.aigateway.sdk.model.UnifiedStreamEvent;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * 协议适配器接口（App 层）
 * <p>
 * 继承 SDK 的协议互转能力，并在此基础上声明 Reactor/SSE 感知的流式编码方法。
 * 所有协议解析/响应编码/错误构建逻辑由 SDK {@link com.code.aigateway.sdk.protocol.ProtocolAdapter} 定义，
 * 本接口仅补充与 Spring WebFlux 耦合的 SSE 编码能力。
 * </p>
 * <p>
 * <b>实现约束</b>：所有实现类必须继承 {@link AbstractSdkProtocolAdapter}，
 * 不得直接实现本接口，否则将缺少 SDK 桥接逻辑。
 * </p>
 */
public interface ProtocolAdapter extends com.code.aigateway.sdk.protocol.ProtocolAdapter {

    /**
     * 将统一流式事件编码为 SSE 事件流
     * <p>
     * 一个 UnifiedStreamEvent 可能产生零个或多个 SSE 事件（如 Anthropic 首个 text_delta
     * 需要先发送 content_block_start 再发送 content_block_delta）。
     * </p>
     */
    Flux<ServerSentEvent<String>> encodeStreamEvent(UnifiedStreamEvent event, StreamContext ctx);

    /**
     * 生成流起始事件（如 Anthropic 的 message_start）
     */
    default Flux<ServerSentEvent<String>> initialStreamEvents(StreamContext ctx) {
        return Flux.empty();
    }

    /**
     * 将流处理链中的异常编码为协议特定的结构化错误事件
     * <p>所有实现类均通过 {@link AbstractSdkProtocolAdapter} 桥接到 SDK，无默认实现。</p>
     */
    Flux<ServerSentEvent<String>> encodeStreamError(Throwable throwable, StreamContext ctx);

    /**
     * 生成流终止事件（如 OpenAI 的 [DONE]）
     */
    Flux<ServerSentEvent<String>> terminalStreamEvents(StreamContext ctx);
}
