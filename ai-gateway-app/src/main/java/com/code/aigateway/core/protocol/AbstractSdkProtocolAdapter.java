package com.code.aigateway.core.protocol;

import com.code.aigateway.sdk.protocol.EncodedEvent;
import com.code.aigateway.sdk.protocol.StreamEncodeContext;
import com.code.aigateway.core.model.StreamContext;
import com.code.aigateway.sdk.error.ErrorCode;
import com.code.aigateway.sdk.model.UnifiedRequest;
import com.code.aigateway.sdk.model.UnifiedResponse;
import com.code.aigateway.sdk.model.UnifiedStreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * SDK 协议适配器抽象基类
 * <p>
 * 封装 App ProtocolAdapter 与 SDK ProtocolAdapter 之间的通用桥接逻辑：
 * <ul>
 *   <li>SSE 桥接：SDK EncodedEvent 列表 → Spring ServerSentEvent Flux</li>
 *   <li>统一委托：所有协议逻辑委托给 SDK 适配器实例</li>
 * </ul>
 * </p>
 */
public abstract class AbstractSdkProtocolAdapter implements ProtocolAdapter {

    protected final ObjectMapper objectMapper;

    protected AbstractSdkProtocolAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 获取底层 SDK 适配器实例 */
    protected abstract com.code.aigateway.sdk.protocol.ProtocolAdapter sdkAdapter();

    // ==================== SDK ProtocolAdapter 方法委托 ====================

    @Override
    public UnifiedRequest parse(Object rawRequest) {
        return sdkAdapter().parse(rawRequest);
    }

    @Override
    public Object encodeResponse(UnifiedResponse response) {
        return sdkAdapter().encodeResponse(response);
    }

    @Override
    public boolean isSse() {
        return sdkAdapter().isSse();
    }

    @Override
    public Object buildError(String message, String errorType, String code, String param) {
        return sdkAdapter().buildError(message, errorType, code, param);
    }

    @Override
    public String mapErrorType(ErrorCode errorCode) {
        return sdkAdapter().mapErrorType(errorCode);
    }

    // ==================== App 层 SSE 方法（桥接 SDK → Flux<SSE>） ====================

    @Override
    public Flux<ServerSentEvent<String>> encodeStreamEvent(UnifiedStreamEvent event, StreamContext ctx) {
        return toSseFlux(sdkAdapter().encodeStreamEvent(event, ctx.sdkContext()));
    }

    @Override
    public Flux<ServerSentEvent<String>> encodeStreamError(Throwable throwable, StreamContext ctx) {
        return toSseFlux(sdkAdapter().encodeStreamError(throwable, ctx.sdkContext()));
    }

    @Override
    public Flux<ServerSentEvent<String>> initialStreamEvents(StreamContext ctx) {
        return toSseFlux(sdkAdapter().initialStreamEvents(ctx.sdkContext()));
    }

    @Override
    public Flux<ServerSentEvent<String>> terminalStreamEvents(StreamContext ctx) {
        return toSseFlux(sdkAdapter().terminalStreamEvents(ctx.sdkContext()));
    }

    // ==================== SDK 层流式编码方法（仅满足接口约束，App 层应使用 Flux<SSE> 重载） ====================

    /**
     * SDK 层流式编码（仅满足接口约束）。
     * <p>App 层代码应使用 {@link #encodeStreamEvent(UnifiedStreamEvent, StreamContext)} 获取 SSE Flux。</p>
     */
    @Override
    public final List<EncodedEvent> encodeStreamEvent(UnifiedStreamEvent event, StreamEncodeContext context) {
        return sdkAdapter().encodeStreamEvent(event, context);
    }

    /**
     * SDK 层流起始事件（仅满足接口约束）。
     * <p>App 层代码应使用 {@link #initialStreamEvents(StreamContext)} 获取 SSE Flux。</p>
     */
    @Override
    public final List<EncodedEvent> initialStreamEvents(StreamEncodeContext context) {
        return sdkAdapter().initialStreamEvents(context);
    }

    /**
     * SDK 层流终止事件（仅满足接口约束）。
     * <p>App 层代码应使用 {@link #terminalStreamEvents(StreamContext)} 获取 SSE Flux。</p>
     */
    @Override
    public final List<EncodedEvent> terminalStreamEvents(StreamEncodeContext context) {
        return sdkAdapter().terminalStreamEvents(context);
    }

    /**
     * SDK 层流错误编码（仅满足接口约束）。
     * <p>App 层代码应使用 {@link #encodeStreamError(Throwable, StreamContext)} 获取 SSE Flux。</p>
     */
    @Override
    public final List<EncodedEvent> encodeStreamError(Throwable throwable, StreamEncodeContext context) {
        return sdkAdapter().encodeStreamError(throwable, context);
    }

    // ==================== 工具方法 ====================

    /**
     * 将 SDK EncodedEvent 列表转换为 Spring SSE Flux
     * <p>过滤掉 data 为 null 的事件，避免产生空 SSE data 行</p>
     */
    protected Flux<ServerSentEvent<String>> toSseFlux(List<EncodedEvent> events) {
        if (events == null || events.isEmpty()) {
            return Flux.empty();
        }
        return Flux.fromIterable(events)
                .filter(e -> e.data() != null)  // 过滤 data 为 null 的事件
                .map(e -> {
                    if (e.eventName() != null) {
                        return ServerSentEvent.<String>builder().event(e.eventName()).data(e.data()).build();
                    }
                    return ServerSentEvent.<String>builder(e.data()).build();
                });
    }
}
