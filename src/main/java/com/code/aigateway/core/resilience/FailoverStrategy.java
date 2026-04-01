package com.code.aigateway.core.resilience;

import com.code.aigateway.core.error.ErrorCode;
import com.code.aigateway.core.error.GatewayException;
import com.code.aigateway.core.router.RouteResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;

/**
 * Provider 故障转移策略
 * <p>
 * 遍历路由候选列表，按优先级依次尝试。
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
     * @param candidates   候选路由列表（按优先级排序）
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

        // 多候选：按优先级尝试，跳过熔断打开的
        Mono<T> chain = Mono.empty();
        for (int i = candidates.size() - 1; i >= 0; i--) {
            RouteResult candidate = candidates.get(i);
            final int index = i;
            // 从后往前构建 fallback 链：后面的候选作为前面失败时的备选
            chain = chain.switchIfEmpty(Mono.defer(() -> {
                // 检查熔断状态
                if (circuitBreakerManager.isCircuitOpen(candidate.getProviderName())) {
                    log.warn("[故障转移] provider={} 熔断已打开，跳过, correlationId={}",
                            candidate.getProviderName(), correlationId);
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
                        .doOnError(ex -> log.warn("[故障转移] 候选 #{} 失败: provider={}, error={}, correlationId={}",
                                index, candidate.getProviderName(), ex.getMessage(), correlationId));
            }));
        }

        return chain.switchIfEmpty(Mono.error(() ->
                new GatewayException(ErrorCode.PROVIDER_CIRCUIT_OPEN,
                        "all providers are circuit-open or unavailable")));
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

        Flux<T> chain = Flux.empty();
        for (int i = candidates.size() - 1; i >= 0; i--) {
            RouteResult candidate = candidates.get(i);
            final int index = i;
            chain = chain.switchIfEmpty(Flux.defer(() -> {
                if (circuitBreakerManager.isCircuitOpen(candidate.getProviderName())) {
                    log.warn("[故障转移-流式] provider={} 熔断已打开，跳过, correlationId={}",
                            candidate.getProviderName(), correlationId);
                    return Flux.empty();
                }
                return callFunction.apply(candidate)
                        .doOnError(ex -> log.warn("[故障转移-流式] 候选 #{} 失败: provider={}, error={}, correlationId={}",
                                index, candidate.getProviderName(), ex.getMessage(), correlationId));
            }));
        }

        return chain.switchIfEmpty(Flux.error(() ->
                new GatewayException(ErrorCode.PROVIDER_CIRCUIT_OPEN,
                        "all providers are circuit-open or unavailable")));
    }
}
