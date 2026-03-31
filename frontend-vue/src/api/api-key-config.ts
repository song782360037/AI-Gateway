import request from '../utils/request'
import type { PageResult } from '../types/common'
import type {
  ApiKeyConfigAddReq,
  ApiKeyConfigCreateRsp,
  ApiKeyConfigQueryReq,
  ApiKeyConfigRsp,
  ApiKeyConfigUpdateReq,
} from '../types/api-key-config'

export function fetchApiKeyPage(data: ApiKeyConfigQueryReq) {
  return request.post<ApiKeyConfigQueryReq, PageResult<ApiKeyConfigRsp>>('/admin/api-key-config/list', data)
}

export function addApiKey(data: ApiKeyConfigAddReq) {
  return request.post<ApiKeyConfigAddReq, ApiKeyConfigCreateRsp>('/admin/api-key-config/add', data)
}

export function updateApiKey(data: ApiKeyConfigUpdateReq) {
  return request.post<ApiKeyConfigUpdateReq, void>('/admin/api-key-config/update', data)
}

export function deleteApiKey(id: number) {
  return request.post<never, void>(`/admin/api-key-config/delete/${id}`)
}
