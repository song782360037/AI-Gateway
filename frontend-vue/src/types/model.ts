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
  priority: number
  routeStrategy: string
  weight: number
  matchConditionJson?: string
  extConfigJson?: string
}

export interface ModelRedirectConfigUpdateReq {
  id: number
  versionNo: number
  aliasName: string
  providerCode: string
  targetModel: string
  enabled: boolean
  priority: number
  routeStrategy: string
  weight: number
  matchConditionJson?: string
  extConfigJson?: string
}

export interface ModelRedirectConfigRsp {
  id: number
  aliasName: string
  providerCode: string
  targetModel: string
  enabled: boolean
  priority: number
  routeStrategy: string
  weight: number
  matchConditionJson?: string
  extConfigJson?: string
  versionNo: number
  createTime?: string
  updateTime?: string
}
