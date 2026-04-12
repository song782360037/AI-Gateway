package com.code.aigateway.admin.model.dataobject;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 请求日志数据对象
 */
@Data
public class RequestLogDO {

    /** 主键 */
    private Long id;

    /** 请求唯一标识 */
    private String requestId;

    /** 用户请求的模型别名 */
    private String aliasModel;

    /** 实际路由到的目标模型 */
    private String targetModel;

    /** 提供商编码 */
    private String providerCode;

    /** 提供商类型 */
    private String providerType;

    /** 响应协议 */
    private String responseProtocol;

    /** 请求路径 */
    private String requestPath;

    /** HTTP 方法 */
    private String httpMethod;

    /** API Key 前缀 */
    private String apiKeyPrefix;

    /** 候选路由数 */
    private Integer candidateCount;

    /** 候选尝试次数 */
    private Integer attemptCount;

    /** Failover 次数 */
    private Integer failoverCount;

    /** 重试次数 */
    private Integer retryCount;

    /** 熔断打开跳过次数 */
    private Integer circuitOpenSkippedCount;

    /** 是否命中限流 */
    private Boolean rateLimitTriggered;

    /** 上游 HTTP 状态码 */
    private Integer upstreamHttpStatus;

    /** 上游错误类型 */
    private String upstreamErrorType;

    /** 链路终止阶段 */
    private String terminalStage;

    /** 是否流式请求，映射 bit(1) */
    private Boolean isStream;

    /** 输入 Token 数 */
    private Integer promptTokens;

    /** 输入命中缓存的 Token 数 */
    private Integer cachedInputTokens;

    /** 输出 Token 数 */
    private Integer completionTokens;

    /** 总 Token 数 */
    private Integer totalTokens;

    /** 响应耗时（毫秒） */
    private Integer durationMs;

    /** 请求状态：SUCCESS / ERROR / CANCELLED / REJECTED */
    private String status;

    /** 错误码（失败时记录） */
    private String errorCode;

    /** 错误详情（失败时记录上游原始错误描述） */
    private String errorMessage;

    /** 来源 IP */
    private String sourceIp;

    /** 创建时间 */
    private LocalDateTime createTime;
}
