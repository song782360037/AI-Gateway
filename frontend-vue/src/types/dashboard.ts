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
  /** 平均响应时间（ms） */
  avgResponseMs: DualMetric
}

/** 模型调用排行 */
export interface ModelUsageRank {
  rank: number
  modelName: string
  callCount: number
  tokenCount: number
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
