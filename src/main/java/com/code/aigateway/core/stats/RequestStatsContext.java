package com.code.aigateway.core.stats;

import com.code.aigateway.core.model.ResponseProtocol;
import com.code.aigateway.core.router.RouteResult;
import lombok.Data;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 当前请求的统计上下文
 * <p>
 * 通过 WebFilter 写入 ServerWebExchange attributes，供 Controller、Service、异常处理器共享。
 * </p>
 */
@Data
public class RequestStatsContext {

    /** exchange attribute key */
    public static final String ATTRIBUTE_KEY = RequestStatsContext.class.getName();

    /** 请求开始时间（毫秒） */
    private long startTimeMs;

    /** 来源 IP */
    private String sourceIp;

    /** 统计请求信息（协议无关接口） */
    private StatsRequestInfo requestInfo;

    /** 响应协议类型（由 controller 设置） */
    private ResponseProtocol responseProtocol;

    /** 路由结果，成功路由后填充 */
    private RouteResult routeResult;

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
}
