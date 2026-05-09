package com.code.aigateway.admin.model.rsp;

import lombok.Data;

/**
 * 系统监控实时指标响应（用于前端定时刷新图表）
 */
@Data
public class SystemRealtimeRsp {

    /** 系统 CPU 使用率（百分比，0-100） */
    private double systemCpuUsage;

    /** 进程 CPU 使用率（百分比，0-100） */
    private double processCpuUsage;

    /** JVM 堆内存使用率（百分比，0-100） */
    private double jvmHeapUsage;

    /** JVM 已用堆内存（字节） */
    private long jvmUsedHeap;

    /** 活跃线程数 */
    private int activeThreadCount;

    /** 活跃连接数 */
    private int activeConnections;

    /** 当前时间戳（毫秒） */
    private long timestamp;
}
