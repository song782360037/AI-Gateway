import request from '../utils/request'
import type { PageResult } from '../types/common'
import type {
  ProviderConfigAddReq,
  ProviderConfigQueryReq,
  ProviderConfigRsp,
  ProviderConfigUpdateReq,
} from '../types/provider'

export function fetchProviderPage(data: ProviderConfigQueryReq) {
  return request.post<ProviderConfigQueryReq, PageResult<ProviderConfigRsp>>('/admin/provider-config/list', data)
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
