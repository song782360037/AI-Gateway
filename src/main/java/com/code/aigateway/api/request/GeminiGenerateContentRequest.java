package com.code.aigateway.api.request;

import com.code.aigateway.core.stats.StatsRequestInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Google Gemini API 请求
 * <p>
 * 兼容 Gemini generateContent API 的请求格式。
 * 注意：模型名称从 URL 路径中获取（/v1beta/models/{model}:generateContent），
 * 此 DTO 中的 model 字段用于统计和路由。
 * </p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiGenerateContentRequest implements StatsRequestInfo {

    /** 模型名称（从 URL 路径注入） */
    private String model;

    /** 内容列表（消息历史） */
    @Valid
    private List<Content> contents;

    /** 系统指令 */
    private SystemInstruction systemInstruction;

    /** 生成配置 */
    private GenerationConfig generationConfig;

    /** 工具定义列表 */
    @Valid
    private List<Tool> tools;

    /** 工具配置 */
    private ToolConfig toolConfig;

    /** 是否启用流式输出（由 endpoint 决定，此处用于标记） */
    private Boolean stream = false;

    @Override
    public Boolean isStream() {
        return stream;
    }

    // ===================== 内容格式 =====================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Content {
        private String role;
        private List<Part> parts;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Part {
        private String text;
        @JsonProperty("function_call")
        private FunctionCall functionCall;
        @JsonProperty("function_response")
        private FunctionResponse functionResponse;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FunctionCall {
        private String name;
        private Object args;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FunctionResponse {
        private String name;
        private Object response;
    }

    // ===================== 系统指令 =====================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SystemInstruction {
        private List<Part> parts;
    }

    // ===================== 生成配置 =====================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GenerationConfig {
        @JsonProperty("maxOutputTokens")
        private Integer maxOutputTokens;
        private Double temperature;
        @JsonProperty("topP")
        private Double topP;
        @JsonProperty("topK")
        private Integer topK;
        private List<String> stopSequences;
    }

    // ===================== 工具定义 =====================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Tool {
        @JsonProperty("function_declarations")
        private List<FunctionDeclaration> functionDeclarations;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FunctionDeclaration {
        private String name;
        private String description;
        private Map<String, Object> parameters;
    }

    // ===================== 工具配置 =====================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolConfig {
        @JsonProperty("function_calling_config")
        private FunctionCallingConfig functionCallingConfig;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FunctionCallingConfig {
        private String mode;
        @JsonProperty("allowed_function_names")
        private List<String> allowedFunctionNames;
    }
}
