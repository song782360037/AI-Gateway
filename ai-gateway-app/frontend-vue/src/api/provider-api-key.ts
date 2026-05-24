import request from '../utils/request'
import type { ProviderApiKeyAddReq, ProviderApiKeyRsp, ProviderApiKeyUpdateReq } from '../types/provider'

/** 查询指定提供商下所有 API Key */
export function fetchApiKeys(providerCode: string) {
  return request.post<never, ProviderApiKeyRsp[]>('/admin/provider-api-key/list', { providerCode })
}

/** 新增 API Key */
export function addApiKey(data: ProviderApiKeyAddReq) {
  return request.post<ProviderApiKeyAddReq, void>('/admin/provider-api-key/add', data)
}

/** 更新 API Key（备注/权重/排序） */
export function updateApiKey(data: ProviderApiKeyUpdateReq) {
  return request.post<ProviderApiKeyUpdateReq, void>('/admin/provider-api-key/update', data)
}

/** 删除 API Key */
export function deleteApiKey(id: number) {
  return request.post<never, void>(`/admin/provider-api-key/delete/${id}`)
}

/** 切换 API Key 启用/禁用状态 */
export function toggleApiKey(id: number, versionNo: number, enabled: boolean) {
  return request.post<never, void>('/admin/provider-api-key/toggle', null, {
    params: { id, versionNo, enabled },
  })
}
