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
 * 为每个 AI Provider 的每个模型维护独立的熔断器实例。
 * 基于 resilience4j CircuitBreakerRegistry 动态创建和管理。
 * 熔断维度：providerCode + model（如 openai-main:gpt-4），
 * 避免单个模型失败导致同一 Provider 下所有模型被熔断。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CircuitBreakerManager {

    private static final String KEY_SEPARATOR = ":";

    /** disabled 实例的前缀，避免被聚合查询误计 */
    private static final String DISABLED_PREFIX = "_disabled_";

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

        log.info("[熔断器] 初始化完成，熔断维度: provider+model，配置: slidingWindowSize={}, failureRateThreshold={}%",
                config.getSlidingWindowSize(), config.getFailureRateThreshold());
    }

    /**
     * 构建复合 key：providerCode:model
     */
    private String buildKey(String providerCode, String model) {
        return providerCode + KEY_SEPARATOR + model;
    }

    /**
     * 获取或创建指定 provider+model 的熔断器
     *
     * @param providerCode Provider 编码（如 openai-main）
     * @param model        目标模型名（如 gpt-4）
     * @return 熔断器实例
     */
    public CircuitBreaker getOrCreate(String providerCode, String model) {
        GatewayProperties.CircuitBreakerProperties props = gatewayProperties.getCircuitBreaker();
        if (props == null || !props.isEnabled()) {
            // 使用独立前缀，避免被 isAnyCircuitOpen/getProviderSummary 聚合误计
            return registry.circuitBreaker(DISABLED_PREFIX + buildKey(providerCode, model),
                    io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                            .slidingWindowSize(1)
                            .failureRateThreshold(100)
                            .build());
        }
        return registry.circuitBreaker(buildKey(providerCode, model));
    }

    /**
     * 检查指定 provider+model 的熔断器是否打开
     */
    public boolean isCircuitOpen(String providerCode, String model) {
        GatewayProperties.CircuitBreakerProperties props = gatewayProperties.getCircuitBreaker();
        if (props == null || !props.isEnabled()) {
            return false;
        }
        CircuitBreaker cb = registry.find(buildKey(providerCode, model)).orElse(null);
        if (cb == null) {
            return false;
        }
        return cb.getState() == CircuitBreaker.State.OPEN;
    }

    /**
     * 获取指定 provider+model 的熔断器状态描述
     */
    public String getState(String providerCode, String model) {
        CircuitBreaker cb = registry.find(buildKey(providerCode, model)).orElse(null);
        if (cb == null) {
            return "CLOSED";
        }
        return cb.getState().name();
    }

    /**
     * 获取指定 provider+model 的熔断器指标
     */
    public CircuitBreaker.Metrics getMetrics(String providerCode, String model) {
        CircuitBreaker cb = registry.find(buildKey(providerCode, model)).orElse(null);
        if (cb == null) {
            return null;
        }
        return cb.getMetrics();
    }

    /**
     * 重置指定 Provider 下所有模型的熔断器状态为 CLOSED。
     * <p>
     * 适用于优先级变更后需要主动恢复的场景：当管理员将某个 Provider
     * 提升为最高优先级时，可调用此方法清除该 Provider 历史遗留的熔断状态，
     * 使其能立即接受新请求而非等待半开探测周期。
     * </p>
     *
     * @param providerCode Provider 编码
     * @return 重置的熔断器数量
     */
    public long resetProviderCircuits(String providerCode) {
        long resetCount = 0;
        for (CircuitBreaker cb : registry.getAllCircuitBreakers()) {
            if (isCircuitBreakerOfProvider(cb.getName(), providerCode)
                    && cb.getState() != CircuitBreaker.State.CLOSED) {
                cb.reset();
                resetCount++;
                log.info("[熔断器] 手动重置 provider+model={}, 状态 -> CLOSED", cb.getName());
            }
        }
        return resetCount;
    }

    /**
     * 重置指定 provider+model 的熔断器状态为 CLOSED。
     *
     * @param providerCode Provider 编码
     * @param model        目标模型名
     * @return true 表示重置成功（熔断器存在且非 CLOSED），false 表示无需重置
     */
    public boolean resetCircuit(String providerCode, String model) {
        CircuitBreaker cb = registry.find(buildKey(providerCode, model)).orElse(null);
        if (cb == null || cb.getState() == CircuitBreaker.State.CLOSED) {
            return false;
        }
        cb.reset();
        log.info("[熔断器] 手动重置 provider+model={}, 状态 -> CLOSED", cb.getName());
        return true;
    }

    /**
     * 获取指定 Provider 的熔断器聚合摘要（单次遍历）
     * <p>
     * 用于健康检查，一次遍历同时计算：是否有任意模型熔断打开、
     * 已打开熔断的模型数、总模型熔断器数。
     * </p>
     * <p>
     * 注意：使用精确匹配而非前缀匹配，避免当一个 providerCode 是另一个的前缀时
     * （如 "openai" 与 "openai-main"）发生跨提供商熔断状态泄漏。
     * </p>
     *
     * @param providerCode Provider 编码
     * @return 聚合摘要，熔断未启用时返回全零的摘要（anyOpen=false）
     */
    public ProviderCircuitSummary getProviderSummary(String providerCode) {
        GatewayProperties.CircuitBreakerProperties props = gatewayProperties.getCircuitBreaker();
        if (props == null || !props.isEnabled()) {
            return ProviderCircuitSummary.EMPTY;
        }

        long totalCount = 0;
        long openCount = 0;

        for (CircuitBreaker cb : registry.getAllCircuitBreakers()) {
            // 精确匹配：提取 CB 名称中的 providerCode 部分，确保不会因前缀重叠导致跨提供商泄漏
            if (isCircuitBreakerOfProvider(cb.getName(), providerCode)) {
                totalCount++;
                if (cb.getState() == CircuitBreaker.State.OPEN) {
                    openCount++;
                }
            }
        }

        return new ProviderCircuitSummary(openCount > 0, openCount, totalCount);
    }

    /**
     * 判断熔断器名称是否属于指定 Provider。
     * <p>
     * CB 名称格式为 "providerCode:model"，需要精确匹配 providerCode 部分，
     * 而非简单的前缀匹配，避免 "openai" 与 "openai-main" 等编码前缀重叠导致误判。
     * 同时排除 disabled 前缀的虚拟熔断器。
     * </p>
     *
     * @param cbName       熔断器注册表中的名称
     * @param providerCode Provider 编码
     * @return 是否属于指定 Provider
     */
    private boolean isCircuitBreakerOfProvider(String cbName, String providerCode) {
        // 跳过 disabled 虚拟熔断器（前缀 "_disabled_"）
        if (cbName.startsWith(DISABLED_PREFIX)) {
            return false;
        }
        // 精确匹配：提取 CB 名称中第一个分隔符之前的 providerCode 部分，与入参严格相等
        // 例如 providerCode="openai" 只匹配 "openai:gpt-4"，不匹配 "openai-main:gpt-4"
        int separatorIndex = cbName.indexOf(KEY_SEPARATOR);
        if (separatorIndex <= 0) {
            return false;
        }
        return cbName.substring(0, separatorIndex).equals(providerCode);
    }

    /**
     * Provider 熔断器聚合摘要
     */
    public record ProviderCircuitSummary(boolean anyOpen, long openCount, long totalCount) {
        /** 熔断未启用时的空摘要 */
        static final ProviderCircuitSummary EMPTY = new ProviderCircuitSummary(false, 0, 0);
    }

    private void onCircuitBreakerError(CircuitBreakerOnErrorEvent event) {
        log.debug("[熔断器] provider+model={}, 记录一次失败: {}",
                event.getCircuitBreakerName(), event.getThrowable().getMessage());
    }

    private void onStateTransition(CircuitBreakerOnStateTransitionEvent event) {
        log.warn("[熔断器] provider+model={}, 状态变更: {} -> {}",
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
