package com.code.aigateway.admin.model.rsp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 请求日志查询响应
 */
@Data
public class RequestLogRsp {

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

    /** 是否流式请求 */
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

    /** 请求状态：SUCCESS / ERROR */
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
