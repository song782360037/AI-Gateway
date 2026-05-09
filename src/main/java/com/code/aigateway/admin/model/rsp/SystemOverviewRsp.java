package com.code.aigateway.admin.model.rsp;

import lombok.Data;

/**
 * 系统监控概览响应
 */
@Data
public class SystemOverviewRsp {

    /** 系统 CPU 使用率（百分比，0-100） */
    private double systemCpuUsage;

    /** 进程 CPU 使用率（百分比，0-100） */
    private double processCpuUsage;

    /** 物理内存总量（字节） */
    private long totalPhysicalMemory;

    /** 已用物理内存（字节） */
    private long usedPhysicalMemory;

    /** JVM 堆内存已用（字节） */
    private long jvmUsedHeap;

    /** JVM 堆内存最大（字节） */
    private long jvmMaxHeap;

    /** JVM 非堆内存已用（字节） */
    private long jvmUsedNonHeap;

    /** 总线程数（含已销毁线程，使用 long 避免长时间运行后溢出） */
    private long totalThreadCount;

    /** 活跃线程数 */
    private int activeThreadCount;

    /** 峰值线程数 */
    private int peakThreadCount;

    /** 守护线程数 */
    private int daemonThreadCount;

    /** 应用启动时间（毫秒时间戳） */
    private long startupTime;

    /** 应用运行时长（秒） */
    private long uptimeSeconds;

    /** 连接池信息 */
    private ConnectionPoolInfo connectionPool;

    /**
     * 连接池信息
     */
    @Data
    public static class ConnectionPoolInfo {

        /** 活跃连接数 */
        private int activeConnections;

        /** 空闲连接数 */
        private int idleConnections;

        /** 最大连接数 */
        private int maxConnections;

        /** 待等待连接数 */
        private int pendingConnections;
    }
}
