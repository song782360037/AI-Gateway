package com.code.aigateway.core.stats;

import com.code.aigateway.admin.mapper.RequestLogMapper;
import com.code.aigateway.admin.mapper.RequestStatHourlyMapper;
import com.code.aigateway.admin.model.dataobject.RequestLogDO;
import com.code.aigateway.admin.model.dataobject.RequestStatHourlyDO;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.model.UnifiedUsage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.util.concurrent.Queues;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 请求统计采集器
 * <p>
 * 核心职责：
 * <ul>
 *   <li>接收各入口（chat / streamChat / error）的统计事件</li>
 *   <li>异步批量写入 request_log</li>
 *   <li>实时 upsert request_stat_hourly 聚合表</li>
 * </ul>
 * 使用 Reactor Sinks 实现背压缓冲，不阻塞主请求链路。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestStatsCollector {

    private final RequestLogMapper requestLogMapper;
    private final RequestStatHourlyMapper statHourlyMapper;

    /** 异步缓冲队列：初始容量 8192，可动态扩容 */
    private final Sinks.Many<RequestLogDO> sink = Sinks.many().unicast().onBackpressureBuffer(Queues.<RequestLogDO>unbounded(8192).get());
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * 初始化异步消费：按批次（最多 100 条 / 最多等 5 秒）刷盘。
     * 使用 AtomicBoolean 防止重复订阅。
     */
    @PostConstruct
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void init() {
        if (!initialized.compareAndSet(false, true)) {
            log.warn("[请求统计] init() 重复调用，已忽略");
            return;
        }
        sink.asFlux()
                .bufferTimeout(100, Duration.ofSeconds(5))
                .publishOn(Schedulers.boundedElastic())
                .subscribe(this::flushBatch, ex -> log.error("[请求统计] 异步消费异常", ex));
    }

    /**
     * 记录非流式请求成功
     *
     * @param context 当前请求统计上下文
     * @param usage   统一使用统计（从 UnifiedResponse 中获取）
     */
    public void collectSuccess(RequestStatsContext context, UnifiedUsage usage) {
        if (context == null || context.getRequestInfo() == null || !context.tryMarkCollected()) {
            return;
        }
        RequestLogDO logDO = buildLog(context, "SUCCESS", null, null);
        applyUsage(logDO, usage);
        emit(logDO);
    }

    /**
     * 记录流式请求成功
     */
    public void collectStreamSuccess(RequestStatsContext context, UnifiedUsage usage) {
        if (context == null || context.getRequestInfo() == null || !context.tryMarkCollected()) {
            return;
        }
        RequestLogDO logDO = buildLog(context, "SUCCESS", null, null);
        applyUsage(logDO, usage);
        emit(logDO);
    }

    /**
     * 记录流式请求被客户端取消（主动断开连接、代理层超时等）
     * <p>
     * 取消时可能已有部分 usage 数据（partial usage），仍记录但不计入成功率。
     * </p>
     */
    public void collectStreamCancelled(RequestStatsContext context, UnifiedUsage usage) {
        if (context == null || context.getRequestInfo() == null || !context.tryMarkCollected()) {
            return;
        }
        if (context.getTerminalStage() == null) {
            context.setTerminalStage("STREAMING");
        }
        RequestLogDO logDO = buildLog(context, "CANCELLED", "STREAM_CANCELLED", "Client disconnected");
        applyUsage(logDO, usage);
        log.info("[流式取消] requestId={}, model={}, provider={}, duration={}ms",
                logDO.getRequestId(),
                context.getRequestInfo().getModel(),
                context.getRouteResult() != null ? context.getRouteResult().getProviderName() : "N/A",
                context.elapsedMs());
        emit(logDO);
    }

    /**
     * 记录请求失败
     */
    public void collectError(RequestStatsContext context, Throwable ex) {
        if (context == null || context.getRequestInfo() == null || !context.tryMarkCollected()) {
            return;
        }
        String errorCode = resolveErrorCode(ex);
        String errorMessage = resolveErrorMessage(ex);
        applyErrorContext(context, ex);
        RequestLogDO logDO = buildLog(context, "ERROR", errorCode, errorMessage);
        log.warn("[请求失败] requestId={}, model={}, provider={}, errorCode={}, error={}",
                logDO.getRequestId(),
                context.getRequestInfo().getModel(),
                context.getRouteResult() != null ? context.getRouteResult().getProviderName() : "N/A",
                errorCode, errorMessage);
        emit(logDO);
    }

    /**
     * 记录前置拒绝请求（如认证失败、限流拒绝）。
     */
    public void collectRejected(RequestStatsContext context, String errorCode, String errorMessage) {
        if (context == null || !context.tryMarkCollected()) {
            return;
        }
        RequestLogDO logDO = buildLog(context, "REJECTED", errorCode, errorMessage);
        emit(logDO);
    }

    // ===================== 私有方法 =====================

    private void emit(RequestLogDO logDO) {
        Sinks.EmitResult result = sink.tryEmitNext(logDO);
        if (result.isFailure()) {
            log.warn("[请求统计] 缓冲队列已满，丢弃请求日志: requestId={}", logDO.getRequestId());
        }
    }

    /**
     * 批量刷盘：逐条写入 request_log + upsert request_stat_hourly，单条失败不影响其他记录
     */
    private void flushBatch(List<RequestLogDO> batch) {
        int failCount = 0;
        for (RequestLogDO record : batch) {
            try {
                requestLogMapper.insert(record);
                upsertHourlyStat(record);
            } catch (Exception e) {
                failCount++;
                log.warn("[请求统计] 单条写入失败，requestId={}", record.getRequestId(), e);
            }
        }
        if (failCount > 0) {
            log.warn("[请求统计] 批量写入完成，失败 {}/{} 条", failCount, batch.size());
        }
    }

    /**
     * 根据 request_log 记录更新小时统计表
     */
    private void upsertHourlyStat(RequestLogDO record) {
        try {
            LocalDateTime statTime = record.getCreateTime().truncatedTo(ChronoUnit.HOURS);

            RequestStatHourlyDO stat = new RequestStatHourlyDO();
            stat.setStatTime(statTime);
            stat.setAliasModel(record.getAliasModel());
            stat.setProviderCode(record.getProviderCode() != null ? record.getProviderCode() : "unknown");
            stat.setRequestCount(1);
            stat.setSuccessCount("SUCCESS".equals(record.getStatus()) ? 1 : 0);
            stat.setErrorCount(("ERROR".equals(record.getStatus()) || "REJECTED".equals(record.getStatus())) ? 1 : 0);
            stat.setCancelCount("CANCELLED".equals(record.getStatus()) ? 1 : 0);
            stat.setPromptTokens((long) nullToZero(record.getPromptTokens()));
            stat.setCachedInputTokens((long) nullToZero(record.getCachedInputTokens()));
            stat.setCompletionTokens((long) nullToZero(record.getCompletionTokens()));
            stat.setTotalTokens((long) nullToZero(record.getTotalTokens()));
            stat.setTotalDurationMs((long) nullToZero(record.getDurationMs()));

            double cost = ModelPriceTable.estimateCost(
                    record.getTargetModel() != null ? record.getTargetModel() : record.getAliasModel(),
                    nullToZero(record.getPromptTokens()),
                    nullToZero(record.getCachedInputTokens()),
                    nullToZero(record.getCompletionTokens())
            );
            stat.setEstimatedCost(BigDecimal.valueOf(cost));

            statHourlyMapper.upsert(stat);
        } catch (Exception e) {
            log.warn("[请求统计] 更新小时聚合失败，requestId={}", record.getRequestId(), e);
        }
    }

    private RequestLogDO buildLog(RequestStatsContext context, String status, String errorCode, String errorMessage) {
        RequestLogDO logDO = new RequestLogDO();
        // 优先使用 correlationId 作为 requestId，便于链路追踪
        logDO.setRequestId(context.getCorrelationId() != null
                ? context.getCorrelationId() : UUID.randomUUID().toString());
        logDO.setAliasModel(context.getRequestInfo() != null ? context.getRequestInfo().getModel() : null);
        logDO.setTargetModel(resolveTargetModel(context));
        logDO.setProviderCode(resolveProviderCode(context));
        logDO.setProviderType(resolveProviderType(context));
        logDO.setResponseProtocol(context.getResponseProtocol() != null ? context.getResponseProtocol().name() : null);
        logDO.setRequestPath(context.getRequestPath());
        logDO.setHttpMethod(context.getHttpMethod());
        logDO.setApiKeyPrefix(context.getApiKeyPrefix());
        logDO.setCandidateCount(context.getCandidateCount());
        logDO.setAttemptCount(context.getAttemptCount());
        logDO.setFailoverCount(context.getFailoverCount());
        logDO.setRetryCount(context.getRetryCount());
        logDO.setCircuitOpenSkippedCount(context.getCircuitOpenSkippedCount());
        logDO.setRateLimitTriggered(context.getRateLimitTriggered());
        logDO.setUpstreamHttpStatus(context.getUpstreamHttpStatus());
        logDO.setUpstreamErrorType(context.getUpstreamErrorType());
        logDO.setTerminalStage(context.getTerminalStage());
        logDO.setIsStream(context.getRequestInfo() != null && Boolean.TRUE.equals(context.getRequestInfo().isStream()));
        logDO.setDurationMs((int) context.elapsedMs());
        logDO.setStatus(status);
        logDO.setErrorCode(errorCode);
        logDO.setErrorMessage(errorMessage);
        logDO.setSourceIp(context.getSourceIp());
        logDO.setCreateTime(LocalDateTime.now());
        return logDO;
    }

    private void applyUsage(RequestLogDO logDO, UnifiedUsage usage) {
        if (usage == null) {
            return;
        }
        logDO.setPromptTokens(usage.getInputTokens());
        logDO.setCachedInputTokens(usage.getCachedInputTokens());
        logDO.setCompletionTokens(usage.getOutputTokens());
        logDO.setTotalTokens(usage.getTotalTokens());
    }

    private void applyErrorContext(RequestStatsContext context, Throwable ex) {
        if (context.getTerminalStage() == null) {
            context.setTerminalStage("UPSTREAM");
        }
        if (ex instanceof GatewayException ge) {
            context.setUpstreamHttpStatus(ge.getUpstreamHttpStatus());
            context.setUpstreamErrorType(ge.getUpstreamErrorType());
        }
    }

    private String resolveErrorCode(Throwable ex) {
        if (ex instanceof com.code.aigateway.core.error.GatewayException ge) {
            return ge.getErrorCode().name();
        }
        return "INTERNAL_ERROR";
    }

    /**
     * 从异常中提取错误详情，拼接 HTTP 状态码 + 错误类型 + 错误描述
     */
    private String resolveErrorMessage(Throwable ex) {
        if (ex instanceof com.code.aigateway.core.error.GatewayException ge) {
            StringBuilder sb = new StringBuilder();
            if (ge.getUpstreamHttpStatus() != null) {
                sb.append("HTTP ").append(ge.getUpstreamHttpStatus());
            }
            if (ge.getUpstreamErrorType() != null && !ge.getUpstreamErrorType().isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append(" | ");
                }
                sb.append(ge.getUpstreamErrorType());
            }
            if (ge.getMessage() != null && !ge.getMessage().isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append(" | ");
                }
                String message = ge.getMessage();
                sb.append(message.length() > 100 ? message.substring(0, 100) + "..." : message);
            }
            if (sb.isEmpty()) {
                sb.append(ge.getErrorCode().name());
            }
            return sb.toString();
        }
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) {
            return null;
        }
        return msg.length() > 200 ? msg.substring(0, 200) + "..." : msg;
    }

    private String resolveTargetModel(RequestStatsContext context) {
        if (context.getFinalTargetModel() != null) {
            return context.getFinalTargetModel();
        }
        return context.getRouteResult() != null ? context.getRouteResult().getTargetModel() : null;
    }

    private String resolveProviderCode(RequestStatsContext context) {
        if (context.getFinalProviderCode() != null) {
            return context.getFinalProviderCode();
        }
        return context.getRouteResult() != null ? context.getRouteResult().getProviderName() : null;
    }

    private String resolveProviderType(RequestStatsContext context) {
        if (context.getFinalProviderType() != null) {
            return context.getFinalProviderType();
        }
        return context.getRouteResult() != null ? context.getRouteResult().getProviderType().name() : null;
    }

    private int nullToZero(Integer value) {
        return value != null ? value : 0;
    }

    /**
     * 优雅停机：尝试完成 sink 并刷盘剩余数据
     */
    @PreDestroy
    public void shutdown() {
        log.info("[请求统计] 优雅停机，刷盘剩余统计数据...");
        sink.tryEmitComplete();
    }
}
