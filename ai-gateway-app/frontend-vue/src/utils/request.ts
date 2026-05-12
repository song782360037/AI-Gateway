import axios, { AxiosHeaders } from 'axios'
import { ElMessage } from 'element-plus'
import type { InternalAxiosRequestConfig } from 'axios'
import type { ApiResponse } from '../types/common'
import { markAuthRefreshRequired } from './auth-state'

const request = axios.create({
  // 统一从环境变量读取基础路径，开发和生产都保持同一调用方式。
  baseURL: import.meta.env.VITE_API_BASE,
  timeout: 15000,
  withCredentials: true,
})

const CSRF_COOKIE_NAME = 'AI_GATEWAY_ADMIN_CSRF'
const CSRF_HEADER_NAME = 'X-CSRF-Token'
const UNSAFE_METHODS = new Set(['post', 'put', 'patch', 'delete'])
const CSRF_RETRY_MAX = 1 // CSRF 失败后最多自动重试次数

let csrfTokenPromise: Promise<string> | null = null
let csrfTokenInvalidated = false // 标记当前缓存的 Cookie Token 是否已被后端拒绝

function shouldAttachCsrfToken(config: InternalAxiosRequestConfig): boolean {
  const method = (config.method || 'get').toLowerCase()
  const url = config.url || ''
  return UNSAFE_METHODS.has(method) && isSameOriginAdminPath(url)
}

function isSameOriginAdminPath(url: string): boolean {
  if (/^https?:\/\//i.test(url)) {
    try {
      const parsedUrl = new URL(url)
      return parsedUrl.origin === window.location.origin && parsedUrl.pathname.startsWith('/admin/')
    } catch {
      return false
    }
  }
  const path = url.startsWith('/') ? url : `/${url}`
  return path.startsWith('/admin/')
}

function readCookie(name: string): string {
  const cookie = document.cookie.split('; ').find((item) => item.startsWith(`${name}=`))
  return cookie ? decodeURIComponent(cookie.substring(name.length + 1)) : ''
}

async function getCsrfToken(forceRefresh = false): Promise<string> {
  // 当缓存的 Cookie Token 已被后端拒绝时，强制从后端重新获取
  const cookieToken = !forceRefresh && !csrfTokenInvalidated ? readCookie(CSRF_COOKIE_NAME) : ''
  if (cookieToken) {
    return cookieToken
  }

  if (!csrfTokenPromise) {
    // GET /admin/csrf 不会触发请求拦截器附加 CSRF Token（GET 不在 UNSAFE_METHODS 中），因此不会递归调用
    csrfTokenPromise = request
      .get<never, { token: string }>('/admin/csrf')
      .then((data) => data.token)
      .catch((err) => {
        console.warn('[CSRF] 获取 CSRF Token 失败，写操作可能被拒绝:', err?.message || err)
        return ''
      })
      .finally(() => {
        csrfTokenPromise = null
        csrfTokenInvalidated = false // 无论成功失败都重置标记，下次正常走 Cookie 读取
      })
  }
  return csrfTokenPromise
}

request.interceptors.request.use(async (config) => {
  if (!shouldAttachCsrfToken(config)) {
    return config
  }

  try {
    const token = await getCsrfToken()
    if (token) {
      const headers = AxiosHeaders.from(config.headers)
      headers.set(CSRF_HEADER_NAME, token)
      config.headers = headers
    }
  } catch {
    // CSRF Token 获取失败不阻塞请求，由后端校验决定是否拒绝
    console.warn('[CSRF] 无法附加 CSRF Token，请求可能被后端拒绝')
  }
  return config
})

/**
 * 响应拦截器：
 * - 业务层错误（R<T> 包装）拆包抛错
 * - HTTP 401 → 登录状态失效，刷新认证状态并跳转登录页
 * - HTTP 429 → 限流提示，显示剩余配额和重试时间
 * - HTTP 503 → 服务不可用（熔断），友好提示
 * - 其他网络异常统一提示
 */
request.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>

    // 后端管理接口统一使用 R<T> 包装，这里集中拆包并统一抛错。
    if (typeof body?.success === 'boolean') {
      if (body.success) {
        return body.data
      }

      const message = body.message || '请求失败，请稍后重试'
      ElMessage.error(message)
      return Promise.reject(new Error(message))
    }

    return response.data
  },
  (error) => {
    const status = error?.response?.status

    // 401 未授权：登录状态失效或尚未登录，刷新状态并跳转
    if (status === 401) {
      markAuthRefreshRequired()
      const hash = window.location.hash || ''
      if (!hash.includes('/login')) {
        const current = hash.replace(/^#/, '')
        const redirect =
          current && current !== '/' ? `?redirect=${encodeURIComponent(current)}` : ''
        window.location.hash = `#/login${redirect}`
      }
      const message = error?.response?.data?.message || '请先登录后继续'
      ElMessage.error(message)
      return Promise.reject(error)
    }

    // 403 CSRF Token 失效：标记失效并自动重试一次
    if (status === 403 && error?.response?.data?.code === 'CSRF_TOKEN_INVALID') {
      const config = error.config
      const retryCount = config.__csrfRetryCount || 0
      if (retryCount < CSRF_RETRY_MAX) {
        csrfTokenInvalidated = true
        config.__csrfRetryCount = retryCount + 1
        return request.request(config)
      }
      ElMessage.error('CSRF Token 已失效，请刷新页面重试')
      return Promise.reject(error)
    }

    // 429 限流：提取限流信息
    if (status === 429) {
      const headers = error?.response?.headers || {}
      const limit = headers['x-ratelimit-limit']
      const retryAfter = headers['retry-after']

      let rateLimitMsg = '请求过于频繁，请稍后重试'
      if (limit) {
        rateLimitMsg = `请求频率超限（上限 ${limit} 次/分钟），请稍后重试`
      }
      if (retryAfter) {
        const resetDate = new Date(Number(retryAfter) * 1000)
        const waitSeconds = Math.max(0, Math.ceil((resetDate.getTime() - Date.now()) / 1000))
        rateLimitMsg += `，预计 ${waitSeconds} 秒后恢复`
      }
      ElMessage.warning(rateLimitMsg)
      return Promise.reject(error)
    }

    // 503 服务不可用（熔断器打开）
    if (status === 503) {
      ElMessage.error('AI 服务暂时不可用，系统正在自动切换备用服务，请稍后重试')
      return Promise.reject(error)
    }

    // 504 网关超时
    if (status === 504) {
      ElMessage.error('AI 服务响应超时，请稍后重试或减少输入长度')
      return Promise.reject(error)
    }

    // 其他错误
    const message =
      error?.response?.data?.message ||
      error?.response?.data?.error?.message ||
      error?.message ||
      '网络异常，请稍后重试'

    ElMessage.error(message)
    return Promise.reject(error)
  },
)

export default request
