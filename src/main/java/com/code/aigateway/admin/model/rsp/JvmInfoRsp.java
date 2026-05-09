package com.code.aigateway.admin.model.rsp;

import lombok.Data;

import java.util.List;

/**
 * JVM 详细信息响应
 */
@Data
public class JvmInfoRsp {

    /** JVM 名称（如 OpenJDK 64-Bit Server VM） */
    private String jvmName;

    /** JVM 版本（如 21.0.1） */
    private String jvmVersion;

    /** JVM 供应商 */
    private String jvmVendor;

    /** JVM 启动参数 */
    private String vmArguments;

    /** 堆内存信息 */
    private MemoryInfo heapMemory;

    /** 非堆内存信息 */
    private MemoryInfo nonHeapMemory;

    /** GC 信息列表 */
    private List<GcInfo> gcInfos;

    /** 已加载类数量 */
    private int loadedClassCount;

    /** 类加载总数（含已卸载） */
    private long totalLoadedClassCount;

    /** 已卸载类数量 */
    private long unloadedClassCount;

    /** 线程详细信息 */
    private ThreadDetailInfo threadDetail;

    /**
     * 内存信息
     */
    @Data
    public static class MemoryInfo {

        /** 已用内存（字节） */
        private long used;

        /** 最大可用内存（字节），-1 表示未定义上限 */
        private long max;

        /** 已提交内存（字节） */
        private long committed;

        /** 使用率（百分比，0-100） */
        private double usagePercent;
    }

    /**
     * GC 信息
     */
    @Data
    public static class GcInfo {

        /** GC 名称（如 G1 Young Generation） */
        private String name;

        /** GC 总次数 */
        private long count;

        /** GC 总耗时（毫秒） */
        private long totalTimeMs;
    }

    /**
     * 线程详细信息
     */
    @Data
    public static class ThreadDetailInfo {

        /** 总线程数（含已销毁线程，使用 long 避免长时间运行后溢出） */
        private long totalCount;

        /** 活跃线程数 */
        private int activeCount;

        /** 峰值线程数 */
        private int peakCount;

        /** 守护线程数 */
        private int daemonCount;

        /** 死锁线程数 */
        private int deadlockedCount;
    }
}
