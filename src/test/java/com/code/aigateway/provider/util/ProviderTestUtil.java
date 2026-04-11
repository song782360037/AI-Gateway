package com.code.aigateway.provider.util;

import com.code.aigateway.core.resilience.CircuitBreakerManager;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.mockito.Mockito;

/**
 * Provider 测试公共工具方法
 */
public final class ProviderTestUtil {

    private ProviderTestUtil() {}

    /**
     * 创建一个永不熔断的 CircuitBreakerManager
     */
    public static CircuitBreakerManager noopCircuitBreakerManager() {
        CircuitBreaker noopCb = CircuitBreaker.of("test",
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(1)
                        .failureRateThreshold(100)
                        .minimumNumberOfCalls(9999)
                        .build());
        CircuitBreakerManager cbManager = Mockito.mock(CircuitBreakerManager.class);
        Mockito.when(cbManager.getOrCreate(Mockito.anyString(), Mockito.anyString())).thenReturn(noopCb);
        return cbManager;
    }
}
