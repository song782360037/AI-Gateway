package com.code.aigateway.core.protocol;

import com.code.aigateway.api.response.GeminiErrorResponse;
import com.code.aigateway.api.response.GeminiGenerateContentResponse;
import com.code.aigateway.core.encoder.GeminiResponseEncoder;
import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.model.ResponseProtocol;
import com.code.aigateway.core.model.StreamContext;
import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.model.UnifiedResponse;
import com.code.aigateway.core.model.UnifiedStreamEvent;
import com.code.aigateway.core.model.UnifiedUsage;
import com.code.aigateway.core.parser.GeminiRequestParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini API 协议适配器
 * <p>
 * Gemini 使用 JSON 数组流（非 SSE），isSse() 返回 false。
 * 流式响应通过 streamGenerateContent 端点返回 NDJSON（换行分隔的 JSON）。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class GeminiProtocolAdapter implements ProtocolAdapter {

    private final GeminiRequestParser requestParser;
    private final GeminiResponseEncoder responseEncoder;
    private final ObjectMapper objectMapper;

    @Override
    public ResponseProtocol getProtocol() {
        return ResponseProtocol.GEMINI;
    }

    @Override
    public UnifiedRequest parse(Object rawRequest) {
        return requestParser.parse((com.code.aigateway.api.request.GeminiGenerateContentRequest) rawRequest);
    }

    @Override
    public Object encodeResponse(UnifiedResponse response) {
        return responseEncoder.encode(response);
    }

    /**
     * Gemini 流式使用 JSON 数组格式（非 SSE）。
     * 每个事件编码为一个完整的 Gemini 响应 JSON 对象。
     * Controller 会将 SSE wrapper 剥离，只取 data 部分。
     */
    @Override
    public Flux<ServerSentEvent<String>> encodeStreamEvent(UnifiedStreamEvent event, StreamContext ctx) {
        if ("done".equals(event.getType())) {
            return Flux.empty();
        }

        if ("text_delta".equals(event.getType())) {
            Map<String, Object> part = new LinkedHashMap<>();
            part.put("text", event.getTextDelta() != null ? event.getTextDelta() : "");

            List<Map<String, Object>> parts = List.of(part);
            Map<String, Object> content = Map.of("parts", parts, "role", "model");
            Map<String, Object> candidate = Map.of("content", content);
            Map<String, Object> chunk = new LinkedHashMap<>();
            chunk.put("candidates", List.of(candidate));

            return Flux.just(ServerSentEvent.builder(toJson(chunk)).build());
        }

        if ("tool_call".equals(event.getType())) {
            Map<String, Object> functionCall = new LinkedHashMap<>();
            functionCall.put("name", event.getToolName() != null ? event.getToolName() : "");
            Map<String, Object> fcPart = new LinkedHashMap<>();
            fcPart.put("functionCall", functionCall);

            List<Map<String, Object>> parts = List.of(fcPart);
            Map<String, Object> content = Map.of("parts", parts, "role", "model");
            Map<String, Object> candidate = Map.of("content", content);
            Map<String, Object> chunk = new LinkedHashMap<>();
            chunk.put("candidates", List.of(candidate));

            return Flux.just(ServerSentEvent.builder(toJson(chunk)).build());
        }

        if ("tool_call_delta".equals(event.getType())) {
            Map<String, Object> fcPart = new LinkedHashMap<>();
            Map<String, Object> functionCall = new LinkedHashMap<>();
            functionCall.put("name", event.getToolName() != null ? event.getToolName() : "");
            functionCall.put("args", event.getArgumentsDelta() != null ? event.getArgumentsDelta() : "{}");
            fcPart.put("functionCall", functionCall);

            List<Map<String, Object>> parts = List.of(fcPart);
            Map<String, Object> content = Map.of("parts", parts, "role", "model");
            Map<String, Object> candidate = Map.of("content", content);
            Map<String, Object> chunk = new LinkedHashMap<>();
            chunk.put("candidates", List.of(candidate));

            return Flux.just(ServerSentEvent.builder(toJson(chunk)).build());
        }

        return Flux.empty();
    }

    @Override
    public Flux<ServerSentEvent<String>> terminalStreamEvents(StreamContext ctx) {
        // Gemini 流式不发送显式终止标记
        return Flux.empty();
    }

    @Override
    public boolean isSse() {
        return false;
    }

    @Override
    public Object buildError(String message, String errorType, String code, String param) {
        Integer statusCode = mapHttpStatus(errorType);
        return new GeminiErrorResponse(
                new GeminiErrorResponse.ErrorDetail(statusCode, message, errorType)
        );
    }

    @Override
    public String mapErrorType(ErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_REQUEST, MODEL_NOT_FOUND, CAPABILITY_NOT_SUPPORTED -> "INVALID_ARGUMENT";
            case AUTH_FAILED -> "UNAUTHENTICATED";
            case PROVIDER_RATE_LIMIT -> "RESOURCE_EXHAUSTED";
            default -> "INTERNAL";
        };
    }

    private Integer mapHttpStatus(String errorType) {
        if (errorType == null) return 500;
        return switch (errorType) {
            case "INVALID_ARGUMENT" -> 400;
            case "UNAUTHENTICATED" -> 401;
            case "PERMISSION_DENIED" -> 403;
            case "RESOURCE_EXHAUSTED" -> 429;
            case "INTERNAL" -> 500;
            default -> 500;
        };
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize gemini chunk", e);
        }
    }
}
