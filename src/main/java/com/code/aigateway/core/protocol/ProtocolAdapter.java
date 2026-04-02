package com.code.aigateway.core.protocol;

import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.model.ResponseProtocol;
import com.code.aigateway.core.model.StreamContext;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedResponse;
import com.code.aigateway.core.model.UnifiedStreamEvent;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * 协议适配器接口
 * <p>
 * 将每种对外 API 格式的"请求解析 + 响应编码 + SSE 编码 + 错误编码"
 * 封装为一个策略对象。ChatGatewayService 仅依赖 UnifiedRequest / UnifiedResponse /
 * UnifiedStreamEvent，通过 ProtocolAdapter 委托格式相关的编解码。
 * </p>
 */
public interface ProtocolAdapter {

    /** 获取当前适配器对应的协议类型 */
    ResponseProtocol getProtocol();

    /**
     * 将原始请求解析为统一请求模型
     *
     * @param rawRequest 原始请求对象（具体类型由各适配器定义）
     * @return 统一请求模型
     */
    UnifiedRequest parse(Object rawRequest);

    /**
     * 将统一响应编码为协议特定的响应对象
     *
     * @param response 统一响应
     * @return 协议特定的响应对象
     */
    Object encodeResponse(UnifiedResponse response);

    /**
     * 将统一流式事件编码为 SSE 事件流
     * <p>
     * 一个 UnifiedStreamEvent 可能产生零个或多个 SSE 事件（如 Anthropic 首个 text_delta
     * 需要先发送 content_block_start 再发送 content_block_delta）。
     * </p>
     *
     * @param event 统一流式事件
     * @param ctx   流式编码上下文
     * @return SSE 事件流；返回空流表示跳过该事件
     */
    Flux<ServerSentEvent<String>> encodeStreamEvent(UnifiedStreamEvent event, StreamContext ctx);

    /**
     * 生成流起始事件（如 Anthropic 的 message_start）
     * <p>
     * 在主事件流之前发送，用于初始化消息结构。
     * 默认返回空流。
     * </p>
     *
     * @param ctx 流式编码上下文
     * @return 起始事件流
     */
    default Flux<ServerSentEvent<String>> initialStreamEvents(StreamContext ctx) {
        return Flux.empty();
    }

    /**
     * 生成流终止事件（如 OpenAI 的 [DONE]）
     *
     * @param ctx 流式编码上下文
     * @return 终止事件流，无需终止事件时返回 Flux.empty()
     */
    Flux<ServerSentEvent<String>> terminalStreamEvents(StreamContext ctx);

    /** 是否使用 SSE 格式（Gemini 使用 JSON 数组流，返回 false） */
    boolean isSse();

    /**
     * 构建协议特定的错误响应体
     *
     * @param message  错误消息
     * @param errorType 错误类型字符串
     * @param code     错误码
     * @param param    出错参数路径（可为 null）
     * @return 协议特定的错误响应对象
     */
    Object buildError(String message, String errorType, String code, String param);

    /**
     * 将网关 ErrorCode 映射为协议特定的错误类型字符串
     *
     * @param errorCode 网关错误码
     * @return 协议特定的错误类型（如 "invalid_request_error"、"authentication_error"）
     */
    String mapErrorType(ErrorCode errorCode);
}
