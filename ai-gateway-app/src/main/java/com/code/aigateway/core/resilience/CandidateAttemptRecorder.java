package com.code.aigateway.core.resilience;

import com.code.aigateway.core.router.RouteResult;
import com.code.aigateway.core.stats.RequestStatsContext;
import com.code.aigateway.core.stats.TraceDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * 候选尝试记录器
 * <p>
 * 从 FailoverStrategy 中提取，专门管理故障转移过程中的链路追踪详情
 *（TraceDetails）的创建、记录、更新和序列化。
 * </p>
 */
@Slf4j
class CandidateAttemptRecorder {

    private static final ObjectMapper TRACE_MAPPER = new ObjectMapper();

    private final RequestStatsContext context;
    private final TraceDetails traceDetails;

    CandidateAttemptRecorder(RequestStatsContext context) {
        this.context = context;
        this.traceDetails = createOrGet();
    }

    TraceDetails traceDetails() {
        return traceDetails;
    }

    /** 记录候选尝试 */
    void record(int index, RouteResult candidate, String status,
                String errorMessage, Integer httpStatus, String errorType,
                int retryCount, Long attemptStart) {
        if (traceDetails == null) return;
        TraceDetails.CandidateAttempt attempt = buildAttempt(
                index, candidate, status, errorMessage, httpStatus, errorType, retryCount, attemptStart);
        traceDetails.addCandidateAttempt(attempt);
    }

    /** 更新或新增候选尝试记录 */
    void upsert(int index, RouteResult candidate, String status,
                String errorMessage, Integer httpStatus, String errorType,
                int retryCount, Long attemptStart) {
        if (traceDetails == null) return;
        TraceDetails.CandidateAttempt existing = traceDetails.findCandidateAttempt(index);
        if (existing == null) {
            record(index, candidate, status, errorMessage, httpStatus, errorType, retryCount, attemptStart);
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

    /** 完成追踪详情汇总并刷新 JSON */
    void finalize(RouteResult successCandidate) {
        if (traceDetails == null || context == null) return;
        if (successCandidate != null) {
            traceDetails.setFinalProviderCode(successCandidate.getProviderName());
            traceDetails.setFinalTargetModel(successCandidate.getTargetModel());
        }
        traceDetails.setTotalAttempts(context.getAttemptCount());
        traceDetails.setTotalFailovers(context.getFailoverCount());
        traceDetails.setTotalRetries(context.getRetryCount());
        traceDetails.setCircuitOpenSkippedCount(context.getCircuitOpenSkippedCount());
        try {
            TraceDetails snapshot = createSnapshot(traceDetails);
            context.setTraceDetailsJson(TRACE_MAPPER.writeValueAsString(snapshot));
        } catch (Exception e) {
            log.warn("[故障转移] 序列化链路追踪详情失败, correlationId={}", context.getCorrelationId(), e);
        }
    }

    int retryCount() {
        return context == null ? 0 : context.getRetryCount();
    }

    int retryDelta(int retryStart) {
        return Math.max(0, retryCount() - retryStart);
    }

    // ===================== 私有方法 =====================

    private TraceDetails createOrGet() {
        if (context == null) return new TraceDetails();
        TraceDetails existing = context.getTraceDetails();
        if (existing == null) {
            existing = new TraceDetails();
            context.setTraceDetails(existing);
        }
        return existing;
    }

    private TraceDetails.CandidateAttempt buildAttempt(int index, RouteResult candidate,
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

    private TraceDetails createSnapshot(TraceDetails source) {
        TraceDetails snapshot = new TraceDetails();
        snapshot.setFinalProviderCode(source.getFinalProviderCode());
        snapshot.setFinalTargetModel(source.getFinalTargetModel());
        snapshot.setTotalAttempts(source.getTotalAttempts());
        snapshot.setTotalFailovers(source.getTotalFailovers());
        snapshot.setTotalRetries(source.getTotalRetries());
        snapshot.setCircuitOpenSkippedCount(source.getCircuitOpenSkippedCount());
        snapshot.setKeySelectionStrategy(source.getKeySelectionStrategy());
        snapshot.setKeySelectionReason(source.getKeySelectionReason());
        for (TraceDetails.CandidateAttempt attempt : source.getCandidateAttemptsSnapshot()) {
            snapshot.addCandidateAttempt(attempt);
        }
        return snapshot;
    }
}
