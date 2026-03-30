package com.code.aigateway.core.stats;

import com.code.aigateway.admin.mapper.RequestLogMapper;
import com.code.aigateway.admin.mapper.RequestStatHourlyMapper;
import com.code.aigateway.admin.model.dataobject.RequestLogDO;
import com.code.aigateway.admin.model.dataobject.RequestStatHourlyDO;
import com.code.aigateway.api.response.OpenAiChatCompletionResponse;
import com.code.aigateway.core.model.UnifiedUsage;
import jakarta.annotation.PostConstruct;
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
     */
    public void collectSuccess(RequestStatsContext context, OpenAiChatCompletionResponse response) {
        if (context == null || !context.tryMarkCollected()) {
            return;
        }
        OpenAiChatCompletionResponse.Usage usage = response != null ? response.getUsage() : null;
        RequestLogDO logDO = buildLog(context, "SUCCESS", null);
        if (usage != null) {
            logDO.setPromptTokens(usage.getPromptTokens());
            logDO.setCompletionTokens(usage.getCompletionTokens());
            logDO.setTotalTokens(usage.getTotalTokens());
        }
        emit(logDO);
    }

    /**
     * 记录流式请求成功
     */
    public void collectStreamSuccess(RequestStatsContext context, UnifiedUsage usage) {
        if (context == null || !context.tryMarkCollected()) {
            return;
        }
        RequestLogDO logDO = buildLog(context, "SUCCESS", null);
        if (usage != null) {
            logDO.setPromptTokens(usage.getInputTokens());
            logDO.setCompletionTokens(usage.getOutputTokens());
            logDO.setTotalTokens(usage.getTotalTokens());
        }
        emit(logDO);
    }

    /**
     * 记录请求失败
     */
    public void collectError(RequestStatsContext context, Throwable ex) {
        if (context == null || context.getRequest() == null || !context.tryMarkCollected()) {
            return;
        }
        RequestLogDO logDO = buildLog(context, "ERROR", resolveErrorCode(ex));
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
            stat.setErrorCount("ERROR".equals(record.getStatus()) ? 1 : 0);
            stat.setPromptTokens((long) nullToZero(record.getPromptTokens()));
            stat.setCompletionTokens((long) nullToZero(record.getCompletionTokens()));
            stat.setTotalTokens((long) nullToZero(record.getTotalTokens()));
            stat.setTotalDurationMs((long) nullToZero(record.getDurationMs()));

            double cost = ModelPriceTable.estimateCost(
                    record.getTargetModel() != null ? record.getTargetModel() : record.getAliasModel(),
                    nullToZero(record.getPromptTokens()),
                    nullToZero(record.getCompletionTokens())
            );
            stat.setEstimatedCost(BigDecimal.valueOf(cost));

            statHourlyMapper.upsert(stat);
        } catch (Exception e) {
            log.warn("[请求统计] 更新小时聚合失败，requestId={}", record.getRequestId(), e);
        }
    }

    private RequestLogDO buildLog(RequestStatsContext context, String status, String errorCode) {
        RequestLogDO logDO = new RequestLogDO();
        logDO.setRequestId(UUID.randomUUID().toString());
        logDO.setAliasModel(context.getRequest().getModel());
        logDO.setTargetModel(context.getRouteResult() != null ? context.getRouteResult().getTargetModel() : null);
        logDO.setProviderCode(context.getRouteResult() != null ? context.getRouteResult().getProviderName() : null);
        logDO.setProviderType(context.getRouteResult() != null ? context.getRouteResult().getProviderType().name() : null);
        logDO.setIsStream(Boolean.TRUE.equals(context.getRequest().getStream()));
        logDO.setDurationMs((int) context.elapsedMs());
        logDO.setStatus(status);
        logDO.setErrorCode(errorCode);
        logDO.setSourceIp(context.getSourceIp());
        logDO.setCreateTime(LocalDateTime.now());
        return logDO;
    }

    private String resolveErrorCode(Throwable ex) {
        if (ex instanceof com.code.aigateway.core.error.GatewayException ge) {
            return ge.getErrorCode().name();
        }
        return "INTERNAL_ERROR";
    }

    private int nullToZero(Integer value) {
        return value != null ? value : 0;
    }
}
