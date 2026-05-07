import request from '../utils/request'
import type { PageResult } from '../types/common'
import type {
  SupportedModelAddReq,
  SupportedModelQueryReq,
  SupportedModelRsp,
  SupportedModelUpdateReq,
} from '../types/supported-model'

export function fetchSupportedModelPage(data: SupportedModelQueryReq) {
  return request.post<SupportedModelQueryReq, PageResult<SupportedModelRsp>>(
    '/admin/supported-model/list',
    data,
  )
}

export function addSupportedModel(data: SupportedModelAddReq) {
  return request.post<SupportedModelAddReq, number>('/admin/supported-model/add', data)
}

export function updateSupportedModel(data: SupportedModelUpdateReq) {
  return request.post<SupportedModelUpdateReq, void>('/admin/supported-model/update', data)
}

export function deleteSupportedModel(id: number, versionNo: number) {
  return request.post<never, void>('/admin/supported-model/delete', { id, versionNo })
}

export function toggleSupportedModel(id: number, versionNo: number) {
  return request.post<never, void>('/admin/supported-model/toggle', { id, versionNo })
}

/** 从路由别名同步导入模型 */
export function syncFromRedirect() {
  return request.post<never, number>('/admin/supported-model/sync-from-redirect')
}
