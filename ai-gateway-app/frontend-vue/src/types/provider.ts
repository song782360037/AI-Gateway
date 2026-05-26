export interface ProviderConfigQueryReq {
  providerCode?: string
  providerType?: string
  enabled?: boolean
  page: number
  pageSize: number
}

/** thinking 参数兼容模式：full=完整官方参数，simplified=仅输出 type 字段 */
export type ThinkingCompatMode = 'full' | 'simplified'

/** Key 选择策略：ROUND_ROBIN=轮询，RANDOM=加权随机（按 weight 字段），FALLBACK=按排序号降级 */
export type KeySelectionStrategy = 'ROUND_ROBIN' | 'RANDOM' | 'FALLBACK'

export interface ProviderConfigAddReq {
  providerCode: string
  providerType: string
  displayName?: string
  enabled: boolean
  baseUrl: string
  keySelectionStrategy?: KeySelectionStrategy
  timeoutSeconds: number
  priority: number
  supportedProtocols?: string[]
  customHeaders?: Record<string, string>
  thinkingCompatMode?: ThinkingCompatMode
  /** 新增时一并添加的 API Key 列表 */
  apiKeys?: ProviderApiKeyAddReq[]
}

export interface ProviderConfigUpdateReq {
  id: number
  versionNo: number
  providerCode: string
  providerType: string
  displayName?: string
  enabled: boolean
  baseUrl: string
  keySelectionStrategy?: KeySelectionStrategy
  timeoutSeconds: number
  priority: number
  supportedProtocols?: string[]
  customHeaders?: Record<string, string>
  thinkingCompatMode?: ThinkingCompatMode
}

export interface ProviderConfigRsp {
  id: number
  providerCode: string
  providerType: string
  displayName?: string
  enabled: boolean
  baseUrl: string
  keySelectionStrategy?: string
  apiKeyCount?: number
  timeoutSeconds: number
  priority: number
  supportedProtocols?: string[]
  customHeaders?: Record<string, string>
  thinkingCompatMode?: ThinkingCompatMode
  versionNo: number
  createTime?: string
  updateTime?: string
}

/** 提供商 API Key 响应 */
export interface ProviderApiKeyRsp {
  id: number
  providerCode: string
  apiKeyMasked?: string
  remark?: string
  enabled: boolean
  weight: number
  sortOrder: number
  versionNo: number
  createTime?: string
  updateTime?: string
}

/** 提供商 API Key 新增请求 */
export interface ProviderApiKeyAddReq {
  providerCode: string
  apiKey: string
  remark?: string
  enabled?: boolean
  weight?: number
  sortOrder?: number
}

/** 提供商 API Key 更新请求 */
export interface ProviderApiKeyUpdateReq {
  id: number
  versionNo: number
  remark?: string
  enabled?: boolean
  weight?: number
  sortOrder?: number
}

/** 提供商连接测试结果 */
export interface ConnectionTestResult {
  success: boolean
  latencyMs: number
  errorMessage?: string
  errorType?: string
}

/** 全局自定义请求头响应 */
export interface GlobalCustomHeadersRsp {
  customHeaders: Record<string, string>
  versionNo: number
}

/** 全局自定义请求头更新请求 */
export interface GlobalCustomHeadersUpdateReq {
  versionNo: number
  customHeaders?: Record<string, string>
}
