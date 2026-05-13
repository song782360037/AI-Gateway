package com.code.aigateway.admin.service;

import com.code.aigateway.admin.model.rsp.JvmInfoRsp;
import com.code.aigateway.admin.model.rsp.SystemOverviewRsp;
import com.code.aigateway.admin.model.rsp.SystemRealtimeRsp;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.management.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统监控服务
 * <p>
 * 采集操作系统、JVM、线程、连接池等运行时指标，
 * 供管理后台系统监控页面展示。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemMonitorService {

    private final MeterRegistry meterRegistry;

    /** 缓存连接池 Meter 前缀，避免每次遍历全量 Meter */
    private volatile String cachedPoolPrefix;

    /**
     * 获取系统概览信息
     */
    public SystemOverviewRsp getOverview() {
        SystemOverviewRsp rsp = new SystemOverviewRsp();

        // 操作系统指标
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            rsp.setSystemCpuUsage(roundPercent(sunOsBean.getCpuLoad() * 100));
            rsp.setProcessCpuUsage(roundPercent(sunOsBean.getProcessCpuLoad() * 100));
            rsp.setTotalPhysicalMemory(sunOsBean.getTotalMemorySize());
            rsp.setUsedPhysicalMemory(sunOsBean.getTotalMemorySize() - sunOsBean.getFreeMemorySize());
        }

        // JVM 堆内存（max 可能为 -1 表示未定义上限，回退到 committed）
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        rsp.setJvmUsedHeap(heapUsage.getUsed());
        long maxHeap = heapUsage.getMax() > 0 ? heapUsage.getMax() : heapUsage.getCommitted();
        rsp.setJvmMaxHeap(maxHeap);
        rsp.setJvmUsedNonHeap(memoryBean.getNonHeapMemoryUsage().getUsed());

        // 线程信息
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        // 总启动线程数（含已销毁的），与当前活跃线程数形成有意义的对比
        rsp.setTotalThreadCount(threadBean.getTotalStartedThreadCount());
        rsp.setActiveThreadCount(threadBean.getThreadCount());
        rsp.setPeakThreadCount(threadBean.getPeakThreadCount());
        rsp.setDaemonThreadCount(threadBean.getDaemonThreadCount());

        // 启动时间和运行时长
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        rsp.setStartupTime(runtimeBean.getStartTime());
        rsp.setUptimeSeconds(runtimeBean.getUptime() / 1000);

        // 连接池信息
        rsp.setConnectionPool(fetchConnectionPoolInfo());

        return rsp;
    }

    /**
     * 获取 JVM 详细信息
     */
    public JvmInfoRsp getJvmInfo() {
        JvmInfoRsp rsp = new JvmInfoRsp();

        // JVM 基本信息
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        rsp.setJvmName(runtimeBean.getVmName());
        rsp.setJvmVersion(runtimeBean.getVmVersion());
        rsp.setJvmVendor(runtimeBean.getVmVendor());
        // 过滤掉可能包含敏感信息的 JVM 参数，仅保留不含密码/密钥等关键字的参数
        rsp.setVmArguments(runtimeBean.getInputArguments().stream()
                .filter(arg -> !containsSensitiveKeyword(arg))
                .collect(Collectors.joining(" ")));

        // 堆内存
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        rsp.setHeapMemory(toMemoryInfo(memoryBean.getHeapMemoryUsage()));
        rsp.setNonHeapMemory(toMemoryInfo(memoryBean.getNonHeapMemoryUsage()));

        // GC 信息
        List<JvmInfoRsp.GcInfo> gcInfos = new ArrayList<>();
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            JvmInfoRsp.GcInfo gcInfo = new JvmInfoRsp.GcInfo();
            gcInfo.setName(gcBean.getName());
            gcInfo.setCount(gcBean.getCollectionCount());
            gcInfo.setTotalTimeMs(gcBean.getCollectionTime());
            gcInfos.add(gcInfo);
        }
        rsp.setGcInfos(gcInfos);

        // 类加载信息
        ClassLoadingMXBean classBean = ManagementFactory.getClassLoadingMXBean();
        rsp.setLoadedClassCount(classBean.getLoadedClassCount());
        rsp.setTotalLoadedClassCount(classBean.getTotalLoadedClassCount());
        rsp.setUnloadedClassCount(classBean.getUnloadedClassCount());

        // 线程详细信息
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        JvmInfoRsp.ThreadDetailInfo threadDetail = new JvmInfoRsp.ThreadDetailInfo();
            // 总启动线程数（含已销毁），与当前活跃线程数形成有意义的对比
            threadDetail.setTotalCount(threadBean.getTotalStartedThreadCount());
            threadDetail.setActiveCount(threadBean.getThreadCount());
        threadDetail.setPeakCount(threadBean.getPeakThreadCount());
        threadDetail.setDaemonCount(threadBean.getDaemonThreadCount());
        // 检测死锁
        long[] deadlocked = threadBean.findDeadlockedThreads();
        threadDetail.setDeadlockedCount(deadlocked != null ? deadlocked.length : 0);
        rsp.setThreadDetail(threadDetail);

        return rsp;
    }

    /**
     * 获取实时指标（供前端定时刷新）
     */
    public SystemRealtimeRsp getRealtime() {
        SystemRealtimeRsp rsp = new SystemRealtimeRsp();
        rsp.setTimestamp(System.currentTimeMillis());

        // CPU 使用率
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            rsp.setSystemCpuUsage(roundPercent(sunOsBean.getCpuLoad() * 100));
            rsp.setProcessCpuUsage(roundPercent(sunOsBean.getProcessCpuLoad() * 100));
        }

        // JVM 堆内存
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        rsp.setJvmUsedHeap(heapUsage.getUsed());
        long maxHeap = heapUsage.getMax() > 0 ? heapUsage.getMax() : heapUsage.getCommitted();
        rsp.setJvmHeapUsage(maxHeap > 0 ? roundPercent((double) heapUsage.getUsed() / maxHeap * 100) : 0);

        // 活跃线程数
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        rsp.setActiveThreadCount(threadBean.getThreadCount());

        // 活跃连接数
        SystemOverviewRsp.ConnectionPoolInfo poolInfo = fetchConnectionPoolInfo();
        rsp.setActiveConnections(poolInfo.getActiveConnections());

        return rsp;
    }

    /**
     * 从 MeterRegistry 获取 Reactor Netty 连接池指标
     */
    private SystemOverviewRsp.ConnectionPoolInfo fetchConnectionPoolInfo() {
        SystemOverviewRsp.ConnectionPoolInfo info = new SystemOverviewRsp.ConnectionPoolInfo();
        try {
            // Reactor Netty 连接池注册的 Meter 名称规范：
            // reactor.netty.connection.provider.ai-gateway-pool.active.connections
            // reactor.netty.connection.provider.ai-gateway-pool.idle.connections
            // reactor.netty.connection.provider.ai-gateway-pool.max.connections
            // reactor.netty.connection.provider.ai-gateway-pool.pending.connections
            // 优先使用缓存的前缀；首次调用时动态探测，避免每次都遍历全量 Meter
            String prefix = cachedPoolPrefix;
            if (prefix == null) {
                synchronized (this) {
                    prefix = cachedPoolPrefix;
                    if (prefix == null) {
                        prefix = meterRegistry.getMeters().stream()
                                .map(m -> m.getId().getName())
                                .filter(name -> name.startsWith("reactor.netty.connection.provider."))
                                .map(name -> name.replaceAll("\\.(active|idle|max|pending)\\.connections$", ""))
                                .distinct()
                                .findFirst()
                                .orElse("reactor.netty.connection.provider.ai-gateway-pool");
                        cachedPoolPrefix = prefix;
                    }
                }
            }
            info.setActiveConnections(getGaugeValue(prefix + ".active.connections"));
            info.setIdleConnections(getGaugeValue(prefix + ".idle.connections"));
            info.setMaxConnections(getGaugeValue(prefix + ".max.connections"));
            info.setPendingConnections(getGaugeValue(prefix + ".pending.connections"));
        } catch (Exception e) {
            log.debug("获取连接池指标失败: {}", e.getMessage());
        }
        return info;
    }

    /**
     * 从 MeterRegistry 获取 Gauge 指标值
     */
    private int getGaugeValue(String meterName) {
        return Search.in(meterRegistry)
                .name(meterName)
                .gauges()
                .stream()
                .findFirst()
                .map(g -> (int) Math.round(g.value()))
                .orElse(0);
    }

    /**
     * 将 MemoryUsage 转换为 MemoryInfo
     */
    private JvmInfoRsp.MemoryInfo toMemoryInfo(MemoryUsage usage) {
        JvmInfoRsp.MemoryInfo info = new JvmInfoRsp.MemoryInfo();
        info.setUsed(usage.getUsed());
        long max = usage.getMax() > 0 ? usage.getMax() : usage.getCommitted();
        info.setMax(max);
        info.setCommitted(usage.getCommitted());
        info.setUsagePercent(max > 0 ? roundPercent((double) usage.getUsed() / max * 100) : 0);
        return info;
    }

    /** 敏感关键字列表，用于过滤 JVM 参数中的密码/密钥等敏感信息 */
    private static final List<String> SENSITIVE_KEYWORDS = List.of(
            "password", "passwd", "secret", "token", "apikey", "api-key", "credential",
            "authorization", "auth-token"
    );

    /**
     * 检查参数是否包含敏感关键字（不区分大小写）
     */
    private boolean containsSensitiveKeyword(String arg) {
        String lower = arg.toLowerCase();
        return SENSITIVE_KEYWORDS.stream().anyMatch(lower::contains);
    }

    /**
     * 四舍五入百分比，保留一位小数
     */
    private double roundPercent(double value) {
        if (value < 0) {
            return 0;
        }
        return Math.round(value * 10.0) / 10.0;
    }
}
