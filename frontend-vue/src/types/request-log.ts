/** 请求日志分页查询参数 */
export interface RequestLogQueryReq {
  startTime?: string
  endTime?: string
  /** 提供商类型：OPENAI / ANTHROPIC / GEMINI / OPENAI_RESPONSES */
  providerType?: string
  /** 请求状态：SUCCESS / ERROR / CANCELLED */
  status?: string
  /** 模型别名（模糊匹配） */
  aliasModel?: string
  /** 请求唯一标识 */
  requestId?: string
  page: number
  pageSize: number
}

/** 请求日志响应 */
export interface RequestLogRsp {
  id: number
  requestId: string
  aliasModel: string
  targetModel: string
  providerCode: string
  providerType: string
  isStream: boolean
  promptTokens: number | null
  cachedInputTokens: number | null
  completionTokens: number | null
  totalTokens: number | null
  durationMs: number | null
  status: string
  errorCode: string | null
  errorMessage: string | null
  sourceIp: string | null
  createTime: string
}
