package com.code.aigateway.sdk.protocol;

import com.code.aigateway.sdk.error.ErrorCode;
import com.code.aigateway.sdk.error.ProtocolException;
import com.code.aigateway.sdk.model.ProtocolType;
import com.code.aigateway.sdk.model.UnifiedRequest;
import com.code.aigateway.sdk.model.UnifiedResponse;
import com.code.aigateway.sdk.model.UnifiedStreamEvent;

import java.util.List;

/**
 * 协议适配器接口
 * <p>
 * SDK 的核心接口，将每种 AI API 格式的「请求解析 + 响应编码 + 流式编码 + 错误编码」
 * 封装为一个策略对象。使用者通过此接口实现协议互转。
 * </p>
 * <p>
 * 使用方式：
 * <pre>
 *   // 1. 解析请求
 *   UnifiedRequest unified = adapter.parse(rawRequest);
 *
 *   // 2. 编码响应
 *   Object response = adapter.encodeResponse(unifiedResponse);
 *
 *   // 3. 流式编码
 *   List&lt;EncodedEvent&gt; events = adapter.encodeStreamEvent(streamEvent, context);
 * </pre>
 * </p>
 */
public interface ProtocolAdapter {

    /** 获取协议类型 */
    ProtocolType getProtocolType();

    /**
     * 将原始请求解析为统一请求模型
     *
     * @param rawRequest 协议特定的请求对象（Map / POJO 均可）
     * @return 统一请求模型
     * @throws ProtocolException 解析失败时抛出
     */
    UnifiedRequest parse(Object rawRequest);

    /**
     * 将统一响应编码为协议特定的响应对象
     *
     * @param response 统一响应
     * @return 协议特定的响应对象（可被 Jackson 序列化）
     */
    Object encodeResponse(UnifiedResponse response);

    /**
     * 将统一流式事件编码为协议特定的事件列表
     * <p>
     * 一个 UnifiedStreamEvent 可能产生零个或多个编码事件
     * （如 Anthropic 首个 text_delta 需要先发送 content_block_start 再发送 content_block_delta）。
     * </p>
     *
     * @param event   统一流式事件
     * @param context 流式编码上下文
     * @return 编码事件列表；空列表表示跳过该事件
     */
    List<EncodedEvent> encodeStreamEvent(UnifiedStreamEvent event, StreamEncodeContext context);

    /**
     * 生成流起始事件（如 Anthropic 的 message_start）
     * <p>默认返回空列表。</p>
     *
     * @param context 流式编码上下文
     * @return 起始事件列表
     */
    default List<EncodedEvent> initialStreamEvents(StreamEncodeContext context) {
        return List.of();
    }

    /**
     * 生成流终止事件（如 OpenAI 的 [DONE]）
     * <p>默认返回空列表。</p>
     *
     * @param context 流式编码上下文
     * @return 终止事件列表
     */
    default List<EncodedEvent> terminalStreamEvents(StreamEncodeContext context) {
        return List.of();
    }

    /**
     * 将异常编码为协议特定的错误事件列表
     * <p>默认使用无名事件（SSE data: 格式）。OpenAI Responses 等使用命名事件的协议需覆盖此方法。</p>
     *
     * @param throwable 异常
     * @param context   流式编码上下文
     * @return 错误事件列表
     */
    default List<EncodedEvent> encodeStreamError(Throwable throwable, StreamEncodeContext context) {
        Object errorBody = ProtocolUtils.buildStreamErrorBody(throwable, this);
        return List.of(EncodedEvent.data(context.toJson(errorBody)));
    }

    /**
     * 是否使用 SSE 格式（Gemini 使用 NDJSON，返回 false）
     */
    boolean isSse();

    /**
     * 构建协议特定的错误响应体
     *
     * @param message   错误消息
     * @param errorType 错误类型
     * @param code      错误码
     * @param param     出错参数路径
     * @return 协议特定的错误响应对象
     */
    Object buildError(String message, String errorType, String code, String param);

    /**
     * 将 ErrorCode 映射为协议特定的错误类型字符串
     */
    String mapErrorType(ErrorCode errorCode);
}
