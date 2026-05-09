package com.code.aigateway.core.stats;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 活跃请求跟踪器
 * <p>
 * 记录当前正在处理中的请求信息（客户端IP、提供商、模型等），
 * 用于仪表盘实时展示"正在请求"的数量和详情。
 * </p>
 * <p>
 * 线程安全：内部使用 ConcurrentHashMap 存储，适合高并发场景。
 * 内置定时清理任务，防止因未捕获异常导致的请求未被正常移除。
 * </p>
 */
@Slf4j
@Component
public class ActiveRequestTracker {

    /** 请求超时阈值：超过此时间的活跃请求视为泄漏，自动清理（10分钟） */
    private static final long LEAK_THRESHOLD_MINUTES = 10;

    /** 活跃请求存储：correlationId → 请求信息 */
    private final ConcurrentHashMap<String, ActiveRequest> activeRequests = new ConcurrentHashMap<>();

    /** 定时清理调度器（守护线程，不阻止 JVM 退出） */
    private final ScheduledExecutorService cleanupScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "active-request-cleanup");
                t.setDaemon(true);
                return t;
            });

    @PostConstruct
    public void init() {
        // 每分钟清理一次超时的泄漏请求
        cleanupScheduler.scheduleAtFixedRate(() -> {
            try {
                cleanupLeakedRequests();
            } catch (Exception ex) {
                log.warn("[活跃请求] 清理泄漏请求任务执行失败", ex);
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    @PreDestroy
    public void shutdown() {
        cleanupScheduler.shutdownNow();
    }

    /**
     * 清理超时的泄漏请求（超过阈值时间仍未被移除的请求）
     */
    private void cleanupLeakedRequests() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(LEAK_THRESHOLD_MINUTES);
        List<String> leakedKeys = new ArrayList<>();
        for (Map.Entry<String, ActiveRequest> entry : activeRequests.entrySet()) {
            if (entry.getValue().getStartTime() != null && entry.getValue().getStartTime().isBefore(threshold)) {
                leakedKeys.add(entry.getKey());
            }
        }
        if (!leakedKeys.isEmpty()) {
            log.warn("[活跃请求] 清理 {} 个泄漏请求（超过 {} 分钟未结束）", leakedKeys.size(), LEAK_THRESHOLD_MINUTES);
            leakedKeys.forEach(activeRequests::remove);
        }
    }

    /**
     * 注册一个活跃请求
     *
     * @param correlationId 请求链路追踪ID
     * @param sourceIp      来源IP
     * @param model         请求模型（别名）
     * @param isStream      是否流式请求
     */
    public void register(String correlationId, String sourceIp, String model, Boolean isStream) {
        if (correlationId == null || correlationId.isBlank()) {
            return;
        }
        ActiveRequest req = new ActiveRequest();
        req.setCorrelationId(correlationId);
        req.setSourceIp(sourceIp);
        req.setModel(model);
        req.setStream(Boolean.TRUE.equals(isStream));
        req.setStartTime(LocalDateTime.now());
        activeRequests.put(correlationId, req);
    }

    /**
     * 更新活跃请求的原始请求信息。
     */
    public void updateRequestInfo(String correlationId, String model, Boolean isStream) {
        if (correlationId == null) {
            return;
        }
        ActiveRequest req = activeRequests.get(correlationId);
        if (req == null) {
            return;
        }
        req.setModel(model);
        req.setStream(Boolean.TRUE.equals(isStream));
    }

    /**
     * 更新活跃请求的路由信息（提供商编码、目标模型）
     * <p>
     * 在路由完成后调用，补充提供商和目标模型信息。
     * </p>
     */
    public void updateRoute(String correlationId, String providerCode, String targetModel) {
        if (correlationId == null) {
            return;
        }
        ActiveRequest req = activeRequests.get(correlationId);
        if (req != null) {
            req.setProviderCode(providerCode);
            req.setTargetModel(targetModel);
        }
    }

    /**
     * 移除一个活跃请求（请求结束时调用）
     */
    public void remove(String correlationId) {
        if (correlationId != null) {
            activeRequests.remove(correlationId);
        }
    }

    /**
     * 获取当前活跃请求总数
     */
    public int getActiveCount() {
        return activeRequests.size();
    }

    /**
     * 获取当前正在请求的唯一客户端（来源IP）数量
     */
    public int getActiveClientCount() {
        return (int) activeRequests.values().stream()
                .map(ActiveRequest::getSourceIp)
                .filter(ip -> ip != null && !ip.isBlank() && !"unknown".equals(ip))
                .distinct()
                .count();
    }

    /**
     * 获取所有活跃请求的快照列表
     */
    public List<ActiveRequest> getActiveRequests() {
        return new ArrayList<>(activeRequests.values());
    }

    /**
     * 获取按提供商+模型分组的活跃请求数量
     * <p>
     * 返回 Map 的 key 格式为 "providerCode|targetModel"，value 为该组合的请求数量。
     * </p>
     */
    public Map<String, Integer> getActiveGroupByProviderModel() {
        return activeRequests.values().stream()
                .collect(Collectors.groupingBy(
                        req -> {
                            String provider = req.getProviderCode() != null ? req.getProviderCode() : "pending";
                            String model = req.getTargetModel() != null ? req.getTargetModel() : "pending";
                            return provider + "|" + model;
                        },
                        Collectors.summingInt(e -> 1)
                ));
    }

    /**
     * 活跃请求信息
     */
    @Getter
    @Setter
    public static class ActiveRequest {
        /** 链路追踪ID */
        private String correlationId;
        /** 来源IP */
        private String sourceIp;
        /** 请求模型（别名） */
        private String model;
        /** 是否流式 */
        private boolean stream;
        /** 提供商编码（路由后填充） */
        private String providerCode;
        /** 目标模型（路由后填充） */
        private String targetModel;
        /** 请求开始时间 */
        private LocalDateTime startTime;
    }
}
