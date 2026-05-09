package com.code.aigateway.core.resilience;

import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.router.RouteResult;
import com.code.aigateway.core.stats.RequestStatsContext;
import com.code.aigateway.core.stats.TraceDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provider 故障转移策略
 * <p>
 * 遍历路由候选列表，按优先级从高到低依次尝试。
 * 跳过熔断已打开的 Provider，主 Provider 失败时自动切换到备选。
 * 流式请求仅首 token 前可故障转移，避免客户端收到重复/缺失内容。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FailoverStrategy {

    private final CircuitBreakerManager circuitBreakerManager;

    /** 链路追踪详情序列化器（线程安全，复用单例；仅用于简单 POJO，不含 Java 8 时间类型） */
    private static final ObjectMapper TRACE_MAPPER = new ObjectMapper();

    /**
     * 非流式请求故障转移
     *
     * @param candidates   候选路由列表（按优先级排序，index 0 最高）
     * @param callFunction 调用函数，接收 RouteResult 返回 Mono
     * @param correlationId 请求链路追踪 ID
     * @param <T>          响应类型
     * @return 第一个成功的响应，或最后一个错误
     */
    public <T> Mono<T> executeWithFailover(List<RouteResult> candidates,
                                           Function<RouteResult, Mono<T>> callFunction,
                                           String correlationId) {
        return executeWithFailover(candidates, callFunction, correlationId, null);
    }

    public <T> Mono<T> executeWithFailover(List<RouteResult> candidates,
                                           Function<RouteResult, Mono<T>> callFunction,
                                           String correlationId,
                                           RequestStatsContext context) {
        if (candidates == null || candidates.isEmpty()) {
            markTerminalStage(context, "ROUTING");
            return Mono.error(new GatewayException(ErrorCode.PROVIDER_NOT_FOUND, "no route candidates available"));
        }

        // 初始化链路追踪详情
        TraceDetails traceDetails = getOrCreateTraceDetails(context);

        // 单候选直接调用，无故障转移
        if (candidates.size() == 1) {
            if (context != null) {
                context.incrementAttemptCount();
            }
            return wrapSingleCandidateMono(callFunction, candidates.get(0), traceDetails, context);
        }

        // 收集每个候选的错误信息，用于最终错误聚合
        final List<CandidateError> candidateErrors = Collections.synchronizedList(new ArrayList<>());

        // 从前往后构建 switchIfEmpty 链：index 0 在最内层（最先执行），index N 在最外层（最后执行）
        Mono<T> chain = Mono.empty();
        for (int i = 0; i < candidates.size(); i++) {
            RouteResult candidate = candidates.get(i);
            final int index = i;
            chain = chain.switchIfEmpty(Mono.defer(() -> {
                if (context != null) {
                    context.incrementAttemptCount();
                }
                // 按 provider+model 维度检查熔断状态
                if (circuitBreakerManager.isCircuitOpen(candidate.getProviderName(), candidate.getTargetModel())) {
                    log.warn("[故障转移] provider={}, model={} 熔断已打开，跳过, correlationId={}",
                            candidate.getProviderName(), candidate.getTargetModel(), correlationId);
                    candidateErrors.add(new CandidateError(
                            candidate.getProviderName(), candidate.getTargetModel(), "circuit-open"));
                    // 记录熔断跳过的候选
                    recordCandidateAttempt(traceDetails, index, candidate, "CIRCUIT_OPEN",
                            "circuit-open", null, null, 0, null);
                    if (context != null) {
                        context.incrementCircuitOpenSkippedCount();
                        markTerminalStage(context, "FAILOVER");
                    }
                    return Mono.empty();
                }
                log.debug("[故障转移] 尝试候选 #{}: provider={}, model={}, correlationId={}",
                        index, candidate.getProviderName(), candidate.getTargetModel(), correlationId);
                long attemptStart = System.currentTimeMillis();
                int retryStart = retryCount(context);
                return callFunction.apply(candidate)
                        .doOnNext(result -> {
                            if (index > 0) {
                                log.info("[故障转移] 候选 #{} 成功（非首选）, provider={}, correlationId={}",
                                        index, candidate.getProviderName(), correlationId);
                            }
                            // 记录成功的候选尝试
                            recordCandidateAttempt(traceDetails, index, candidate, "SUCCESS",
                                    null, null, null, retryDelta(context, retryStart), attemptStart);
                            finalizeTraceDetails(traceDetails, context, candidate);
                        })
                        .onErrorResume(ex -> {
                            // 客户端侧错误（认证失败、参数错误、资源不存在）直接透传，不继续尝试其他候选
                            if (shouldSkipFailover(ex)) {
                                markTerminalStage(context, "UPSTREAM");
                                // 记录客户端侧错误的候选尝试
                                recordCandidateAttempt(traceDetails, index, candidate, "FAILED",
                                        extractErrorMessage(ex), extractHttpStatus(ex), extractErrorType(ex),
                                        retryDelta(context, retryStart), attemptStart);
                                finalizeTraceDetails(traceDetails, context, null);
                                return Mono.error(ex);
                            }
                            log.warn("[故障转移] 候选 #{} 失败: provider={}, error={}, correlationId={}",
                                    index, candidate.getProviderName(), ex.getMessage(), correlationId);
                            candidateErrors.add(new CandidateError(
                                    candidate.getProviderName(), candidate.getTargetModel(),
                                    extractErrorMessage(ex)));
                            // 记录失败的候选尝试
                            upsertCandidateAttempt(traceDetails, index, candidate, "FAILED",
                                    extractErrorMessage(ex), extractHttpStatus(ex), extractErrorType(ex),
                                    retryDelta(context, retryStart), attemptStart);
                            if (context != null) {
                                context.incrementFailoverCount();
                                markTerminalStage(context, "FAILOVER");
                            }
                            return Mono.empty();
                        });
            }));
        }

        return chain.switchIfEmpty(Mono.error(() -> {
            finalizeTraceDetails(traceDetails, context, null);
            return buildAllFailedException(candidateErrors, correlationId, context);
        }));
    }

    /**
     * 流式请求故障转移（仅首 token 前可转移）
     */
    public <T> Flux<T> executeStreamWithFailover(List<RouteResult> candidates,
                                                 Function<RouteResult, Flux<T>> callFunction,
                                                 String correlationId) {
        return executeStreamWithFailover(candidates, callFunction, correlationId, null);
    }

    /**
     * 流式请求故障转移（仅首 token 前可转移）
     */
    public <T> Flux<T> executeStreamWithFailover(List<RouteResult> candidates,
                                                 Function<RouteResult, Flux<T>> callFunction,
                                                 String correlationId,
                                                 RequestStatsContext context) {
        if (candidates == null || candidates.isEmpty()) {
            markTerminalStage(context, "ROUTING");
            return Flux.error(new GatewayException(ErrorCode.PROVIDER_NOT_FOUND, "no route candidates available"));
        }

        // 初始化链路追踪详情
        TraceDetails traceDetails = getOrCreateTraceDetails(context);

        if (candidates.size() == 1) {
            if (context != null) {
                context.incrementAttemptCount();
            }
            return executeStreamCandidate(callFunction, candidates.get(0), 0, traceDetails, context,
                    new ArrayList<>(), correlationId, false);
        }

        // 收集每个候选的错误信息
        final List<CandidateError> candidateErrors = Collections.synchronizedList(new ArrayList<>());

        // 从前往后构建 switchIfEmpty 链：index 0 在最内层（最先执行），index N 在最外层（最后执行）
        Flux<T> chain = Flux.empty();
        for (int i = 0; i < candidates.size(); i++) {
            RouteResult candidate = candidates.get(i);
            final int index = i;
            chain = chain.switchIfEmpty(Flux.defer(() -> {
                if (context != null) {
                    context.incrementAttemptCount();
                }
                return executeStreamCandidate(callFunction, candidate, index, traceDetails, context,
                        candidateErrors, correlationId, true);
            }));
        }

        return chain.switchIfEmpty(Flux.error(() -> {
            finalizeTraceDetails(traceDetails, context, null);
            return buildAllFailedException(candidateErrors, correlationId, context);
        }));
    }

    // ===================== 单候选包装方法 =====================

    /**
     * 执行单个流式候选，并根据是否允许 failover 决定首 token 前错误的处理方式。
     */
    private <T> Flux<T> executeStreamCandidate(Function<RouteResult, Flux<T>> callFunction,
                                               RouteResult candidate,
                                               int index,
                                               TraceDetails traceDetails,
                                               RequestStatsContext context,
                                               List<CandidateError> candidateErrors,
                                               String correlationId,
                                               boolean allowFailover) {
        if (circuitBreakerManager.isCircuitOpen(candidate.getProviderName(), candidate.getTargetModel())) {
            log.warn("[故障转移-流式] provider={}, model={} 熔断已打开，跳过, correlationId={}",
                    candidate.getProviderName(), candidate.getTargetModel(), correlationId);
            candidateErrors.add(new CandidateError(candidate.getProviderName(), candidate.getTargetModel(), "circuit-open"));
            recordCandidateAttempt(traceDetails, index, candidate, "CIRCUIT_OPEN", "circuit-open", null, null, 0, null);
            if (context != null) {
                context.incrementCircuitOpenSkippedCount();
                markTerminalStage(context, "FAILOVER");
            }
            return allowFailover ? Flux.empty() : Flux.error(new GatewayException(ErrorCode.PROVIDER_CIRCUIT_OPEN,
                    "provider circuit is open"));
        }

        long attemptStart = System.currentTimeMillis();
        int retryStart = retryCount(context);
        AtomicBoolean emitted = new AtomicBoolean(false);
        return callFunction.apply(candidate)
                .doOnNext(ignored -> markStreamEmitted(traceDetails, context, candidate, index, emitted, retryStart, attemptStart))
                .doOnComplete(() -> {
                    upsertCandidateAttempt(traceDetails, index, candidate, "SUCCESS",
                            null, null, null, retryDelta(context, retryStart), attemptStart);
                    finalizeTraceDetails(traceDetails, context, candidate);
                })
                .onErrorResume(ex -> handleStreamCandidateError(
                        ex, traceDetails, context, candidate, index, emitted, retryStart,
                        attemptStart, candidateErrors, correlationId, allowFailover));
    }

    private void markStreamEmitted(TraceDetails traceDetails,
                                   RequestStatsContext context,
                                   RouteResult candidate,
                                   int index,
                                   AtomicBoolean emitted,
                                   int retryStart,
                                   long attemptStart) {
        if (emitted.compareAndSet(false, true)) {
            recordCandidateAttempt(traceDetails, index, candidate, "STREAMING",
                    null, null, null, retryDelta(context, retryStart), attemptStart);
            finalizeTraceDetails(traceDetails, context, candidate);
        }
    }

    private <T> Flux<T> handleStreamCandidateError(Throwable ex,
                                                   TraceDetails traceDetails,
                                                   RequestStatsContext context,
                                                   RouteResult candidate,
                                                   int index,
                                                   AtomicBoolean emitted,
                                                   int retryStart,
                                                   long attemptStart,
                                                   List<CandidateError> candidateErrors,
                                                   String correlationId,
                                                   boolean allowFailover) {
        if (emitted.get()) {
            markTerminalStage(context, "STREAMING");
            upsertCandidateAttempt(traceDetails, index, candidate, "STREAMING",
                    extractErrorMessage(ex), extractHttpStatus(ex), extractErrorType(ex),
                    retryDelta(context, retryStart), attemptStart);
            finalizeTraceDetails(traceDetails, context, candidate);
            return Flux.error(ex);
        }
        if (shouldSkipFailover(ex) || !allowFailover) {
            markTerminalStage(context, "UPSTREAM");
            upsertCandidateAttempt(traceDetails, index, candidate, "FAILED",
                    extractErrorMessage(ex), extractHttpStatus(ex), extractErrorType(ex),
                    retryDelta(context, retryStart), attemptStart);
            finalizeTraceDetails(traceDetails, context, null);
            return Flux.error(ex);
        }

        log.warn("[故障转移-流式] 候选 #{} 失败: provider={}, model={}, error={}, correlationId={}",
                index, candidate.getProviderName(), candidate.getTargetModel(), ex.getMessage(), correlationId);
        candidateErrors.add(new CandidateError(candidate.getProviderName(), candidate.getTargetModel(), extractErrorMessage(ex)));
        upsertCandidateAttempt(traceDetails, index, candidate, "FAILED",
                extractErrorMessage(ex), extractHttpStatus(ex), extractErrorType(ex),
                retryDelta(context, retryStart), attemptStart);
        if (context != null) {
            context.incrementFailoverCount();
            markTerminalStage(context, "FAILOVER");
        }
        return Flux.empty();
    }

    /**
     * 包装单候选的非流式调用，统一处理链路追踪记录
     */
    private <T> Mono<T> wrapSingleCandidateMono(Function<RouteResult, Mono<T>> callFunction,
                                                 RouteResult candidate,
                                                 TraceDetails traceDetails,
                                                 RequestStatsContext context) {
        long attemptStart = System.currentTimeMillis();
        int retryStart = retryCount(context);
        return callFunction.apply(candidate)
                .doOnNext(result -> {
                    recordCandidateAttempt(traceDetails, 0, candidate, "SUCCESS", null, null, null,
                            retryDelta(context, retryStart), attemptStart);
                })
                .doOnError(ex -> {
                    recordCandidateAttempt(traceDetails, 0, candidate, "FAILED",
                            extractErrorMessage(ex), extractHttpStatus(ex), extractErrorType(ex),
                            retryDelta(context, retryStart), attemptStart);
                })
                .doFinally(signalType -> {
                    if (signalType == SignalType.ON_COMPLETE) {
                        finalizeTraceDetails(traceDetails, context, candidate);
                    } else {
                        finalizeTraceDetails(traceDetails, context, null);
                    }
                });
    }

    // ===================== 链路追踪辅助方法 =====================

    /**
     * 获取或创建链路追踪详情对象
     */
    private TraceDetails getOrCreateTraceDetails(RequestStatsContext context) {
        if (context == null) {
            return new TraceDetails();
        }
        // 从 context 获取或创建 traceDetails
        TraceDetails existing = context.getTraceDetails();
        if (existing == null) {
            existing = new TraceDetails();
            context.setTraceDetails(existing);
        }
        return existing;
    }

    /**
     * 记录单个候选提供商的尝试信息
     */
    private void recordCandidateAttempt(TraceDetails traceDetails, int index, RouteResult candidate,
                                        String status, String errorMessage, Integer httpStatus,
                                        String errorType, int retryCount, Long attemptStart) {
        if (traceDetails == null) return;

        TraceDetails.CandidateAttempt attempt = buildCandidateAttempt(
                index, candidate, status, errorMessage, httpStatus, errorType, retryCount, attemptStart);
        traceDetails.addCandidateAttempt(attempt);
    }

    /**
     * 更新已存在的候选尝试记录；不存在时新增。
     */
    private void upsertCandidateAttempt(TraceDetails traceDetails, int index, RouteResult candidate,
                                        String status, String errorMessage, Integer httpStatus,
                                        String errorType, int retryCount, Long attemptStart) {
        if (traceDetails == null) return;

        TraceDetails.CandidateAttempt existing = traceDetails.findCandidateAttempt(index);
        if (existing == null) {
            recordCandidateAttempt(traceDetails, index, candidate, status, errorMessage, httpStatus, errorType, retryCount, attemptStart);
            return;
        }
        existing.setStatus(status);
        existing.setErrorMessage(errorMessage);
        existing.setHttpStatus(httpStatus);
        existing.setErrorType(errorType);
        existing.setRetryCount(retryCount);
        if (attemptStart != null) {
            existing.setAttemptStartTime(attemptStart);
            existing.setDurationMs(System.currentTimeMillis() - attemptStart);
        }
    }

    private TraceDetails.CandidateAttempt buildCandidateAttempt(int index, RouteResult candidate,
                                                               String status, String errorMessage, Integer httpStatus,
                                                               String errorType, int retryCount, Long attemptStart) {
        TraceDetails.CandidateAttempt attempt = new TraceDetails.CandidateAttempt(
                index, candidate.getProviderName(), candidate.getTargetModel());
        attempt.setStatus(status);
        attempt.setErrorMessage(errorMessage);
        attempt.setHttpStatus(httpStatus);
        attempt.setErrorType(errorType);
        attempt.setRetryCount(retryCount);
        if (attemptStart != null) {
            attempt.setAttemptStartTime(attemptStart);
            attempt.setDurationMs(System.currentTimeMillis() - attemptStart);
        }
        return attempt;
    }

    private int retryCount(RequestStatsContext context) {
        return context == null ? 0 : context.getRetryCount();
    }

    private int retryDelta(RequestStatsContext context, int retryStart) {
        return Math.max(0, retryCount(context) - retryStart);
    }

    /**
     * 完成链路追踪详情的汇总
     * <p>
     * 会更新 TraceDetails 内存对象（统计数据、最终提供商），并刷新 JSON 到 context。
     * 流式请求可能先记录 STREAMING，随后在完成或错误时覆盖为最终状态。
     * </p>
     */
    private void finalizeTraceDetails(TraceDetails traceDetails, RequestStatsContext context, RouteResult successCandidate) {
        if (traceDetails == null || context == null) return;

        // 设置最终选择的提供商（始终更新内存对象）
        if (successCandidate != null) {
            traceDetails.setFinalProviderCode(successCandidate.getProviderName());
            traceDetails.setFinalTargetModel(successCandidate.getTargetModel());
        }

        // 汇总统计（始终更新内存对象，反映最新状态）
        traceDetails.setTotalAttempts(context.getAttemptCount());
        traceDetails.setTotalFailovers(context.getFailoverCount());
        traceDetails.setTotalRetries(context.getRetryCount());
        traceDetails.setCircuitOpenSkippedCount(context.getCircuitOpenSkippedCount());

        // 每次 finalize 都刷新 JSON，确保流式取消后 STREAMING 记录能被后续 SUCCESS/FAILED 覆盖。
        try {
            TraceDetails snapshot = createTraceDetailsSnapshot(traceDetails);
            String json = TRACE_MAPPER.writeValueAsString(snapshot);
            context.setTraceDetailsJson(json);
        } catch (Exception e) {
            log.warn("[故障转移] 序列化链路追踪详情失败, correlationId={}", context.getCorrelationId(), e);
        }
    }

    /**
     * 创建 TraceDetails 的快照，用于线程安全的序列化
     */
    private TraceDetails createTraceDetailsSnapshot(TraceDetails source) {
        TraceDetails snapshot = new TraceDetails();
        snapshot.setFinalProviderCode(source.getFinalProviderCode());
        snapshot.setFinalTargetModel(source.getFinalTargetModel());
        snapshot.setTotalAttempts(source.getTotalAttempts());
        snapshot.setTotalFailovers(source.getTotalFailovers());
        snapshot.setTotalRetries(source.getTotalRetries());
        snapshot.setCircuitOpenSkippedCount(source.getCircuitOpenSkippedCount());
        // 使用同步快照获取候选尝试记录
        for (TraceDetails.CandidateAttempt attempt : source.getCandidateAttemptsSnapshot()) {
            snapshot.addCandidateAttempt(attempt);
        }
        return snapshot;
    }

    /**
     * 判断错误是否不应触发故障转移（客户端侧错误，换 Provider 也会失败）
     */
    private boolean shouldSkipFailover(Throwable ex) {
        if (ex instanceof GatewayException gwEx) {
            ErrorCode code = gwEx.getErrorCode();
            return code == ErrorCode.PROVIDER_AUTH_ERROR
                    || code == ErrorCode.PROVIDER_BAD_REQUEST
                    || code == ErrorCode.PROVIDER_RESOURCE_NOT_FOUND;
        }
        return false;
    }

    /**
     * 候选错误记录
     */
    private record CandidateError(String provider, String model, String error) {}

    /**
     * 从异常中提取可读的错误消息，优先使用 GatewayException 的上游上下文
     */
    private String extractErrorMessage(Throwable ex) {
        if (ex instanceof GatewayException gwEx) {
            StringBuilder sb = new StringBuilder(ex.getMessage());
            if (gwEx.getUpstreamHttpStatus() != null) {
                sb.append(" (HTTP ").append(gwEx.getUpstreamHttpStatus()).append(")");
            }
            if (gwEx.getUpstreamErrorType() != null) {
                sb.append(" [").append(gwEx.getUpstreamErrorType()).append("]");
            }
            return sb.toString();
        }
        return ex.getMessage();
    }

    /**
     * 从异常中提取上游 HTTP 状态码
     */
    private Integer extractHttpStatus(Throwable ex) {
        if (ex instanceof GatewayException gwEx) {
            Integer status = gwEx.getUpstreamHttpStatus();
            return (status != null && status > 0) ? status : null;
        }
        return null;
    }

    /**
     * 从异常中提取上游错误类型
     */
    private String extractErrorType(Throwable ex) {
        if (ex instanceof GatewayException gwEx) {
            return gwEx.getUpstreamErrorType();
        }
        return null;
    }

    /**
     * 构建所有候选都失败时的聚合异常，保留每个候选的真实错误原因
     */
    private GatewayException buildAllFailedException(List<CandidateError> candidateErrors,
                                                     String correlationId,
                                                     RequestStatsContext context) {
        markTerminalStage(context, "FAILOVER");
        if (candidateErrors.isEmpty()) {
            return new GatewayException(ErrorCode.PROVIDER_CIRCUIT_OPEN,
                    "all providers are circuit-open or unavailable");
        }

        String details = candidateErrors.stream()
                .map(e -> e.provider() + "/" + e.model() + ": " + e.error())
                .collect(Collectors.joining("; "));

        String message = "all providers failed — " + details;
        log.error("[故障转移] 所有候选均失败, correlationId={}, 详情: {}", correlationId, details);

        return new GatewayException(ErrorCode.PROVIDER_CIRCUIT_OPEN, message);
    }

    private void markTerminalStage(RequestStatsContext context, String terminalStage) {
        if (context != null) {
            context.setTerminalStage(terminalStage);
        }
    }
}
