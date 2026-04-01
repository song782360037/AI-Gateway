export interface ApiKeyConfigQueryReq {
  name?: string
  status?: string
  page: number
  pageSize: number
}

export interface ApiKeyConfigAddReq {
  name: string
  status?: string
  dailyLimit?: number | null
  rpmLimit?: number | null
  hourlyLimit?: number | null
  totalLimit?: number | null
  expireTime?: string | null
}

export interface ApiKeyConfigUpdateReq {
  id: number
  versionNo: number
  name?: string
  status?: string
  dailyLimit?: number | null
  rpmLimit?: number | null
  hourlyLimit?: number | null
  totalLimit?: number | null
  expireTime?: string | null
}

export interface ApiKeyConfigRsp {
  id: number
  keyPrefix: string
  name: string
  status: string
  dailyLimit?: number | null
  rpmLimit?: number | null
  hourlyLimit?: number | null
  totalLimit?: number | null
  usedCount: number
  expireTime?: string | null
  versionNo: number
  createTime?: string
  updateTime?: string
}

/** 创建响应（含完整 key，仅返回一次） */
export interface ApiKeyConfigCreateRsp extends ApiKeyConfigRsp {
  apiKey: string
}
