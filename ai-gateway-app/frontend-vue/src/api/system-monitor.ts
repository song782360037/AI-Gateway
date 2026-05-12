import request from '../utils/request'
import type { SystemOverview, JvmInfo, SystemRealtime } from '../types/system-monitor'

/** 获取系统概览信息 */
export function fetchSystemOverview() {
  return request.get<never, SystemOverview>('/admin/system-monitor/overview')
}

/** 获取 JVM 详细信息 */
export function fetchJvmInfo() {
  return request.get<never, JvmInfo>('/admin/system-monitor/jvm')
}

/** 获取系统实时指标 */
export function fetchSystemRealtime() {
  return request.get<never, SystemRealtime>('/admin/system-monitor/realtime')
}
