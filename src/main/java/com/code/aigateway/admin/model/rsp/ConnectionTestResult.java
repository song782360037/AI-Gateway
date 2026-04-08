package com.code.aigateway.admin.model.rsp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提供商连接测试结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionTestResult {

    /**
     * 连接是否成功
     */
    private boolean success;

    /**
     * 响应延迟（毫秒）
     */
    private long latencyMs;

    /**
     * 失败时的错误信息
     */
    private String errorMessage;

    /**
     * 错误类型分类
     * <p>
     * AUTH_FAILED  — 认证失败（401/403）
     * RATE_LIMIT   — 请求频率超限（429）
     * TIMEOUT      — 连接超时
     * NETWORK_ERROR — 网络不可达 / DNS 解析失败 / SSL 握手失败
     * SERVER_ERROR  — 上游服务端错误（5xx）
     * UNKNOWN       — 未知异常
     * </p>
     */
    private String errorType;
}
