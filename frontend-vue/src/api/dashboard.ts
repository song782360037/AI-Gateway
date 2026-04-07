import request from '../utils/request'
import type { DashboardPeriod, DashboardStats, ModelUsageRank, RecentRequest, SystemHealth } from '../types/dashboard'

/** 获取仪表盘统计概览 */
export function fetchDashboardStats(period: DashboardPeriod) {
  return request.get<never, DashboardStats>('/admin/dashboard/overview', { params: { period } })
}

/** 获取模型调用排行 Top 10 */
export function fetchModelUsageRank(period: DashboardPeriod) {
  return request.get<never, ModelUsageRank[]>('/admin/dashboard/model-rank', { params: { period } })
}

/** 获取最近请求记录 */
export function fetchRecentRequests(period: DashboardPeriod) {
  return request.get<never, RecentRequest[]>('/admin/dashboard/recent-requests', { params: { period } })
}

/** 系统健康检测 */
export function fetchSystemHealth() {
  return request.get<never, SystemHealth>('/admin/dashboard/health')
}
