import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '../types/common'

const request = axios.create({
  // 统一从环境变量读取基础路径，开发和生产都保持同一调用方式。
  baseURL: import.meta.env.VITE_API_BASE,
  timeout: 15000,
})

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
    const message = error?.response?.data?.message
      || error?.response?.data?.error?.message
      || error?.message
      || '网络异常，请稍后重试'

    ElMessage.error(message)
    return Promise.reject(error)
  },
)

export default request
