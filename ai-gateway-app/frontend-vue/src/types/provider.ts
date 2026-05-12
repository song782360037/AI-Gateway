export interface ProviderConfigQueryReq {
  providerCode?: string
  providerType?: string
  enabled?: boolean
  page: number
  pageSize: number
}

export interface ProviderConfigAddReq {
  providerCode: string
  providerType: string
  displayName?: string
  enabled: boolean
  baseUrl: string
  apiKey: string
  timeoutSeconds: number
  priority: number
  supportedProtocols?: string[]
  customHeaders?: Record<string, string>
}

export interface ProviderConfigUpdateReq {
  id: number
  versionNo: number
  providerCode: string
  providerType: string
  displayName?: string
  enabled: boolean
  baseUrl: string
  apiKey?: string
  timeoutSeconds: number
  priority: number
  supportedProtocols?: string[]
  customHeaders?: Record<string, string>
}

export interface ProviderConfigRsp {
  id: number
  providerCode: string
  providerType: string
  displayName?: string
  enabled: boolean
  baseUrl: string
  apiKeyMasked?: string
  timeoutSeconds: number
  priority: number
  supportedProtocols?: string[]
  customHeaders?: Record<string, string>
  versionNo: number
  createTime?: string
  updateTime?: string
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
