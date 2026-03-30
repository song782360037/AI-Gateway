/**
 * 仪表盘统计概览
 */
export interface DashboardStats {
  /** 请求数 */
  requests: DualMetric
  /** 消费金额（USD） */
  cost: DualMetric
  /** Token 消耗 */
  tokens: DualMetric
  /** 每分钟 Token 数 */
  tpm: number
  /** 每分钟请求数 */
  rpm: number
  /** 平均响应时间（ms） */
  avgResponseMs: number
}

/** 今日 / 累计双维度指标 */
export interface DualMetric {
  today: number
  total: number
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
