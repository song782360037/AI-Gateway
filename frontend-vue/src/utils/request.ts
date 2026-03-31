import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '../types/common'

const TOKEN_KEY = 'ai_gateway_admin_token'

const request = axios.create({
  // 统一从环境变量读取基础路径，开发和生产都保持同一调用方式。
  baseURL: import.meta.env.VITE_API_BASE,
  timeout: 15000,
})

/**
 * 请求拦截器：自动附加 JWT Token
 */
request.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/**
 * 响应拦截器：
 * - 业务层错误（R<T> 包装）拆包抛错
 * - HTTP 401 → Token 失效，清除本地状态并跳转登录页
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
    // 401 未授权：Token 过期或无效，清除登录态并跳转
    if (error?.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      // 避免重复跳转（当前已在登录页时不跳转）
      const hash = window.location.hash || ''
      if (!hash.includes('/login')) {
        const current = hash.replace(/^#/, '')
        const redirect = current && current !== '/' ? `?redirect=${encodeURIComponent(current)}` : ''
        window.location.hash = `#/login${redirect}`
      }
      ElMessage.error('登录已过期，请重新登录')
      return Promise.reject(error)
    }

    const message = error?.response?.data?.message
      || error?.response?.data?.error?.message
      || error?.message
      || '网络异常，请稍后重试'

    ElMessage.error(message)
    return Promise.reject(error)
  },
)

export default request
