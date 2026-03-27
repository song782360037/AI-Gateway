package com.code.aigateway.core.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 统一的请求模型
 * <p>
 * 作为不同 AI 提供商请求格式的中间表示，解耦 API 层与提供商层。
 * 包含聊天请求的所有核心要素。
 * </p>
 *
 * @author sst
 */
@Data
public class UnifiedRequest {

    /**
     * 请求协议类型（如 openai-chat）
     */
    private String requestProtocol;

    /**
     * 期望的响应协议类型
     */
    private String responseProtocol;

    /**
     * 目标提供商名称
     */
    private String provider;

    /**
     * 模型名称（实际模型名，非别名）
     */
    private String model;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * 消息列表
     */
    private List<UnifiedMessage> messages;

    /**
     * 工具定义列表
     */
    private List<UnifiedTool> tools;

    /**
     * 工具选择配置
     */
    private UnifiedToolChoice toolChoice;

    /**
     * 生成配置参数
     */
    private UnifiedGenerationConfig generationConfig;

    /**
     * 响应格式配置
     */
    private UnifiedResponseFormat responseFormat;

    /**
     * 是否启用流式输出
     */
    private Boolean stream;

    /**
     * 元数据（扩展字段）
     */
    private Map<String, Object> metadata;

    /**
     * Provider 运行时上下文
     * <p>
     * 用于在不修改 ProviderClient 接口签名的前提下，
     * 将路由后的 provider 运行参数传递给具体 provider 实现。
     * </p>
     */
    private ProviderExecutionContext executionContext;

    /**
     * Provider 运行时上下文
     */
    @Data
    public static class ProviderExecutionContext {

        /**
         * Provider 名称
         */
        private String providerName;

        /**
         * Provider 基础地址
         */
        private String providerBaseUrl;

        /**
         * Provider 版本号
         */
        private String providerVersion;

        /**
         * Provider 请求超时时间（秒）
         */
        private Integer providerTimeoutSeconds;

        /**
         * Provider 运行时 API Key。
         * <p>
         * 该字段由路由阶段写入，只在 provider 调用链路内部使用，
         * 避免 provider client 再回查静态 YAML 配置。
         * </p>
         */
        private String providerApiKey;
    }
}
