package com.code.aigateway.core.resilience;

import com.code.aigateway.sdk.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.router.RouteResult;
import com.code.aigateway.core.stats.RequestStatsContext;
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
 * Provider 故障转移策略，含链路追踪记录。
 * <p>
 * 链路追踪详情管理委托给 {@link CandidateAttemptRecorder}，本类专注于故障转移编排。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FailoverStrategy {

    private final CircuitBreakerManager circuitBreakerManager;

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

        CandidateAttemptRecorder recorder = new CandidateAttemptRecorder(context);

        if (candidates.size() == 1) {
            if (context != null) context.incrementAttemptCount();
            return wrapSingleCandidateMono(callFunction, candidates.get(0), recorder, context);
        }

        final List<CandidateError> candidateErrors = Collections.synchronizedList(new ArrayList<>());

        Mono<T> chain = Mono.empty();
        for (int i = 0; i < candidates.size(); i++) {
            RouteResult candidate = candidates.get(i);
            final int index = i;
            chain = chain.switchIfEmpty(Mono.defer(() -> {
                if (context != null) context.incrementAttemptCount();
                if (circuitBreakerManager.isCircuitOpen(candidate.getProviderName(), candidate.getTargetModel())) {
                    log.warn("[故障转移] provider={}, model={} 熔断已打开, correlationId={}",
                            candidate.getProviderName(), candidate.getTargetModel(), correlationId);
                    candidateErrors.add(new CandidateError(candidate.getProviderName(), candidate.getTargetModel(), "circuit-open"));
                    recorder.record(index, candidate, "CIRCUIT_OPEN", "circuit-open", null, null, 0, null);
                    if (context != null) {
                        context.incrementCircuitOpenSkippedCount();
                        markTerminalStage(context, "FAILOVER");
                    }
                    return Mono.empty();
                }
                log.debug("[故障转移] 尝试候选 #{}: provider={}, model={}, correlationId={}",
                        index, candidate.getProviderName(), candidate.getTargetModel(), correlationId);
                long attemptStart = System.currentTimeMillis();
                int retryStart = recorder.retryCount();
                // 用 Mono.defer 包裹 callFunction，确保同步异常被转为响应式 error，
                // 否则 onErrorResume 不会被挂上，导致异常穿透 failover 链
                return Mono.defer(() -> callFunction.apply(candidate))
                        .doOnNext(result -> {
                            if (index > 0) {
                                log.info("[故障转移] 候选 #{} 成功, provider={}, correlationId={}",
                                        index, candidate.getProviderName(), correlationId);
                            }
                            recorder.record(index, candidate, "SUCCESS", null, null, null,
                                    recorder.retryDelta(retryStart), attemptStart);
                            recorder.finalize(candidate);
                        })
                        .onErrorResume(ex -> {
                            if (shouldSkipFailover(ex)) {
                                markTerminalStage(context, "UPSTREAM");
                                recorder.record(index, candidate, "FAILED",
                                        extractErrorMessage(ex), extractHttpStatus(ex), extractErrorType(ex),
                                        recorder.retryDelta(retryStart), attemptStart);
                                recorder.finalize(null);
                                return Mono.error(ex);
                            }
                            log.warn("[故障转移] 候选 #{} 失败: provider={}, error={}, correlationId={}",
                                    index, candidate.getProviderName(), ex.getMessage(), correlationId);
                            candidateErrors.add(new CandidateError(candidate.getProviderName(), candidate.getTargetModel(), extractErrorMessage(ex)));
                            recorder.upsert(index, candidate, "FAILED",
                                    extractErrorMessage(ex), extractHttpStatus(ex), extractErrorType(ex),
                                    recorder.retryDelta(retryStart), attemptStart);
                            if (context != null) {
                                context.incrementFailoverCount();
                                markTerminalStage(context, "FAILOVER");
                            }
                            return Mono.empty();
                        });
            }));
        }

        return chain.switchIfEmpty(Mono.error(() -> {
            recorder.finalize(null);
            return buildAllFailedException(candidateErrors, correlationId, context);
        }));
    }

    public <T> Flux<T> executeStreamWithFailover(List<RouteResult> candidates,
                                                 Function<RouteResult, Flux<T>> callFunction,
                                                 String correlationId) {
        return executeStreamWithFailover(candidates, callFunction, correlationId, null);
    }

    public <T> Flux<T> executeStreamWithFailover(List<RouteResult> candidates,
                                                 Function<RouteResult, Flux<T>> callFunction,
                                                 String correlationId,
                                                 RequestStatsContext context) {
        if (candidates == null || candidates.isEmpty()) {
            markTerminalStage(context, "ROUTING");
            return Flux.error(new GatewayException(ErrorCode.PROVIDER_NOT_FOUND, "no route candidates available"));
        }

        CandidateAttemptRecorder recorder = new CandidateAttemptRecorder(context);

        if (candidates.size() == 1) {
            if (context != null) context.incrementAttemptCount();
            return executeStreamCandidate(callFunction, candidates.get(0), 0, recorder, context,
                    new ArrayList<>(), correlationId, false);
        }

        final List<CandidateError> candidateErrors = Collections.synchronizedList(new ArrayList<>());

        Flux<T> chain = Flux.empty();
        for (int i = 0; i < candidates.size(); i++) {
            RouteResult candidate = candidates.get(i);
            final int index = i;
            chain = chain.switchIfEmpty(Flux.defer(() -> {
                if (context != null) context.incrementAttemptCount();
                return executeStreamCandidate(callFunction, candidate, index, recorder, context,
                        candidateErrors, correlationId, true);
            }));
        }

        return chain.switchIfEmpty(Flux.error(() -> {
            recorder.finalize(null);
            return buildAllFailedException(candidateErrors, correlationId, context);
        }));
    }

    // ===================== 单候选调用 =====================

    private <T> Flux<T> executeStreamCandidate(Function<RouteResult, Flux<T>> callFunction,
                                               RouteResult candidate, int index,
                                               CandidateAttemptRecorder recorder,
                                               RequestStatsContext context,
                                               List<CandidateError> candidateErrors,
                                               String correlationId, boolean allowFailover) {
        if (circuitBreakerManager.isCircuitOpen(candidate.getProviderName(), candidate.getTargetModel())) {
            log.warn("[故障转移-流式] provider={}, model={} 熔断已打开, correlationId={}",
                    candidate.getProviderName(), candidate.getTargetModel(), correlationId);
            candidateErrors.add(new CandidateError(candidate.getProviderName(), candidate.getTargetModel(), "circuit-open"));
            recorder.record(index, candidate, "CIRCUIT_OPEN", "circuit-open", null, null, 0, null);
            if (context != null) {
                context.incrementCircuitOpenSkippedCount();
                markTerminalStage(context, "FAILOVER");
            }
            return allowFailover ? Flux.empty() : Flux.error(new GatewayException(ErrorCode.PROVIDER_CIRCUIT_OPEN, "provider circuit is open"));
        }

        long attemptStart = System.currentTimeMillis();
        int retryStart = recorder.retryCount();
        AtomicBoolean emitted = new AtomicBoolean(false);
        // 用 Flux.defer 包裹 callFunction，确保同步异常被转为响应式 error，
        // 否则 onErrorResume 不会被挂上，导致异常穿透 failover 链
        return Flux.defer(() -> callFunction.apply(candidate))
                .doOnNext(ignored -> {
                    if (emitted.compareAndSet(false, true)) {
                        recorder.record(index, candidate, "STREAMING", null, null, null,
                                recorder.retryDelta(retryStart), attemptStart);
                        recorder.finalize(candidate);
                    }
                })
                .doOnComplete(() -> {
                    recorder.upsert(index, candidate, "SUCCESS", null, null, null,
                            recorder.retryDelta(retryStart), attemptStart);
                    recorder.finalize(candidate);
                })
                .onErrorResume(ex -> {
                    if (emitted.get()) {
                        markTerminalStage(context, "STREAMING");
                        recorder.upsert(index, candidate, "STREAMING",
                                extractErrorMessage(ex), extractHttpStatus(ex), extractErrorType(ex),
                                recorder.retryDelta(retryStart), attemptStart);
                        recorder.finalize(candidate);
                        return Flux.error(ex);
                    }
                    if (shouldSkipFailover(ex) || !allowFailover) {
                        markTerminalStage(context, "UPSTREAM");
                        recorder.upsert(index, candidate, "FAILED",
                                extractErrorMessage(ex), extractHttpStatus(ex), extractErrorType(ex),
                                recorder.retryDelta(retryStart), attemptStart);
                        recorder.finalize(null);
                        return Flux.error(ex);
                    }
                    log.warn("[故障转移-流式] 候选 #{} 失败: provider={}, model={}, error={}, correlationId={}",
                            index, candidate.getProviderName(), candidate.getTargetModel(), ex.getMessage(), correlationId);
                    candidateErrors.add(new CandidateError(candidate.getProviderName(), candidate.getTargetModel(), extractErrorMessage(ex)));
                    recorder.upsert(index, candidate, "FAILED",
                            extractErrorMessage(ex), extractHttpStatus(ex), extractErrorType(ex),
                            recorder.retryDelta(retryStart), attemptStart);
                    if (context != null) {
                        context.incrementFailoverCount();
                        markTerminalStage(context, "FAILOVER");
                    }
                    return Flux.empty();
                });
    }

    private <T> Mono<T> wrapSingleCandidateMono(Function<RouteResult, Mono<T>> callFunction,
                                                 RouteResult candidate,
                                                 CandidateAttemptRecorder recorder,
                                                 RequestStatsContext context) {
        long attemptStart = System.currentTimeMillis();
        int retryStart = recorder.retryCount();
        // 用 Mono.defer 包裹，确保同步异常被转为响应式 error
        return Mono.defer(() -> callFunction.apply(candidate))
                .doOnNext(result -> recorder.record(0, candidate, "SUCCESS", null, null, null,
                        recorder.retryDelta(retryStart), attemptStart))
                .doOnError(ex -> recorder.record(0, candidate, "FAILED",
                        extractErrorMessage(ex), extractHttpStatus(ex), extractErrorType(ex),
                        recorder.retryDelta(retryStart), attemptStart))
                .doFinally(signalType -> {
                    if (signalType == SignalType.ON_COMPLETE) {
                        recorder.finalize(candidate);
                    } else {
                        recorder.finalize(null);
                    }
                });
    }

    // ===================== 错误处理 =====================

    private boolean shouldSkipFailover(Throwable ex) {
        if (ex instanceof GatewayException gwEx) {
            ErrorCode code = gwEx.getErrorCode();
            return code == ErrorCode.PROVIDER_AUTH_ERROR
                    || code == ErrorCode.PROVIDER_BAD_REQUEST
                    || code == ErrorCode.PROVIDER_RESOURCE_NOT_FOUND;
        }
        return false;
    }

    private String extractErrorMessage(Throwable ex) {
        if (ex instanceof GatewayException gwEx) {
            StringBuilder sb = new StringBuilder(ex.getMessage());
            if (gwEx.getUpstreamHttpStatus() != null) sb.append(" (HTTP ").append(gwEx.getUpstreamHttpStatus()).append(")");
            if (gwEx.getUpstreamErrorType() != null) sb.append(" [").append(gwEx.getUpstreamErrorType()).append("]");
            return sb.toString();
        }
        return ex.getMessage();
    }

    private Integer extractHttpStatus(Throwable ex) {
        if (ex instanceof GatewayException gwEx) {
            Integer status = gwEx.getUpstreamHttpStatus();
            return (status != null && status > 0) ? status : null;
        }
        return null;
    }

    private String extractErrorType(Throwable ex) {
        if (ex instanceof GatewayException gwEx) return gwEx.getUpstreamErrorType();
        return null;
    }

    private GatewayException buildAllFailedException(List<CandidateError> candidateErrors,
                                                     String correlationId, RequestStatsContext context) {
        markTerminalStage(context, "FAILOVER");
        if (candidateErrors.isEmpty()) {
            return new GatewayException(ErrorCode.PROVIDER_CIRCUIT_OPEN, "all providers are circuit-open or unavailable");
        }
        String details = candidateErrors.stream()
                .map(e -> e.provider() + "/" + e.model() + ": " + e.error())
                .collect(Collectors.joining("; "));
        log.error("[故障转移] 所有候选均失败, correlationId={}, 详情: {}", correlationId, details);
        return new GatewayException(ErrorCode.PROVIDER_CIRCUIT_OPEN, "all providers failed — " + details);
    }

    private void markTerminalStage(RequestStatsContext context, String terminalStage) {
        if (context != null) context.setTerminalStage(terminalStage);
    }

    private record CandidateError(String provider, String model, String error) {}
}
