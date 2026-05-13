package com.code.aigateway.provider.openai;

import java.util.HashMap;
import java.util.Map;

/**
 * OpenAI Responses API 流式解析状态跟踪器
 * <p>
 * 维护 tool call 的双索引（by itemId / by callId），
 * 处理 Responses API 流式事件中 tool call 的生命周期管理。
 * </p>
 */
class OpenAiResponsesStreamParser {

    private final Map<String, StreamToolCallState> toolCallsByItemId = new HashMap<>();
    private final Map<String, StreamToolCallState> toolCallsByCallId = new HashMap<>();
    private StreamToolCallState lastToolCall;

    void rememberToolCall(String itemId, String callId, Integer outputIndex, String toolName) {
        StreamToolCallState state = new StreamToolCallState(itemId, callId, outputIndex, toolName);
        if (itemId != null && !itemId.isBlank()) {
            toolCallsByItemId.put(itemId, state);
        }
        if (callId != null && !callId.isBlank()) {
            toolCallsByCallId.put(callId, state);
        }
        lastToolCall = state;
    }

    StreamToolCallState resolveToolCall(String itemId, String callId) {
        if (itemId != null && !itemId.isBlank() && toolCallsByItemId.containsKey(itemId)) {
            return toolCallsByItemId.get(itemId);
        }
        if (callId != null && !callId.isBlank()) {
            return toolCallsByCallId.get(callId);
        }
        return lastToolCall;
    }

    void forgetToolCall(String itemId, String callId) {
        StreamToolCallState state = resolveToolCall(itemId, callId);
        if (state == null) return;
        if (state.itemId != null && !state.itemId.isBlank()) {
            toolCallsByItemId.remove(state.itemId);
        }
        if (state.callId != null && !state.callId.isBlank()) {
            toolCallsByCallId.remove(state.callId);
        }
        if (state == lastToolCall) {
            lastToolCall = null;
        }
    }

    static class StreamToolCallState {
        final String itemId;
        final String callId;
        final Integer outputIndex;
        final String toolName;
        final StringBuilder arguments = new StringBuilder();

        StreamToolCallState(String itemId, String callId, Integer outputIndex, String toolName) {
            this.itemId = itemId;
            this.callId = callId;
            this.outputIndex = outputIndex;
            this.toolName = toolName;
        }
    }
}
