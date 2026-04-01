package com.code.aigateway.api.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Google Gemini API 非流式响应
 */
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiGenerateContentResponse {

    private List<Candidate> candidates;
    @JsonProperty("usageMetadata")
    private UsageMetadata usageMetadata;
    @JsonProperty("modelVersion")
    private String modelVersion;

    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Candidate {
        private Content content;
        @JsonProperty("finishReason")
        private String finishReason;
        @JsonProperty("safetyRatings")
        private List<Map<String, Object>> safetyRatings;

        @Data
        @Builder
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Content {
            private List<Map<String, Object>> parts;
            private String role;
        }
    }

    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UsageMetadata {
        @JsonProperty("promptTokenCount")
        private Integer promptTokenCount;
        @JsonProperty("candidatesTokenCount")
        private Integer candidatesTokenCount;
        @JsonProperty("totalTokenCount")
        private Integer totalTokenCount;
    }
}
