import request from '../utils/request'
import type { PageResult } from '../types/common'
import type {
  ConnectionTestResult,
  GlobalCustomHeadersRsp,
  GlobalCustomHeadersUpdateReq,
  ProviderConfigAddReq,
  ProviderConfigQueryReq,
  ProviderConfigRsp,
  ProviderConfigUpdateReq,
} from '../types/provider'

export function fetchProviderPage(data: ProviderConfigQueryReq) {
  return request.post<ProviderConfigQueryReq, PageResult<ProviderConfigRsp>>(
    '/admin/provider-config/list',
    data,
  )
}

export function addProvider(data: ProviderConfigAddReq) {
  return request.post<ProviderConfigAddReq, number>('/admin/provider-config/add', data)
}

export function updateProvider(data: ProviderConfigUpdateReq) {
  return request.post<ProviderConfigUpdateReq, void>('/admin/provider-config/update', data)
}

export function deleteProvider(id: number) {
  return request.post<never, void>(`/admin/provider-config/delete/${id}`)
}

export function toggleProvider(id: number, versionNo: number) {
  return request.post<never, void>('/admin/provider-config/toggle', { id, versionNo })
}

/** 批量更新优先级的单项参数 */
export interface PriorityUpdateItem {
  id: number
  versionNo: number
  priority: number
}

export function batchUpdatePriority(items: PriorityUpdateItem[]) {
  return request.post<never, void>('/admin/provider-config/batch-update-priority', { items })
}

/** 测试提供商连接 */
export function testConnection(id: number) {
  return request.post<never, ConnectionTestResult>(`/admin/provider-config/test-connection/${id}`)
}

/** 查询上游提供商的可用模型列表 */
export function fetchUpstreamModels(providerCode: string) {
  return request.post<never, string[]>(`/admin/provider-config/${providerCode}/upstream-models`)
}

/** 获取全局自定义请求头 */
export function fetchGlobalCustomHeaders() {
  return request.get<never, GlobalCustomHeadersRsp>('/admin/global-config/custom-headers')
}

/** 更新全局自定义请求头 */
export function updateGlobalCustomHeaders(data: GlobalCustomHeadersUpdateReq) {
  return request.post<GlobalCustomHeadersUpdateReq, void>('/admin/global-config/custom-headers', data)
}
