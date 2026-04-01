package com.code.aigateway.core.protocol;

import com.code.aigateway.core.model.ResponseProtocol;
import org.springframework.web.server.ServerWebExchange;

/**
 * 协议解析工具类
 * <p>
 * 根据 URL 路径或 ServerWebExchange attributes 解析当前请求对应的协议类型。
 * 用于 GlobalExceptionHandler 和 ApiKeyAuthWebFilter 中选择错误响应格式。
 * </p>
 */
public final class ProtocolResolver {

    /** exchange attribute key：存储协议类型 */
    public static final String PROTOCOL_ATTRIBUTE_KEY = ProtocolResolver.class.getName() + ".PROTOCOL";

    private ProtocolResolver() {
    }

    /**
     * 从 exchange attributes 中获取协议类型。
     * 优先读取 controller 写入的属性，未设置时按路径前缀推断，默认 OPENAI_CHAT。
     */
    public static ResponseProtocol fromExchange(ServerWebExchange exchange) {
        ResponseProtocol protocol = exchange.getAttribute(PROTOCOL_ATTRIBUTE_KEY);
        if (protocol != null) {
            return protocol;
        }
        return fromPath(exchange.getRequest().getPath().value());
    }

    /**
     * 按 URL 路径前缀推断协议类型
     */
    public static ResponseProtocol fromPath(String path) {
        if (path.startsWith("/v1/responses")) {
            return ResponseProtocol.OPENAI_RESPONSES;
        }
        if (path.startsWith("/v1/messages")) {
            return ResponseProtocol.ANTHROPIC;
        }
        if (path.startsWith("/v1beta/")) {
            return ResponseProtocol.GEMINI;
        }
        return ResponseProtocol.OPENAI_CHAT;
    }
}
