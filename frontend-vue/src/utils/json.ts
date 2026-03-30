export function formatJsonText(value?: string | null): string {
  if (!value) {
    return ''
  }

  try {
    // 尝试格式化 JSON，便于在表单中阅读和编辑。
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

export function isValidJsonText(value?: string | null): boolean {
  if (!value || !value.trim()) {
    return true
  }

  try {
    JSON.parse(value)
    return true
  } catch {
    return false
  }
}

export function normalizeJsonText(value?: string | null): string {
  if (!value || !value.trim()) {
    return ''
  }

  try {
    // 表单提交前统一压缩 JSON，避免存入大量无意义空白字符。
    return JSON.stringify(JSON.parse(value))
  } catch {
    return value.trim()
  }
}
