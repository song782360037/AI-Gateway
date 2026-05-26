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

/** 链路追踪候选尝试记录 */
export interface CandidateAttempt {
  /** 候选索引 */
  index: number
  /** 提供商编码 */
  providerCode: string | null
  /** 目标模型 */
  targetModel: string | null
  /** 尝试状态：SUCCESS / FAILED / CIRCUIT_OPEN / SKIPPED / STREAMING */
  status: string
  /** 错误消息 */
  errorMessage: string | null
  /** 上游HTTP状态码 */
  httpStatus: number | null
  /** 上游错误类型 */
  errorType: string | null
  /** 重试次数 */
  retryCount: number
  /** 尝试开始时间（毫秒时间戳） */
  attemptStartTime: number | null
  /** 尝试耗时（毫秒） */
  durationMs: number | null
}

/** 链路追踪详情 */
export interface TraceDetails {
  /** 最终选择的提供商编码 */
  finalProviderCode: string | null
  /** 最终选择的目标模型 */
  finalTargetModel: string | null
  /** 总尝试次数 */
  totalAttempts: number
  /** 总Failover次数 */
  totalFailovers: number
  /** 总重试次数 */
  totalRetries: number
  /** 熔断跳过次数 */
  circuitOpenSkippedCount: number
  /** 每个候选提供商的尝试记录 */
  candidateAttempts: CandidateAttempt[]
  /** Key 选择策略：ROUND_ROBIN / RANDOM / FALLBACK */
  keySelectionStrategy?: string | null
  /** Key 选择原因说明 */
  keySelectionReason?: string | null
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
  /** 提供商 API Key 脱敏标识（前8后4） */
  providerApiKeyMasked: string | null
  /** 提供商 API Key 备注（查询时 JOIN 获取） */
  providerApiKeyRemark: string | null
  candidateCount: number | null
  attemptCount: number | null
  failoverCount: number | null
  retryCount: number | null
  circuitOpenSkippedCount: number | null
  rateLimitTriggered: boolean | null
  upstreamHttpStatus: number | null
  upstreamErrorType: string | null
  terminalStage: string | null
  /** 是否开启思考 */
  thinkingEnabled: boolean | null
  /** 思考深度 */
  thinkingDepth: string | null
  /** 是否映射思考 */
  thinkingMapped: boolean | null
  /** 详细链路追踪信息（JSON格式） */
  traceDetailsJson: string | null
  /** 首token响应时间（毫秒） */
  firstTokenLatencyMs: number | null
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
