package com.code.aigateway.core.resilience;

import com.code.aigateway.config.GatewayProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnErrorEvent;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 熔断器管理器
 * <p>
 * 为每个 AI Provider 维护独立的熔断器实例。
 * 基于 resilience4j CircuitBreakerRegistry 动态创建和管理。
 * 熔断维度：providerCode（如 openai-main），而非 providerType，
 * 因为同类型不同渠道的健康状态可能不同。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CircuitBreakerManager {

    private final GatewayProperties gatewayProperties;
    private CircuitBreakerRegistry registry;

    @PostConstruct
    public void init() {
        GatewayProperties.CircuitBreakerProperties props = gatewayProperties.getCircuitBreaker();
        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig config;

        if (props != null && props.isEnabled()) {
            config = io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                    .slidingWindowSize(props.getSlidingWindowSize())
                    .failureRateThreshold(props.getFailureRateThreshold())
                    .slowCallDurationThreshold(java.time.Duration.ofMillis(props.getSlowCallDurationMs()))
                    .slowCallRateThreshold(props.getSlowCallRateThreshold())
                    .waitDurationInOpenState(java.time.Duration.ofMillis(props.getWaitDurationInOpenStateMs()))
                    .permittedNumberOfCallsInHalfOpenState(props.getPermittedNumberOfCallsInHalfOpenState())
                    .minimumNumberOfCalls(props.getMinimumNumberOfCalls())
                    .build();
        } else {
            config = io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.ofDefaults();
        }

        // 通过 RegistryEventConsumer 在每个熔断器创建时注册事件监听
        registry = CircuitBreakerRegistry.of(config, List.of(new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(io.github.resilience4j.core.registry.EntryAddedEvent<CircuitBreaker> event) {
                registerListeners(event.getAddedEntry());
            }

            @Override
            public void onEntryRemovedEvent(io.github.resilience4j.core.registry.EntryRemovedEvent<CircuitBreaker> event) {
                // 无需处理
            }

            @Override
            public void onEntryReplacedEvent(io.github.resilience4j.core.registry.EntryReplacedEvent<CircuitBreaker> event) {
                registerListeners(event.getNewEntry());
            }
        }));

        log.info("[熔断器] 初始化完成，配置: slidingWindowSize={}, failureRateThreshold={}%",
                config.getSlidingWindowSize(), config.getFailureRateThreshold());
    }

    /**
     * 获取或创建指定 provider 的熔断器
     *
     * @param providerCode Provider 编码（如 openai-main）
     * @return 熔断器实例
     */
    public CircuitBreaker getOrCreate(String providerCode) {
        GatewayProperties.CircuitBreakerProperties props = gatewayProperties.getCircuitBreaker();
        if (props == null || !props.isEnabled()) {
            // 熔断未启用时返回始终关闭的实例
            return registry.circuitBreaker(providerCode + "_disabled",
                    io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                            .slidingWindowSize(1)
                            .failureRateThreshold(100)
                            .build());
        }
        return registry.circuitBreaker(providerCode);
    }

    /**
     * 检查指定 provider 的熔断器是否打开
     */
    public boolean isCircuitOpen(String providerCode) {
        GatewayProperties.CircuitBreakerProperties props = gatewayProperties.getCircuitBreaker();
        if (props == null || !props.isEnabled()) {
            return false;
        }
        CircuitBreaker cb = registry.find(providerCode).orElse(null);
        if (cb == null) {
            return false;
        }
        return cb.getState() == CircuitBreaker.State.OPEN;
    }

    /**
     * 获取熔断器状态描述
     */
    public String getState(String providerCode) {
        CircuitBreaker cb = registry.find(providerCode).orElse(null);
        if (cb == null) {
            return "CLOSED";
        }
        return cb.getState().name();
    }

    /**
     * 获取熔断器指标（用于健康检查和监控）
     */
    public CircuitBreaker.Metrics getMetrics(String providerCode) {
        CircuitBreaker cb = registry.find(providerCode).orElse(null);
        if (cb == null) {
            return null;
        }
        return cb.getMetrics();
    }

    private void onCircuitBreakerError(CircuitBreakerOnErrorEvent event) {
        log.debug("[熔断器] provider={}, 记录一次失败: {}",
                event.getCircuitBreakerName(), event.getThrowable().getMessage());
    }

    private void onStateTransition(CircuitBreakerOnStateTransitionEvent event) {
        log.warn("[熔断器] provider={}, 状态变更: {} -> {}",
                event.getCircuitBreakerName(),
                event.getStateTransition().getFromState(),
                event.getStateTransition().getToState());
    }

    /** 为单个熔断器实例注册事件监听器 */
    private void registerListeners(CircuitBreaker cb) {
        cb.getEventPublisher()
                .onError(this::onCircuitBreakerError)
                .onStateTransition(this::onStateTransition);
    }
}
