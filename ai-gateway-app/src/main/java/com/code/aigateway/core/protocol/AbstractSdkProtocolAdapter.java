package com.code.aigateway.core.protocol;

import com.code.aigateway.sdk.protocol.EncodedEvent;
import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.model.StreamContext;
import com.code.aigateway.sdk.model.UnifiedRequest;
import com.code.aigateway.sdk.model.UnifiedResponse;
import com.code.aigateway.sdk.model.UnifiedStreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * SDK 协议适配器抽象基类
 * <p>
 * 封装主项目 ProtocolAdapter 与 SDK ProtocolAdapter 之间的通用桥接逻辑：
 * <ul>
 *   <li>类型转换：主项目 model <-> SDK model（通过 ObjectMapper.convertValue）</li>
 *   <li>ErrorCode 映射：主项目 ErrorCode -> SDK ErrorCode（静态 Map，避免 try-catch）</li>
 *   <li>SSE 桥接：SDK EncodedEvent 列表 -> Spring ServerSentEvent Flux</li>
 * </ul>
 * </p>
 */
public abstract class AbstractSdkProtocolAdapter implements ProtocolAdapter {

    private static final Logger log = LoggerFactory.getLogger(AbstractSdkProtocolAdapter.class);

    /**
     * 预计算的 ErrorCode 映射表
     * <p>
     * 主项目与 SDK 的 ErrorCode 枚举值保持同名一一对应，直接按名称映射。
     * 若出现 SDK 中不存在的枚举值，记录警告日志并发安全回退到 INTERNAL_ERROR。
     * </p>
     */
    private static final Map<ErrorCode, com.code.aigateway.sdk.error.ErrorCode> ERROR_CODE_MAP;

    static {
        ERROR_CODE_MAP = new EnumMap<>(ErrorCode.class);
        for (ErrorCode mainCode : ErrorCode.values()) {
            com.code.aigateway.sdk.error.ErrorCode mapped;
            try {
                mapped = com.code.aigateway.sdk.error.ErrorCode.valueOf(mainCode.name());
            } catch (IllegalArgumentException ignored) {
                log.warn("[SDK映射] 主项目 ErrorCode.{} 在 SDK 中无对应值，回退为 INTERNAL_ERROR", mainCode.name());
                mapped = com.code.aigateway.sdk.error.ErrorCode.INTERNAL_ERROR;
            }
            ERROR_CODE_MAP.put(mainCode, mapped);
        }
    }

    /** JSON 序列化器，用于类型转换和 SDK 适配器初始化 */
    protected final ObjectMapper objectMapper;

    protected AbstractSdkProtocolAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 获取底层 SDK 适配器实例 */
    protected abstract com.code.aigateway.sdk.protocol.ProtocolAdapter sdkAdapter();

    @Override
    public UnifiedRequest parse(Object rawRequest) {
        // App 与 SDK 统一使用 sdk.model.UnifiedRequest，无需 convertValue 桥接
        return sdkAdapter().parse(rawRequest);
    }

    @Override
    public Object encodeResponse(UnifiedResponse response) {
        // App 与 SDK 统一使用 sdk.model.UnifiedResponse，无需 convertValue 桥接
        return sdkAdapter().encodeResponse(response);
    }

    @Override
    public Flux<ServerSentEvent<String>> encodeStreamEvent(UnifiedStreamEvent event, StreamContext ctx) {
        // App 与 SDK 统一使用 sdk.model.UnifiedStreamEvent，无需 convertValue 桥接
        List<EncodedEvent> events = sdkAdapter().encodeStreamEvent(event, ctx.sdkContext());
        return toSseFlux(events);
    }

    @Override
    public Flux<ServerSentEvent<String>> encodeStreamError(Throwable throwable, StreamContext ctx) {
        List<EncodedEvent> events = sdkAdapter().encodeStreamError(throwable, ctx.sdkContext());
        return toSseFlux(events);
    }

    @Override
    public Flux<ServerSentEvent<String>> initialStreamEvents(StreamContext ctx) {
        List<EncodedEvent> events = sdkAdapter().initialStreamEvents(ctx.sdkContext());
        return toSseFlux(events);
    }

    @Override
    public Flux<ServerSentEvent<String>> terminalStreamEvents(StreamContext ctx) {
        List<EncodedEvent> events = sdkAdapter().terminalStreamEvents(ctx.sdkContext());
        return toSseFlux(events);
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
        return sdkAdapter().mapErrorType(mapToSdkErrorCode(errorCode));
    }

    /** 主项目 ErrorCode -> SDK ErrorCode 映射（静态 Map 查表，无异常开销） */
    protected com.code.aigateway.sdk.error.ErrorCode mapToSdkErrorCode(ErrorCode errorCode) {
        return ERROR_CODE_MAP.getOrDefault(errorCode, com.code.aigateway.sdk.error.ErrorCode.INTERNAL_ERROR);
    }

    /** 将 SDK 的 EncodedEvent 列表转为 Spring SSE Flux */
    protected Flux<ServerSentEvent<String>> toSseFlux(List<EncodedEvent> events) {
        if (events == null || events.isEmpty()) {
            return Flux.empty();
        }
        return Flux.fromIterable(events).map(e -> {
            if (e.eventName() != null) {
                return ServerSentEvent.<String>builder().event(e.eventName()).data(e.data()).build();
            }
            return ServerSentEvent.<String>builder(e.data()).build();
        });
    }
}
