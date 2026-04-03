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
  versionNo: number
  createTime?: string
  updateTime?: string
}
