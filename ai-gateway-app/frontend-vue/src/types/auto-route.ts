export interface AutoRouteConfigQueryReq {
  routeKey?: string
  displayName?: string
  enabled?: boolean
  page: number
  pageSize: number
}

export interface AutoRouteConfigAddReq {
  routeKey: string
  displayName: string
  description?: string
  enabled: boolean
  selectionStrategy: string
}

export interface AutoRouteConfigUpdateReq extends AutoRouteConfigAddReq {
  id: number
  versionNo: number
}

export interface AutoRouteIdVersionReq {
  id: number
  versionNo: number
}

export interface AutoRouteConfigRsp {
  id: number
  routeKey: string
  displayName: string
  description?: string
  enabled: boolean
  selectionStrategy: string
  candidateCount: number
  versionNo: number
  createTime?: string
  updateTime?: string
  candidates?: AutoRouteCandidateRsp[]
}

interface AutoRouteCandidateSmartFields {
  supportsVision: boolean
  supportsTools: boolean
  supportsToolChoiceRequired: boolean
  supportsReasoning: boolean
  supportsJson: boolean
  supportsStream: boolean
  maxInputTokens?: number
  maxOutputTokens?: number
  qualityScore: number
  latencyScore: number
  costScore: number
  toolScore: number
  visionScore: number
  reasoningScore: number
  reliabilityScore: number
  scoreBias: number
}

export interface AutoRouteCandidateAddReq extends AutoRouteCandidateSmartFields {
  configId: number
  providerCode: string
  targetModel: string
  priority: number
  weight: number
  enabled: boolean
  description?: string
}

export interface AutoRouteCandidateUpdateReq extends AutoRouteCandidateSmartFields {
  id: number
  versionNo: number
  providerCode: string
  targetModel: string
  priority: number
  weight: number
  enabled: boolean
  description?: string
}

export interface AutoRouteCandidateRsp extends AutoRouteCandidateSmartFields {
  id: number
  configId: number
  providerCode: string
  targetModel: string
  priority: number
  weight: number
  enabled: boolean
  description?: string
  versionNo: number
  createTime?: string
  updateTime?: string
}
