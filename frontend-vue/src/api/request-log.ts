import request from '../utils/request'
import type { PageResult } from '../types/common'
import type { RequestLogQueryReq, RequestLogRsp } from '../types/request-log'

/** 分页查询请求日志 */
export function fetchRequestLogPage(data: RequestLogQueryReq) {
  return request.post<RequestLogQueryReq, PageResult<RequestLogRsp>>('/admin/request-log/list', data)
}

/** 查询请求日志详情 */
export function fetchRequestLogDetail(requestId: string) {
  return request.get<never, RequestLogRsp>(`/admin/request-log/${requestId}`)
}
