/** 时间范围类型 */
export type DashboardPeriod = 'today' | '7d' | '30d'

/** 双维度指标：当前周期 / 上一周期 + 环比变化 */
export interface DualMetric {
  /** 当前周期值 */
  current: number
  /** 上一周期值 */
  previous: number
  /** 环比变化百分比，如 +12.5 表示增长 12.5% */
  changePercent: number
}

/** 仪表盘统计概览 */
export interface DashboardStats {
  /** 请求数 */
  requests: DualMetric
  /** 消费金额（USD） */
  cost: DualMetric
  /** Token 消耗 */
  tokens: DualMetric
  /** 缓存命中 Token 数 */
  cacheTokens: DualMetric
  /** 平均响应时间（ms） */
  avgResponseMs: DualMetric
  /** 请求成功率（百分比） */
  successRate: DualMetric

  /** 接入通道（提供商）数量 */
  providerCount: number

  /** 模型重定向规则数量 */
  redirectCount: number
}

/** 模型调用排行 */
export interface ModelUsageRank {
  rank: number
  modelName: string
  targetModel: string
  callCount: number
  tokenCount: number
  cachedTokens: number
  cacheSavedCost: number
  cost: number
}

/** 最近请求记录 */
export interface RecentRequest {
  time: string
  model: string
  provider: string
  tokens: number
  duration: number
  status: 'success' | 'error'
}

/** 系统健康状态 */
export interface SystemHealth {
  status: 'UP' | 'DOWN'
}

/** 趋势数据 */
export interface DashboardTrend {
  /** 时间标签列表 */
  labels: string[]
  /** 请求数序列 */
  requestCounts: number[]
  /** Token 消耗序列 */
  tokenCounts: number[]
  /** 费用序列（USD） */
  costs: number[]
  /** 成功率序列（百分比） */
  successRates: number[]
  /** 缓存命中率序列（百分比） */
  cacheHitRates: number[]
}

/** 提供商分布单项 */
export interface ProviderDistributionItem {
  providerCode: string
  requestCount: number
  tokenCount: number
  cost: number
  percent: number
}

/** 提供商分布 */
export interface ProviderDistribution {
  items: ProviderDistributionItem[]
}

/** 错误摘要单项 */
export interface ErrorSummaryItem {
  errorCode: string
  errorCount: number
  percent: number
}

/** 错误摘要 */
export interface ErrorSummary {
  totalErrors: number
  items: ErrorSummaryItem[]
}

/** 实时指标 */
export interface RealtimeMetrics {
  /** 最近 1 分钟请求数（RPM） */
  rpm: number
  /** 最近 1 分钟 Token 数（TPM） */
  tpm: number
  /** 最近 1 分钟成功率（百分比） */
  successRate: number
  /** 活跃通道数 */
  activeProviders: number
  /** 当前正在请求的总数 */
  activeRequestCount: number
  /** 当前正在请求的唯一客户端数量 */
  activeClientCount: number
  /** 当前活跃请求按提供商+模型分组 */
  activeRequestGroups: ActiveRequestGroup[]
}

/** 活跃请求分组信息 */
export interface ActiveRequestGroup {
  /** 提供商编码 */
  providerCode: string
  /** 目标模型 */
  targetModel: string
  /** 该分组的请求数量 */
  count: number
}
