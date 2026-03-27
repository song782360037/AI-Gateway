export interface RuntimeStatus {
  hasSnapshot: boolean
  dirty: boolean
  version?: number
  source?: string
  createdAt?: string
  aliasCount?: number
  providerCount?: number
}
