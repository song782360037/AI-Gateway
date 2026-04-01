package com.code.aigateway.api.request;

import com.code.aigateway.core.stats.StatsRequestInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * OpenAI Responses API 请求
 * <p>
 * 兼容 OpenAI Responses API 的请求格式（POST /v1/responses）。
 * 与 Chat Completions 的主要区别：
 * <ul>
 *   <li>系统提示词使用顶层 instructions 字段</li>
 *   <li>对话历史使用 input 数组（支持多种 item 类型）</li>
 *   <li>工具调用使用 call_id（非 tool_call_id）</li>
 * </ul>
 * </p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAiResponsesRequest implements StatsRequestInfo {

    /** 模型名称（必填） */
    @NotBlank
    private String model;

    /** 系统指令 */
    private String instructions;

    /** 输入数组（消息、函数调用、函数调用输出等） */
    @Valid
    private List<InputItem> input;

    /** 温度参数 */
    private Double temperature;

    /** 最大输出 token 数 */
    @JsonProperty("max_output_tokens")
    private Integer maxOutputTokens;

    /** 是否启用流式输出 */
    private Boolean stream = false;

    /** 工具定义列表 */
    @Valid
    private List<ToolDef> tools;

    /** 工具选择策略 */
    @JsonProperty("tool_choice")
    private Object toolChoice;

    /** 是否允许并行工具调用 */
    @JsonProperty("parallel_tool_calls")
    private Boolean parallelToolCalls;

    /** 扩展元数据 */
    private Map<String, Object> metadata;

    /** StatsRequestInfo 实现 */
    @Override
    public Boolean isStream() {
        return stream;
    }

    // ===================== 输入项类型 =====================

    /**
     * Responses API 输入项（联合类型）
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InputItem {

        /** 项类型：message、function_call、function_call_output */
        private String type;

        /** 角色（type=message 时使用） */
        private String role;

        /** 内容（type=message 时使用，字符串或数组） */
        private Object content;

        /** 函数调用 ID（function_call 和 function_call_output 共用） */
        @JsonProperty("call_id")
        private String callId;

        /** 函数名称（function_call 时使用） */
        private String name;

        /** 函数参数 JSON（function_call 时使用） */
        private String arguments;

        /** 输出内容（function_call_output 时使用） */
        private String output;
    }

    // ===================== 工具定义 =====================

    /**
     * Responses API 工具定义
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolDef {

        /** 工具类型：function */
        private String type;

        /** 函数定义 */
        @Valid
        private FunctionDef function;
    }

    /**
     * 函数定义
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FunctionDef {

        /** 函数名称 */
        private String name;

        /** 函数描述 */
        private String description;

        /** 参数 JSON Schema */
        private Map<String, Object> parameters;

        /** 是否启用严格模式 */
        private Boolean strict;
    }
}
