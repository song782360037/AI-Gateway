package com.code.aigateway.core.resilience;

import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.router.RouteResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
        if (candidates == null || candidates.isEmpty()) {
            return Mono.error(new GatewayException(ErrorCode.PROVIDER_NOT_FOUND, "no route candidates available"));
        }

        // 单候选直接调用，无故障转移
        if (candidates.size() == 1) {
            return callFunction.apply(candidates.get(0));
        }

        // 收集每个候选的错误信息，用于最终错误聚合
        final List<CandidateError> candidateErrors = Collections.synchronizedList(new ArrayList<>());

        // 从前往后构建 switchIfEmpty 链：index 0 在最内层（最先执行），index N 在最外层（最后执行）
        Mono<T> chain = Mono.empty();
        for (int i = 0; i < candidates.size(); i++) {
            RouteResult candidate = candidates.get(i);
            final int index = i;
            chain = chain.switchIfEmpty(Mono.defer(() -> {
                // 按 provider+model 维度检查熔断状态
                if (circuitBreakerManager.isCircuitOpen(candidate.getProviderName(), candidate.getTargetModel())) {
                    log.warn("[故障转移] provider={}, model={} 熔断已打开，跳过, correlationId={}",
                            candidate.getProviderName(), candidate.getTargetModel(), correlationId);
                    candidateErrors.add(new CandidateError(
                            candidate.getProviderName(), candidate.getTargetModel(), "circuit-open"));
                    return Mono.empty();
                }
                log.debug("[故障转移] 尝试候选 #{}: provider={}, model={}, correlationId={}",
                        index, candidate.getProviderName(), candidate.getTargetModel(), correlationId);
                return callFunction.apply(candidate)
                        .doOnNext(result -> {
                            if (index > 0) {
                                log.info("[故障转移] 候选 #{} 成功（非首选）, provider={}, correlationId={}",
                                        index, candidate.getProviderName(), correlationId);
                            }
                        })
                        .onErrorResume(ex -> {
                            log.warn("[故障转移] 候选 #{} 失败: provider={}, error={}, correlationId={}",
                                    index, candidate.getProviderName(), ex.getMessage(), correlationId);
                            candidateErrors.add(new CandidateError(
                                    candidate.getProviderName(), candidate.getTargetModel(),
                                    extractErrorMessage(ex)));
                            return Mono.empty();
                        });
            }));
        }

        return chain.switchIfEmpty(Mono.error(() ->
                buildAllFailedException(candidateErrors, correlationId)));
    }

    /**
     * 流式请求故障转移（仅首 token 前可转移）
     */
    public <T> Flux<T> executeStreamWithFailover(List<RouteResult> candidates,
                                                 Function<RouteResult, Flux<T>> callFunction,
                                                 String correlationId) {
        if (candidates == null || candidates.isEmpty()) {
            return Flux.error(new GatewayException(ErrorCode.PROVIDER_NOT_FOUND, "no route candidates available"));
        }

        if (candidates.size() == 1) {
            return callFunction.apply(candidates.get(0));
        }

        // 收集每个候选的错误信息
        final List<CandidateError> candidateErrors = Collections.synchronizedList(new ArrayList<>());

        // 从前往后构建 switchIfEmpty 链：index 0 在最内层（最先执行），index N 在最外层（最后执行）
        Flux<T> chain = Flux.empty();
        for (int i = 0; i < candidates.size(); i++) {
            RouteResult candidate = candidates.get(i);
            final int index = i;
            chain = chain.switchIfEmpty(Flux.defer(() -> {
                // 按 provider+model 维度检查熔断状态
                if (circuitBreakerManager.isCircuitOpen(candidate.getProviderName(), candidate.getTargetModel())) {
                    log.warn("[故障转移-流式] provider={}, model={} 熔断已打开，跳过, correlationId={}",
                            candidate.getProviderName(), candidate.getTargetModel(), correlationId);
                    candidateErrors.add(new CandidateError(
                            candidate.getProviderName(), candidate.getTargetModel(), "circuit-open"));
                    return Flux.empty();
                }
                return callFunction.apply(candidate)
                        .onErrorResume(ex -> {
                            log.warn("[故障转移-流式] 候选 #{} 失败: provider={}, model={}, error={}, correlationId={}",
                                    index, candidate.getProviderName(), candidate.getTargetModel(),
                                    ex.getMessage(), correlationId);
                            candidateErrors.add(new CandidateError(
                                    candidate.getProviderName(), candidate.getTargetModel(),
                                    extractErrorMessage(ex)));
                            return Flux.empty();
                        });
            }));
        }

        return chain.switchIfEmpty(Flux.error(() ->
                buildAllFailedException(candidateErrors, correlationId)));
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
     * 构建所有候选都失败时的聚合异常，保留每个候选的真实错误原因
     */
    private GatewayException buildAllFailedException(List<CandidateError> candidateErrors, String correlationId) {
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
}
