/**
 * 请求日志相关的常量映射和工具函数
 */

// 提供商标签映射
export const providerLabelMap: Record<string, string> = {
  OPENAI: 'OpenAI',
  OPENAI_RESPONSES: 'OpenAI Responses',
  ANTHROPIC: 'Anthropic',
  GEMINI: 'Gemini',
}

// 提供商标签类型映射
export const providerTagTypeMap: Record<string, string> = {
  OPENAI: 'primary',
  OPENAI_RESPONSES: '',
  ANTHROPIC: 'warning',
  GEMINI: 'success',
}

// 状态标签映射
export const statusLabelMap: Record<string, string> = {
  SUCCESS: '成功',
  ERROR: '失败',
  CANCELLED: '已取消',
  REJECTED: '已拒绝',
}

// 状态标签类型映射
export const statusTagTypeMap: Record<string, string> = {
  SUCCESS: 'success',
  ERROR: 'danger',
  CANCELLED: 'warning',
  REJECTED: 'info',
}

// 终止阶段映射
export const terminalStageLabelMap: Record<string, string> = {
  AUTH: '鉴权',
  RATE_LIMIT: '限流',
  ROUTING: '路由',
  FAILOVER: 'Failover',
  UPSTREAM: '上游调用',
  STREAMING: '流式输出',
}

/**
 * 获取提供商显示名称
 */
export function providerLabel(type?: string | null): string {
  if (!type) return '-'
  return providerLabelMap[type] ?? type
}

/**
 * 获取提供商标签类型
 */
export function providerTagType(type?: string | null): string {
  if (!type) return 'info'
  return providerTagTypeMap[type] ?? 'info'
}

/**
 * 获取状态显示名称
 */
export function statusLabel(status?: string | null): string {
  if (!status) return '-'
  return statusLabelMap[status] ?? status
}

/**
 * 获取状态标签类型
 */
export function statusTagType(status?: string | null): string {
  if (!status) return 'info'
  return statusTagTypeMap[status] ?? 'info'
}

/**
 * 获取终止阶段显示名称
 */
export function terminalStageLabel(stage?: string | null): string {
  if (!stage) return '-'
  return terminalStageLabelMap[stage] ?? stage
}

/**
 * 格式化耗时（毫秒）
 */
export function formatDuration(ms: number): string {
  if (ms >= 1000) {
    return `${(ms / 1000).toFixed(1)}s`
  }
  return `${ms}ms`
}

/**
 * 格式化可空的耗时
 */
export function formatNullableDuration(ms?: number | null): string {
  if (ms === null || ms === undefined) return '-'
  return formatDuration(ms)
}

/**
 * 格式化时间字符串
 */
export function formatTime(dateTime?: string | null): string {
  if (!dateTime) return '-'
  return dateTime.replace('T', ' ').replace(/\.\d{1,3}$/, '')
}

/**
 * 格式化可空的数字
 */
export function formatNullableNumber(value?: number | null): string {
  if (value === null || value === undefined) return '-'
  return value.toLocaleString()
}

/**
 * 布尔值转标签
 */
export function booleanLabel(value?: boolean | null): string {
  if (value === null || value === undefined) return '-'
  return value ? '是' : '否'
}

/**
 * IP 脱敏显示
 */
export function maskIp(ip?: string | null): string {
  if (!ip) return '-'
  if (ip.includes(':')) {
    const segments = ip.split(':').filter(Boolean)
    if (segments.length <= 2) return ip
    return `${segments[0]}:${segments[1]}::****`
  }
  const segments = ip.split('.')
  if (segments.length !== 4) return ip
  return `${segments[0]}.${segments[1]}.*.*`
}

/**
 * 判断是否为错误状态
 */
export function isErrorStatus(status?: string | null): boolean {
  return status === 'ERROR' || status === 'REJECTED'
}

/**
 * 将 budget_tokens 数值映射为 effort 等级
 * 参照 Anthropic adaptive thinking 的 effort 语义划分：
 *   low: < 5000    — 浅度思考
 *   medium: 5K~19K  — 中度思考
 *   high: 20K~79K   — 深度思考
 *   xhigh: >= 80K   — 极深度思考
 */
function budgetTokensToEffort(tokens: number): string {
  if (tokens < 5000) return 'low'
  if (tokens < 20000) return 'medium'
  if (tokens < 80000) return 'high'
  return 'xhigh'
}

/**
 * 格式化思考深度
 * - 纯数字（budgetTokens）：换算为 low/medium/high/xhigh 等级，并附原始值
 * - 非数字（effort 如 high/medium/low）：原样展示
 */
export function formatThinkingDepth(depth?: string | null): string {
  if (!depth) return '-'
  const num = Number(depth)
  if (!isNaN(num) && depth.trim() !== '') {
    const effort = budgetTokensToEffort(num)
    return `${effort} (${num.toLocaleString()})`
  }
  return depth
}

/**
 * 判断是否有治理信号（重试、failover、熔断跳过）
 */
export function hasGovernanceSignals(row: { retryCount?: number | null; failoverCount?: number | null; circuitOpenSkippedCount?: number | null }): boolean {
  return Boolean(
    (row.retryCount ?? 0) > 0 || (row.failoverCount ?? 0) > 0 || (row.circuitOpenSkippedCount ?? 0) > 0,
  )
}
