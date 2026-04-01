import request from '../utils/request'
import type { PageResult } from '../types/common'
import type { RequestLogQueryReq, RequestLogRsp } from '../types/request-log'

/** 分页查询请求日志 */
export function fetchRequestLogPage(data: RequestLogQueryReq) {
  return request.post<RequestLogQueryReq, PageResult<RequestLogRsp>>('/admin/request-log/list', data)
}
