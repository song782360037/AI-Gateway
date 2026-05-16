package com.code.aigateway.sdk.protocol;

import com.code.aigateway.sdk.error.ErrorCode;
import com.code.aigateway.sdk.error.ProtocolException;
import com.code.aigateway.sdk.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Embeddings 协议适配器
 * <p>
 * 支持 OpenAI Embeddings API 的请求解析和响应编码。
 * 纯同步 JSON，不支持流式。
 * </p>
 */
public class OpenAiEmbeddingProtocolAdapter extends AbstractProtocolAdapter {

    public OpenAiEmbeddingProtocolAdapter(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public ProtocolType getProtocolType() {
        return ProtocolType.OPENAI_EMBEDDING;
    }

    @Override
    public boolean isSse() {
        return false;
    }

    // ===================== 请求解析 =====================

    @Override
    public UnifiedRequest parse(Object rawRequest) {
        Map<String, Object> map = toMap(rawRequest, "request");

        UnifiedRequest request = new UnifiedRequest();
        request.setRequestProtocol("openai-embedding");
        request.setResponseProtocol("openai-embedding");
        request.setModel(requireString(map, "model", "model is required"));
        request.setStream(false);

        // input 必填：支持 String / List<String> / List<int[]>
        Object input = map.get("input");
        if (input == null) {
            throw new ProtocolException(ErrorCode.INVALID_REQUEST, "input is required", "input");
        }
        if (!(input instanceof String) && !(input instanceof List<?>)) {
            throw new ProtocolException(ErrorCode.INVALID_REQUEST, "input must be a string or array", "input");
        }
        request.setEmbeddingInput(input);

        // 可选参数
        if (map.containsKey("dimensions")) {
            Object dim = map.get("dimensions");
            if (dim instanceof Number n) {
                request.setEmbeddingDimensions(n.intValue());
            } else {
                throw new ProtocolException(ErrorCode.INVALID_REQUEST, "dimensions must be an integer", "dimensions");
            }
        }
        if (map.containsKey("encoding_format")) {
            Object fmt = map.get("encoding_format");
            if (fmt instanceof String s) {
                request.setEmbeddingEncodingFormat(s);
            } else {
                throw new ProtocolException(ErrorCode.INVALID_REQUEST, "encoding_format must be a string", "encoding_format");
            }
        }
        // user 字段存入 metadata（不覆盖已有 metadata）
        if (map.get("user") != null) {
            if (request.getMetadata() == null) {
                request.setMetadata(new LinkedHashMap<>());
            }
            request.getMetadata().put("user", map.get("user"));
        }

        return request;
    }

    // ===================== 响应编码 =====================

    @Override
    public Object encodeResponse(UnifiedResponse response) {
        List<Map<String, Object>> dataList = new ArrayList<>();
        if (response.getEmbeddingData() != null) {
            for (UnifiedResponse.EmbeddingData item : response.getEmbeddingData()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("object", "embedding");
                entry.put("embedding", item.getEmbedding() != null ? item.getEmbedding().raw() : null);
                entry.put("index", item.getIndex());
                dataList.add(entry);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("object", "list");
        result.put("data", dataList);
        if (response.getId() != null) {
            result.put("id", response.getId());
        }
        result.put("model", response.getModel());
        if (response.getUsage() != null) {
            result.put("usage", encodeUsage(response.getUsage()));
        }
        return result;
    }

    // ===================== 流式编码（不支持） =====================

    @Override
    public List<EncodedEvent> encodeStreamEvent(UnifiedStreamEvent event, StreamEncodeContext ctx) {
        return List.of();
    }

    // ===================== 辅助方法 =====================

    private Map<String, Object> encodeUsage(UnifiedUsage usage) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("prompt_tokens", usage.getInputTokens());
        result.put("total_tokens", usage.getTotalTokens());
        return result;
    }
}
