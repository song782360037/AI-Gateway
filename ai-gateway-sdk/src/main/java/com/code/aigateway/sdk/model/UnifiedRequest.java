package com.code.aigateway.sdk.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 统一的请求模型
 * <p>
 * 协议无关的请求中间表示，各协议的请求经过 Parser 后统一为此结构。
 * </p>
 */
@Data
public class UnifiedRequest {

    /** 请求协议类型（如 openai-chat） */
    private String requestProtocol;

    /** 响应协议类型 */
    private String responseProtocol;

    /** 目标提供商名称 */
    private String provider;

    /** 模型名称 */
    private String model;

    /** 系统提示词 */
    private String systemPrompt;

    /** 消息列表 */
    private List<UnifiedMessage> messages;

    /** 工具定义列表 */
    private List<UnifiedTool> tools;

    /** 工具选择配置 */
    private UnifiedToolChoice toolChoice;

    /** 生成配置 */
    private UnifiedGenerationConfig generationConfig;

    /** 响应格式配置 */
    private UnifiedResponseFormat responseFormat;

    /** 是否流式输出 */
    private Boolean stream;

    /** 元数据（扩展字段，如 statsContext 等 App 层特有对象） */
    private Map<String, Object> metadata;

    /** Embedding 输入（String / List<String> / List<int[]>），非 Embedding 请求为 null */
    private Object embeddingInput;

    /** Embedding 输出向量维度，null 表示使用模型默认值 */
    private Integer embeddingDimensions;

    /** Embedding 编码格式（"float" | "base64"），默认 "float" */
    private String embeddingEncodingFormat;

    /** Rerank 查询文本，非 Rerank 请求为 null */
    private String rerankQuery;

    /** Rerank 待排序文档列表，非 Rerank 请求为 null */
    private List<String> rerankDocuments;

    /** Rerank 返回最相关的 N 个结果，null 表示返回全部 */
    private Integer rerankTopN;

    /** Rerank 是否在响应中返回文档原文 */
    private Boolean rerankReturnDocuments;

    /** Provider 运行时上下文 */
    private ProviderExecutionContext executionContext;

    /**
     * Provider 运行时上下文
     */
    @Data
    public static class ProviderExecutionContext {

        /** Provider 名称 */
        private String providerName;

        /** Provider 基础地址 */
        private String providerBaseUrl;

        /** Provider 请求超时时间（秒） */
        private Integer providerTimeoutSeconds;

        /** Provider 运行时 API Key */
        private String providerApiKey;

        /** 请求链路追踪 ID */
        private String correlationId;

        /** 自定义请求头（全局+提供商级别已合并） */
        private Map<String, String> customHeaders;
    }
}
