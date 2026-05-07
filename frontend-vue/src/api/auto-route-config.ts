import request from '../utils/request'
import type { PageResult } from '../types/common'
import type {
  AutoRouteCandidateAddReq,
  AutoRouteCandidateUpdateReq,
  AutoRouteConfigAddReq,
  AutoRouteConfigQueryReq,
  AutoRouteConfigRsp,
  AutoRouteConfigUpdateReq,
  AutoRouteIdVersionReq,
} from '../types/auto-route'

export function fetchAutoRoutePage(data: AutoRouteConfigQueryReq) {
  return request.post<AutoRouteConfigQueryReq, PageResult<AutoRouteConfigRsp>>(
    '/admin/auto-route-config/list',
    data,
  )
}

export function addAutoRouteConfig(data: AutoRouteConfigAddReq) {
  return request.post<AutoRouteConfigAddReq, number>('/admin/auto-route-config/add', data)
}

export function updateAutoRouteConfig(data: AutoRouteConfigUpdateReq) {
  return request.post<AutoRouteConfigUpdateReq, void>('/admin/auto-route-config/update', data)
}

export function toggleAutoRouteConfig(data: AutoRouteIdVersionReq) {
  return request.post<AutoRouteIdVersionReq, void>('/admin/auto-route-config/toggle', data)
}

export function deleteAutoRouteConfig(data: AutoRouteIdVersionReq) {
  return request.post<AutoRouteIdVersionReq, void>('/admin/auto-route-config/delete', data)
}

export function getAutoRouteConfig(id: number) {
  return request.get<never, AutoRouteConfigRsp>(`/admin/auto-route-config/${id}`)
}

export function addAutoRouteCandidate(data: AutoRouteCandidateAddReq) {
  return request.post<AutoRouteCandidateAddReq, number>(
    '/admin/auto-route-config/candidate/add',
    data,
  )
}

export function updateAutoRouteCandidate(data: AutoRouteCandidateUpdateReq) {
  return request.post<AutoRouteCandidateUpdateReq, void>(
    '/admin/auto-route-config/candidate/update',
    data,
  )
}

export function toggleAutoRouteCandidate(data: AutoRouteIdVersionReq) {
  return request.post<AutoRouteIdVersionReq, void>(
    '/admin/auto-route-config/candidate/toggle',
    data,
  )
}

export function deleteAutoRouteCandidate(data: AutoRouteIdVersionReq) {
  return request.post<AutoRouteIdVersionReq, void>(
    '/admin/auto-route-config/candidate/delete',
    data,
  )
}
