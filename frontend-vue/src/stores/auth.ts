import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, type LoginParams, type LoginResult } from '../api/auth'

const TOKEN_KEY = 'ai_gateway_admin_token'

/**
 * 认证状态管理
 *
 * 职责：
 * - JWT Token 持久化（localStorage）
 * - 登录 / 登出
 * - 暴露认证状态供路由守卫和组件使用
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const isAuthenticated = computed(() => !!token.value)

  /**
   * 登录：调用后端接口，成功后持久化 Token
   */
  async function login(params: LoginParams): Promise<LoginResult> {
    const result = await loginApi(params)
    token.value = result.token
    localStorage.setItem(TOKEN_KEY, result.token)
    return result
  }

  /**
   * 登出：清除本地 Token
   */
  function logout() {
    token.value = null
    localStorage.removeItem(TOKEN_KEY)
  }

  /**
   * 初始化时恢复 Token（页面刷新场景）
   */
  function restore() {
    const saved = localStorage.getItem(TOKEN_KEY)
    if (saved) {
      token.value = saved
    }
  }

  return { token, isAuthenticated, login, logout, restore }
})
