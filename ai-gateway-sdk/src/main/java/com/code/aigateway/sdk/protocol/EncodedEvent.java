package com.code.aigateway.sdk.protocol;

/**
 * 编码后的事件
 * <p>
 * SDK 不关心传输层（SSE/NDJSON），只负责协议数据结构的编码。
 * 每个事件包含可选的 event name（SSE 命名事件使用）和 JSON data。
 * </p>
 *
 * @param eventName 事件名称（可选），SSE 命名事件时使用，如 "message_start"
 * @param data      JSON 格式的事件数据
 */
public record EncodedEvent(String eventName, String data) {

    /**
     * 创建无名事件（普通 SSE data-only 事件）
     */
    public static EncodedEvent data(String data) {
        return new EncodedEvent(null, data);
    }

    /**
     * 创建命名事件（SSE named event）
     */
    public static EncodedEvent named(String eventName, String data) {
        return new EncodedEvent(eventName, data);
    }
}
