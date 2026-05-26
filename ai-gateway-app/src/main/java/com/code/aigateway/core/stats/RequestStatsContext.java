package com.code.aigateway.core.stats;

import com.code.aigateway.sdk.model.ProtocolType;
import com.code.aigateway.core.router.RouteResult;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 当前请求的统计上下文
 * <p>
 * 通过 WebFilter 写入 ServerWebExchange attributes，供 Controller、Service、异常处理器共享。
 * </p>
 */
@Getter
@Setter
public class RequestStatsContext {

    /** exchange attribute key */
    public static final String ATTRIBUTE_KEY = RequestStatsContext.class.getName();

    /** 请求开始时间（毫秒） */
    private long startTimeMs;

    /** 来源 IP */
    private String sourceIp;

    /** 请求链路追踪 ID */
    private String correlationId;

    /** 请求路径 */
    private String requestPath;

    /** HTTP 方法 */
    private String httpMethod;

    /** 统计请求信息（协议无关接口） */
    private StatsRequestInfo requestInfo;

    /** 响应协议类型（由 controller 设置） */
    private ProtocolType responseProtocol;

    /** 路由结果，成功路由后填充 */
    private RouteResult routeResult;

    /** API Key 配置 ID */
    private Long apiKeyConfigId;

    /** API Key 前缀（脱敏） */
    private String apiKeyPrefix;

    /** 本次请求使用的提供商 API Key（脱敏，前8后4） */
    private String providerApiKeyMasked;

    /** 本次请求使用的 provider_api_key 记录 ID，用于日志查询时关联备注 */
    private Long providerKeyId;

    /** 鉴权状态：DISABLED / PASSED / FAILED */
    private String authStatus;

    /** 是否命中限流 */
    private Boolean rateLimitTriggered;

    /** 限流原因 */
    private String rateLimitReason;

    /** 候选路由数 */
    private Integer candidateCount;

    /** 候选尝试次数（线程安全累加） */
    @Getter(AccessLevel.NONE)
    private final AtomicInteger attemptCount = new AtomicInteger(0);

    /** Failover 次数（线程安全累加） */
    @Getter(AccessLevel.NONE)
    private final AtomicInteger failoverCount = new AtomicInteger(0);

    /** 熔断打开跳过次数（线程安全累加） */
    @Getter(AccessLevel.NONE)
    private final AtomicInteger circuitOpenSkippedCount = new AtomicInteger(0);

    /** 重试次数（线程安全累加） */
    @Getter(AccessLevel.NONE)
    private final AtomicInteger retryCount = new AtomicInteger(0);

    /** 最终提供商编码 */
    private String finalProviderCode;

    /** 最终提供商类型 */
    private String finalProviderType;

    /** 最终目标模型 */
    private String finalTargetModel;

    /** 上游 HTTP 状态码 */
    private Integer upstreamHttpStatus;

    /** 上游错误类型 */
    private String upstreamErrorType;

    /** 链路终止阶段：AUTH / RATE_LIMIT / ROUTING / FAILOVER / UPSTREAM / STREAMING / UNKNOWN */
    private String terminalStage;

    /** 是否开启思考 */
    private Boolean thinkingEnabled;

    /** 思考深度（budgetTokens或effort） */
    private String thinkingDepth;

    /** 是否映射思考（ReasoningSemanticMapper） */
    private Boolean thinkingMapped;

    /** 首token响应时间（毫秒） */
    private Long firstTokenLatencyMs;

    /** 详细链路追踪信息（JSON格式） */
    private String traceDetailsJson;

    /** 链路追踪详情对象（运行时临时使用，不持久化） */
    private transient TraceDetails traceDetails;

    /** 是否已经完成过一次统计采集，使用 AtomicBoolean 保证线程安全 */
    private final AtomicBoolean collected = new AtomicBoolean(false);

    /**
     * 原子地标记当前请求已经完成统计采集。
     *
     * @return true 表示本次成功占位，false 表示之前已采集过
     */
    public boolean tryMarkCollected() {
        return collected.compareAndSet(false, true);
    }

    /**
     * 计算当前请求已消耗的时间（毫秒）
     */
    public long elapsedMs() {
        return Math.max(0L, System.currentTimeMillis() - startTimeMs);
    }

    /**
     * 累加候选尝试次数。
     */
    public void incrementAttemptCount() {
        attemptCount.incrementAndGet();
    }

    /**
     * 累加 Failover 次数。
     */
    public void incrementFailoverCount() {
        failoverCount.incrementAndGet();
    }

    /**
     * 累加熔断跳过次数。
     */
    public void incrementCircuitOpenSkippedCount() {
        circuitOpenSkippedCount.incrementAndGet();
    }

    /**
     * 累加重试次数。
     */
    public void incrementRetryCount() {
        retryCount.incrementAndGet();
    }

    /**
     * 获取候选尝试次数（兼容 Lombok getter）。
     */
    public Integer getAttemptCount() {
        return attemptCount.get();
    }

    /**
     * 获取 Failover 次数。
     */
    public Integer getFailoverCount() {
        return failoverCount.get();
    }

    /**
     * 获取熔断跳过次数。
     */
    public Integer getCircuitOpenSkippedCount() {
        return circuitOpenSkippedCount.get();
    }

    /**
     * 获取重试次数。
     */
    public Integer getRetryCount() {
        return retryCount.get();
    }
}
