export interface ModelRedirectConfigQueryReq {
  aliasName?: string
  providerCode?: string
  targetModel?: string
  enabled?: boolean
  page: number
  pageSize: number
}

export interface ModelRedirectConfigAddReq {
  aliasName: string
  providerCode: string
  targetModel: string
  enabled: boolean
}

export interface ModelRedirectConfigUpdateReq {
  id: number
  versionNo: number
  aliasName: string
  providerCode: string
  targetModel: string
  enabled: boolean
}

export interface ModelRedirectConfigRsp {
  id: number
  aliasName: string
  providerCode: string
  targetModel: string
  enabled: boolean
  versionNo: number
  createTime?: string
  updateTime?: string
}
