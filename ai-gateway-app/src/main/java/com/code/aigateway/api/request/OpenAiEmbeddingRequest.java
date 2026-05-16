package com.code.aigateway.api.request;

import com.code.aigateway.core.stats.StatsRequestInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * OpenAI Embeddings 请求
 * <p>
 * 与 OpenAI Embeddings API 的请求格式完全兼容。
 * 纯同步请求，不支持流式。
 * </p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAiEmbeddingRequest implements StatsRequestInfo {

    /** 模型名称（必填） */
    @NotBlank
    private String model;

    /** 输入文本（必填）：支持 String / List<String> / List<int[]> */
    @NotNull
    private Object input;

    /** 输出向量维度（可选，仅 text-embedding-3 及更新模型支持） */
    private Integer dimensions;

    /** 编码格式（可选）：float（默认）或 base64 */
    @JsonProperty("encoding_format")
    private String encodingFormat;

    /** 终端用户标识（可选） */
    private String user;

    @Override
    public Boolean isStream() {
        return false;
    }
}
