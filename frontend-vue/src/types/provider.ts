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
  apiVersion?: string
  timeoutSeconds: number
  priority: number
  extConfigJson?: string
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
  apiVersion?: string
  timeoutSeconds: number
  priority: number
  extConfigJson?: string
}

export interface ProviderConfigRsp {
  id: number
  providerCode: string
  providerType: string
  displayName?: string
  enabled: boolean
  baseUrl: string
  apiKeyMasked?: string
  apiVersion?: string
  timeoutSeconds: number
  priority: number
  extConfigJson?: string
  versionNo: number
  createTime?: string
  updateTime?: string
}
