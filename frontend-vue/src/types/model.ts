export interface ModelRedirectConfigQueryReq {
  aliasName?: string
  providerCode?: string
  targetModel?: string
  enabled?: boolean
  page: number
  pageSize: number
}

/** 模型路由匹配类型 */
export type MatchType = 'EXACT' | 'GLOB' | 'REGEX'

export interface ModelRedirectConfigAddReq {
  aliasName: string
  matchType: MatchType
  providerCode: string
  targetModel: string
  enabled: boolean
}

export interface ModelRedirectConfigUpdateReq {
  id: number
  versionNo: number
  aliasName: string
  matchType: MatchType
  providerCode: string
  targetModel: string
  enabled: boolean
}

export interface ModelRedirectConfigRsp {
  id: number
  aliasName: string
  matchType: MatchType
  providerCode: string
  targetModel: string
  enabled: boolean
  versionNo: number
  createTime?: string
  updateTime?: string
}
