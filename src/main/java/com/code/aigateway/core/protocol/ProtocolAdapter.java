package com.code.aigateway.core.protocol;

import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.model.ResponseProtocol;
import com.code.aigateway.core.model.StreamContext;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedResponse;
import com.code.aigateway.core.model.UnifiedStreamEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
     * 将流处理链中的异常编码为协议特定的结构化错误事件。
     * <p>
     * 该方法用于 SSE/流式响应已经开始写出后的异常兜底，避免仅中断连接而没有协议内错误反馈。
     * 默认将异常映射为协议标准错误体并序列化为 JSON，由具体协议决定是否追加终止标记。
     * 子类若需要自定义 SSE 事件命名或追加终止标记，应覆写此方法。
     * </p>
     *
     * @param throwable 流处理中抛出的异常
     * @param ctx       流式编码上下文
     * @return 协议特定的错误事件流
     */
    default Flux<ServerSentEvent<String>> encodeStreamError(Throwable throwable, StreamContext ctx) {
        ErrorCode errorCode = throwable instanceof GatewayException ge
                ? ge.getErrorCode()
                : ErrorCode.INTERNAL_ERROR;
        String message = throwable.getMessage() == null || throwable.getMessage().isBlank()
                ? "internal server error"
                : throwable.getMessage();
        Object errorBody = buildError(
                message,
                mapErrorType(errorCode),
                errorCode.name(),
                throwable instanceof GatewayException ge ? ge.getParam() : null
        );
        String json;
        try {
            json = new ObjectMapper().writeValueAsString(errorBody);
        } catch (JsonProcessingException e) {
            json = "{\"error\":{\"message\":\"internal server error\"}}";
        }
        return Flux.just(ServerSentEvent.<String>builder(json).build());
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
