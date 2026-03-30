import request from '../utils/request'
import type { DashboardStats, ModelUsageRank, RecentRequest } from '../types/dashboard'

/**
 * 获取仪表盘统计概览
 */
export function fetchDashboardStats() {
  return request.get<never, DashboardStats>('/admin/dashboard/overview')
}

/**
 * 获取模型调用排行 Top 10
 */
export function fetchModelUsageRank() {
  return request.get<never, ModelUsageRank[]>('/admin/dashboard/model-rank')
}

/**
 * 获取最近请求记录
 */
export function fetchRecentRequests() {
  return request.get<never, RecentRequest[]>('/admin/dashboard/recent-requests')
}
