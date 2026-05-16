package com.code.aigateway.api.request;

import com.code.aigateway.core.stats.StatsRequestInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Rerank 请求（Cohere/Jina 标准格式）
 * <p>
 * 兼容 POST /v1/rerank 标准格式。
 * 纯同步请求，不支持流式。
 * </p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RerankRequest implements StatsRequestInfo {

    /** 模型名称（必填） */
    @NotBlank
    private String model;

    /** 搜索查询文本（必填） */
    @NotBlank
    private String query;

    /** 待排序文档列表（必填）：支持 String 或 { "text": "..." } 对象格式 */
    @NotEmpty
    private List<Object> documents;

    /** 返回最相关的 N 个结果（可选） */
    @JsonProperty("top_n")
    private Integer topN;

    /** 是否在响应中返回文档原文（可选） */
    @JsonProperty("return_documents")
    private Boolean returnDocuments;

    @Override
    public Boolean isStream() {
        return false;
    }
}
