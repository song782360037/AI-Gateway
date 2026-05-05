export interface SupportedModelQueryReq {
  modelId?: string
  displayName?: string
  ownedBy?: string
  enabled?: boolean
  page: number
  pageSize: number
}

export interface SupportedModelAddReq {
  modelId: string
  displayName: string
  ownedBy?: string
  enabled: boolean
  sortOrder: number
}

export interface SupportedModelUpdateReq {
  id: number
  versionNo: number
  modelId: string
  displayName: string
  ownedBy?: string
  enabled: boolean
  sortOrder: number
}

export interface SupportedModelRsp {
  id: number
  modelId: string
  displayName: string
  ownedBy: string
  enabled: boolean
  sortOrder: number
  versionNo: number
  createTime?: string
  updateTime?: string
}
