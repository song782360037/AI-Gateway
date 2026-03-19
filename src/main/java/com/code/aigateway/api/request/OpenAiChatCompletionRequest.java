package com.code.aigateway.api.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * OpenAI ChatCompletion Request
 */

@Data
public class OpenAiChatCompletionRequest {

    @NotBlank
    private String model;

    @NotEmpty
    @Valid
    private List<OpenAiMessage> messages;

    private Double temperature;

    @JsonProperty("top_p")
    private Double topP;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    private List<String> stop;

    private Boolean stream = false;

    private List<OpenAiTool> tools;

    @JsonProperty("tool_choice")
    private Object toolChoice;

    @Data
    public static class OpenAiMessage {
        @NotBlank
        private String role;

        /**
         * 允许是 string 或 array
         */
        private Object content;

        @JsonProperty("tool_call_id")
        private String toolCallId;
    }

    @Data
    public static class OpenAiTool {
        private String type;
        private FunctionDef function;

        @Data
        public static class FunctionDef {
            private String name;
            private String description;
            private Map<String, Object> parameters;
        }
    }
}
