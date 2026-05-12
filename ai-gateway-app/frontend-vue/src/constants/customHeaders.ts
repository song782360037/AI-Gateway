/**
 * 自定义请求头相关常量。
 *
 * ⚠️ 重要：修改此文件后，必须同步更新后端
 * CustomHeaderConstants.PROTECTED_HEADERS 和 CustomHeaderConstants.VALID_HEADER_NAME_REGEX，
 * 否则前端校验与后端不一致，用户可能提交被后端拒绝的头。
 */

/** 受保护的认证相关头（小写），不允许在自定义头中设置 */
export const PROTECTED_HEADERS: ReadonlySet<string> = new Set([
  'authorization',
  'x-api-key',
  'x-goog-api-key',
  'anthropic-version',
])

/** 合法的 HTTP header name 正则：允许字母、数字、'-'、'_'、'.' */
export const VALID_HEADER_NAME_REGEX = /^[a-zA-Z0-9\-_.]+$/

/** 值中不允许包含换行符（防止 HTTP 头注入） */
export function hasCrlf(value: string): boolean {
  return value.includes('\r') || value.includes('\n')
}
