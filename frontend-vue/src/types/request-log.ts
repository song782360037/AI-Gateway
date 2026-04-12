/** 请求日志分页查询参数 */
export interface RequestLogQueryReq {
  startTime?: string
  endTime?: string
  /** 提供商类型：OPENAI / ANTHROPIC / GEMINI / OPENAI_RESPONSES */
  providerType?: string
  /** 提供商编码 */
  providerCode?: string
  /** 请求状态：SUCCESS / ERROR / CANCELLED / REJECTED */
  status?: string
  /** 模型别名（模糊匹配） */
  aliasModel?: string
  /** 请求唯一标识 */
  requestId?: string
  /** 是否流式 */
  isStream?: boolean
  /** 是否发生重试 */
  hasRetry?: boolean
  /** 是否发生 Failover */
  hasFailover?: boolean
  page: number
  pageSize: number
}

/** 请求日志响应 */
export interface RequestLogRsp {
  id: number
  requestId: string
  aliasModel: string | null
  targetModel: string | null
  providerCode: string | null
  providerType: string | null
  responseProtocol: string | null
  requestPath: string | null
  httpMethod: string | null
  apiKeyPrefix: string | null
  candidateCount: number | null
  attemptCount: number | null
  failoverCount: number | null
  retryCount: number | null
  circuitOpenSkippedCount: number | null
  rateLimitTriggered: boolean | null
  upstreamHttpStatus: number | null
  upstreamErrorType: string | null
  terminalStage: string | null
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
