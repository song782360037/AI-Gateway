import request from '../utils/request'
import type { PageResult } from '../types/common'
import type {
  ModelRedirectConfigAddReq,
  ModelRedirectConfigQueryReq,
  ModelRedirectConfigRsp,
  ModelRedirectConfigUpdateReq,
} from '../types/model'

export function fetchModelRedirectPage(data: ModelRedirectConfigQueryReq) {
  return request.post<ModelRedirectConfigQueryReq, PageResult<ModelRedirectConfigRsp>>('/admin/model-redirect-config/list', data)
}

export function addModelRedirect(data: ModelRedirectConfigAddReq) {
  return request.post<ModelRedirectConfigAddReq, number>('/admin/model-redirect-config/add', data)
}

export function updateModelRedirect(data: ModelRedirectConfigUpdateReq) {
  return request.post<ModelRedirectConfigUpdateReq, void>('/admin/model-redirect-config/update', data)
}

export function deleteModelRedirect(id: number) {
  return request.post<never, void>(`/admin/model-redirect-config/delete/${id}`)
}

/** 切换路由规则启用/禁用状态 */
export function toggleModelRedirect(id: number, versionNo: number) {
  return request.post<never, void>('/admin/model-redirect-config/toggle', { id, versionNo })
}
