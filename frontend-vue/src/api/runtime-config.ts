import request from '../utils/request'
import type { RuntimeStatus } from '../types/runtime'

export function fetchRuntimeStatus() {
  return request.get<never, RuntimeStatus>('/admin/runtime-config/status')
}

export function reloadRuntimeConfig() {
  return request.post<never, boolean>('/admin/runtime-config/reload')
}
