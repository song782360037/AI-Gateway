package com.code.aigateway.api.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Anthropic Messages API 非流式响应
 */
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnthropicMessagesResponse {

    private String id;
    private String type = "message";
    private String role = "assistant";
    private List<ContentBlock> content;
    private String model;
    @JsonProperty("stop_reason")
    private String stopReason;
    @JsonProperty("stop_sequence")
    private String stopSequence;
    private Usage usage;

    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContentBlock {
        private String type;
        private String text;
        private String thinking;
        private String signature;
        private String id;
        private String name;
        private Object input;
    }

    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {
        @JsonProperty("input_tokens")
        private Integer inputTokens;
        @JsonProperty("output_tokens")
        private Integer outputTokens;
    }
}
