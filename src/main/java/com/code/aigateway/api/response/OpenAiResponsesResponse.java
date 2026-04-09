package com.code.aigateway.api.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * OpenAI Responses API 非流式响应
 */
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAiResponsesResponse {

    private String id;
    private String object = "response";
    @JsonProperty("created_at")
    private Long createdAt;
    private String model;
    private String status;
    private List<OutputItem> output;
    private Usage usage;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OutputItem {
        private String type;
        private String role;
        private List<ContentPart> content;
        /**
         * reasoning 输出的摘要内容
         */
        private List<ContentPart> summary;
        @JsonProperty("call_id")
        private String callId;
        private String name;
        private String arguments;
        private String status;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContentPart {
        private String type;
        private String text;
    }

    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {
        @JsonProperty("input_tokens")
        private Integer inputTokens;
        @JsonProperty("output_tokens")
        private Integer outputTokens;
        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }
}
