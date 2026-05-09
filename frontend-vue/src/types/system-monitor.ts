/** 系统监控 - 概览数据 */
export interface SystemOverview {
  /** 系统 CPU 使用率（百分比） */
  systemCpuUsage: number
  /** 进程 CPU 使用率（百分比） */
  processCpuUsage: number
  /** 物理内存总量（字节） */
  totalPhysicalMemory: number
  /** 已用物理内存（字节） */
  usedPhysicalMemory: number
  /** JVM 堆内存已用（字节） */
  jvmUsedHeap: number
  /** JVM 堆内存最大（字节） */
  jvmMaxHeap: number
  /** JVM 非堆内存已用（字节） */
  jvmUsedNonHeap: number
  /** 总线程数 */
  totalThreadCount: number
  /** 活跃线程数 */
  activeThreadCount: number
  /** 峰值线程数 */
  peakThreadCount: number
  /** 守护线程数 */
  daemonThreadCount: number
  /** 应用启动时间（毫秒时间戳） */
  startupTime: number
  /** 应用运行时长（秒） */
  uptimeSeconds: number
  /** 连接池信息 */
  connectionPool: ConnectionPoolInfo
}

/** 连接池信息 */
export interface ConnectionPoolInfo {
  /** 活跃连接数 */
  activeConnections: number
  /** 空闲连接数 */
  idleConnections: number
  /** 最大连接数 */
  maxConnections: number
  /** 待等待连接数 */
  pendingConnections: number
}

/** JVM 内存信息 */
export interface MemoryInfo {
  /** 已用内存（字节） */
  used: number
  /** 最大可用内存（字节） */
  max: number
  /** 已提交内存（字节） */
  committed: number
  /** 使用率（百分比） */
  usagePercent: number
}

/** GC 信息 */
export interface GcInfo {
  /** GC 名称 */
  name: string
  /** GC 总次数 */
  count: number
  /** GC 总耗时（毫秒） */
  totalTimeMs: number
}

/** 线程详细信息 */
export interface ThreadDetailInfo {
  /** 总线程数 */
  totalCount: number
  /** 活跃线程数 */
  activeCount: number
  /** 峰值线程数 */
  peakCount: number
  /** 守护线程数 */
  daemonCount: number
  /** 死锁线程数 */
  deadlockedCount: number
}

/** 系统监控 - JVM 详情 */
export interface JvmInfo {
  /** JVM 名称 */
  jvmName: string
  /** JVM 版本 */
  jvmVersion: string
  /** JVM 供应商 */
  jvmVendor: string
  /** JVM 启动参数 */
  vmArguments: string
  /** 堆内存信息 */
  heapMemory: MemoryInfo
  /** 非堆内存信息 */
  nonHeapMemory: MemoryInfo
  /** GC 信息列表 */
  gcInfos: GcInfo[]
  /** 已加载类数量 */
  loadedClassCount: number
  /** 类加载总数 */
  totalLoadedClassCount: number
  /** 已卸载类数量 */
  unloadedClassCount: number
  /** 线程详细信息 */
  threadDetail: ThreadDetailInfo
}

/** 系统监控 - 实时指标 */
export interface SystemRealtime {
  /** 系统 CPU 使用率（百分比） */
  systemCpuUsage: number
  /** 进程 CPU 使用率（百分比） */
  processCpuUsage: number
  /** JVM 堆内存使用率（百分比） */
  jvmHeapUsage: number
  /** JVM 已用堆内存（字节） */
  jvmUsedHeap: number
  /** 活跃线程数 */
  activeThreadCount: number
  /** 活跃连接数 */
  activeConnections: number
  /** 当前时间戳（毫秒） */
  timestamp: number
}
