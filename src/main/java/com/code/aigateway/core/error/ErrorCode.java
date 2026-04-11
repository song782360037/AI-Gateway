package com.code.aigateway.core.error;

/**
 * 错误码枚举
 * <p>
 * 定义网关中所有可能的错误类型，用于错误分类和响应处理
 * </p>
 *
 * @author sst
 */
public enum ErrorCode {

    /**
     * 无效请求
     */
    INVALID_REQUEST,

    /**
     * 认证失败
     */
    AUTH_FAILED,

    /**
     * 模型未找到
     */
    MODEL_NOT_FOUND,

    /**
     * 提供商未找到
     */
    PROVIDER_NOT_FOUND,

    /**
     * 提供商已禁用
     */
    PROVIDER_DISABLED,

    /**
     * 能力不支持
     */
    CAPABILITY_NOT_SUPPORTED,

    /**
     * 提供商请求超时
     */
    PROVIDER_TIMEOUT,

    /**
     * 提供商速率限制
     */
    PROVIDER_RATE_LIMIT,

    /**
     * API Key 限流（网关层面）
     */
    RATE_LIMITED,

    /**
     * 提供商熔断器已打开
     */
    PROVIDER_CIRCUIT_OPEN,

    /**
     * 提供商认证/授权失败（401/403），不可重试，不触发 failover
     */
    PROVIDER_AUTH_ERROR,

    /**
     * 提供商请求格式错误（400/422），不可重试，不触发 failover
     */
    PROVIDER_BAD_REQUEST,

    /**
     * 提供商资源未找到（404），不可重试，不触发 failover
     */
    PROVIDER_RESOURCE_NOT_FOUND,

    /**
     * 提供商错误（其他 4xx）
     */
    PROVIDER_ERROR,

    /**
     * 提供商服务端错误（5xx），可重试
     */
    PROVIDER_SERVER_ERROR,

    /**
     * 流式解析错误
     */
    STREAM_PARSE_ERROR,

    /**
     * 配置不存在
     */
    CONFIG_NOT_FOUND,

    /**
     * 配置冲突
     */
    CONFIG_CONFLICT,

    /**
     * 配置被并发修改
     */
    CONFIG_CONCURRENT_MODIFIED,

    /**
     * 运行时配置刷新失败
     */
    CONFIG_REFRESH_FAILED,

    /**
     * 内部错误
     */
    INTERNAL_ERROR
}
