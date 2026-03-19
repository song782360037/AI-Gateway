package com.code.aigateway.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;


@Data
@Builder
public class OpenAiChatCompletionChunkResponse {
    private String id;
    private String object;
    private Long created;
    private String model;
    private List<Choice> choices;

    @Data
    @Builder
    public static class Choice {
        private Integer index;
        private Delta delta;

        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    @Builder
    public static class Delta {
        private String role;
        private String content;
    }
}
