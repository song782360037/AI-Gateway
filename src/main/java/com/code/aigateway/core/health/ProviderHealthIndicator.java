package com.code.aigateway.core.health;

import com.code.aigateway.core.runtime.RoutingSnapshotHolder;
import com.code.aigateway.core.router.RoutingConfigSnapshot;
import com.code.aigateway.core.resilience.CircuitBreakerManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provider 聚合健康指标
 * <p>
 * 检查所有已配置的 AI Provider 连通性，聚合为 UP/DOWN/DEGRADED 三种状态：
 * <ul>
 *   <li>UP — 全部 Provider 可达</li>
 *   <li>DEGRADED — 部分 Provider 不可达（至少一个可用）</li>
 *   <li>DOWN — 全部不可达</li>
 * </ul>
 * 使用 30 秒缓存避免每次 /health 请求都探测。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderHealthIndicator implements ReactiveHealthIndicator {

    private final RoutingSnapshotHolder routingSnapshotHolder;
    private final CircuitBreakerManager circuitBreakerManager;

    /** 缓存有效期（秒） */
    private static final long CACHE_TTL_SECONDS = 30;

    /** 上次检查时间 */
    private volatile Instant lastCheckTime = Instant.EPOCH;

    /** 缓存的 Health 对象 */
    private volatile Health cachedHealth;

    @Override
    public Mono<Health> health() {
        // 缓存未过期时直接返回
        if (cachedHealth != null
                && Duration.between(lastCheckTime, Instant.now()).getSeconds() < CACHE_TTL_SECONDS) {
            return Mono.just(cachedHealth);
        }

        return Mono.fromCallable(this::doHealthCheck)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(health -> {
                    this.cachedHealth = health;
                    this.lastCheckTime = Instant.now();
                });
    }

    private Health doHealthCheck() {
        RoutingConfigSnapshot snapshot = routingSnapshotHolder.get();
        if (snapshot == null) {
            return Health.down()
                    .withDetail("reason", "routing snapshot not loaded")
                    .build();
        }

        Map<String, Object> providerDetails = new LinkedHashMap<>();
        int totalProviders = 0;
        int openCircuits = 0;

        for (var entry : snapshot.getProviderMap().entrySet()) {
            String providerCode = entry.getKey();
            RoutingConfigSnapshot.ProviderEntry provider = entry.getValue();

            totalProviders++;
            // 单次遍历获取 provider 下所有模型的熔断聚合
            CircuitBreakerManager.ProviderCircuitSummary summary =
                    circuitBreakerManager.getProviderSummary(providerCode);
            if (summary.anyOpen()) {
                openCircuits++;
            }

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("enabled", provider.enabled());
            detail.put("type", provider.providerType());
            detail.put("circuitOpen", summary.anyOpen());

            if (summary.anyOpen()) {
                detail.put("status", "OPEN (" + summary.openCount() + "/" + summary.totalCount() + " models)");
            } else {
                detail.put("status", "CLOSED");
            }

            providerDetails.put(providerCode, detail);
        }

        Health.Builder builder;
        if (totalProviders == 0) {
            builder = Health.down();
        } else if (openCircuits == 0) {
            builder = Health.up();
        } else if (openCircuits < totalProviders) {
            builder = Health.status("DEGRADED");
        } else {
            builder = Health.down();
        }

        return builder
                .withDetail("totalProviders", totalProviders)
                .withDetail("openCircuits", openCircuits)
                .withDetail("snapshotVersion", snapshot.getVersion())
                .withDetail("snapshotSource", snapshot.getSource())
                .withDetail("providers", providerDetails)
                .build();
    }
}
