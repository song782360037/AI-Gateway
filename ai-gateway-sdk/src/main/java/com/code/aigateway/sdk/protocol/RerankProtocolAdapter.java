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
 * Rerank 协议适配器（Cohere/Jina 标准格式）
 * <p>
 * 支持 POST /v1/rerank 标准格式的请求解析和响应编码。
 * 纯同步 JSON，不支持流式。
 * </p>
 */
public class RerankProtocolAdapter extends AbstractProtocolAdapter {

    public RerankProtocolAdapter(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public ProtocolType getProtocolType() {
        return ProtocolType.RERANK;
    }

    @Override
    public boolean isSse() {
        return false;
    }

    // ===================== 请求解析 =====================

    @Override
    @SuppressWarnings("unchecked")
    public UnifiedRequest parse(Object rawRequest) {
        Map<String, Object> map = toMap(rawRequest, "request");

        UnifiedRequest request = new UnifiedRequest();
        request.setRequestProtocol("rerank");
        request.setResponseProtocol("rerank");
        request.setModel(requireString(map, "model", "model is required"));
        request.setStream(false);

        // query 必填
        Object queryObj = map.get("query");
        if (!(queryObj instanceof String query) || query.isBlank()) {
            throw new ProtocolException(ErrorCode.INVALID_REQUEST, "query is required", "query");
        }
        request.setRerankQuery(query);

        // documents 必填
        Object documentsObj = map.get("documents");
        if (documentsObj == null) {
            throw new ProtocolException(ErrorCode.INVALID_REQUEST, "documents is required", "documents");
        }
        // documents 可以是 String[] 或 Object[]（带 text 字段的对象）
        List<String> documents = new ArrayList<>();
        if (documentsObj instanceof List<?> docList) {
            for (int i = 0; i < docList.size(); i++) {
                Object doc = docList.get(i);
                if (doc == null) {
                    throw new ProtocolException(ErrorCode.INVALID_REQUEST,
                            "documents[" + i + "] must not be null", "documents");
                }
                if (doc instanceof String s) {
                    documents.add(s);
                } else if (doc instanceof Map<?, ?> docMap) {
                    // 支持对象格式 { "text": "..." }
                    Object text = docMap.get("text");
                    if (text != null) {
                        documents.add(text.toString());
                    } else {
                        throw new ProtocolException(ErrorCode.INVALID_REQUEST,
                                "documents[" + i + "] object must have a 'text' field", "documents");
                    }
                } else {
                    throw new ProtocolException(ErrorCode.INVALID_REQUEST,
                            "documents[" + i + "] must be a string or {\"text\": \"...\"} object", "documents");
                }
            }
        }
        if (documents.isEmpty()) {
            throw new ProtocolException(ErrorCode.INVALID_REQUEST, "documents must not be empty", "documents");
        }
        request.setRerankDocuments(documents);

        // 可选参数
        if (map.containsKey("top_n")) {
            Object topN = map.get("top_n");
            if (topN instanceof Number n) {
                request.setRerankTopN(n.intValue());
            } else {
                throw new ProtocolException(ErrorCode.INVALID_REQUEST, "top_n must be an integer", "top_n");
            }
        }
        if (map.containsKey("return_documents")) {
            Object val = map.get("return_documents");
            if (val instanceof Boolean b) {
                request.setRerankReturnDocuments(b);
            } else {
                throw new ProtocolException(ErrorCode.INVALID_REQUEST, "return_documents must be a boolean", "return_documents");
            }
        }

        return request;
    }

    // ===================== 响应编码 =====================

    @Override
    public Object encodeResponse(UnifiedResponse response) {
        List<Map<String, Object>> resultsList = new ArrayList<>();
        if (response.getRerankResults() != null) {
            for (UnifiedResponse.RerankResult item : response.getRerankResults()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("index", item.getIndex());
                entry.put("relevance_score", item.getRelevanceScore());
                // 仅当 return_documents=true 时返回文档原文
                if (item.getDocument() != null) {
                    entry.put("document", Map.of("text", item.getDocument()));
                }
                resultsList.add(entry);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        if (response.getId() != null) {
            result.put("id", response.getId());
        }
        result.put("model", response.getModel());
        result.put("results", resultsList);
        if (response.getUsage() != null) {
            Map<String, Object> usage = new LinkedHashMap<>();
            usage.put("total_tokens", response.getUsage().getTotalTokens());
            usage.put("prompt_tokens", response.getUsage().getInputTokens());
            result.put("usage", usage);
        }
        return result;
    }

    // ===================== 流式编码（不支持） =====================

    @Override
    public List<EncodedEvent> encodeStreamEvent(UnifiedStreamEvent event, StreamEncodeContext ctx) {
        return List.of();
    }
}
