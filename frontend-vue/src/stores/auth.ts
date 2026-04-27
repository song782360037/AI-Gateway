import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  changePassword as changePasswordApi,
  getAuthStatus,
  initializeAdmin as initializeAdminApi,
  login as loginApi,
  logout as logoutApi,
  updateUsername as updateUsernameApi,
  type AdminAuthStatus,
  type ChangePasswordParams,
  type LoginParams,
  type SetupParams,
  type UpdateUsernameParams,
} from '../api/auth'
import { consumeAuthRefreshFlag } from '../utils/auth-state'

/**
 * 后台认证状态管理
 *
 * 职责：
 * - 初始化状态探测（是否已创建管理员）
 * - 当前登录状态恢复（依赖 HttpOnly Cookie + 后端状态接口）
 * - 登录 / 首次初始化 / 登出
 * - 修改用户名 / 修改密码后的本地状态同步
 */
export const useAuthStore = defineStore('auth', () => {
  const ready = ref(false)
  const initialized = ref<boolean | null>(null)
  const authenticated = ref(false)
  const username = ref('')
  const bootstrapError = ref('')
  let bootstrapPromise: Promise<AdminAuthStatus> | null = null

  const isAuthenticated = computed(() => authenticated.value)
  const needsInitialization = computed(() => initialized.value === false)

  function applyStatus(status: AdminAuthStatus) {
    initialized.value = status.initialized
    authenticated.value = status.authenticated
    username.value = status.username ?? ''
    bootstrapError.value = ''
    ready.value = true
  }

  function clearAuthenticatedState() {
    authenticated.value = false
    username.value = ''
  }

  function resetState() {
    ready.value = false
    initialized.value = null
    bootstrapError.value = ''
    clearAuthenticatedState()
  }

  function applyBootstrapFailure(error: unknown) {
    initialized.value = null
    clearAuthenticatedState()
    bootstrapError.value = error instanceof Error ? error.message : '系统状态检查失败，请稍后重试'
    ready.value = true
  }

  function currentStatus(): AdminAuthStatus {
    return {
      initialized: initialized.value ?? false,
      authenticated: authenticated.value,
      username: username.value || null,
    }
  }

  async function bootstrap(force = false): Promise<AdminAuthStatus> {
    const shouldRefresh = force || consumeAuthRefreshFlag() || Boolean(bootstrapError.value)
    if (ready.value && !shouldRefresh) {
      return currentStatus()
    }
    if (bootstrapPromise) {
      return bootstrapPromise
    }

    bootstrapPromise = getAuthStatus()
      .then((status) => {
        applyStatus(status)
        return status
      })
      .catch((error) => {
        applyBootstrapFailure(error)
        throw error
      })
      .finally(() => {
        bootstrapPromise = null
      })

    return bootstrapPromise
  }

  async function initializeAdmin(params: SetupParams): Promise<AdminAuthStatus> {
    const status = await initializeAdminApi(params)
    applyStatus(status)
    return status
  }

  async function login(params: LoginParams): Promise<AdminAuthStatus> {
    const status = await loginApi(params)
    applyStatus(status)
    return status
  }

  async function updateUsername(params: UpdateUsernameParams): Promise<AdminAuthStatus> {
    const status = await updateUsernameApi(params)
    applyStatus(status)
    return status
  }

  async function changePassword(params: ChangePasswordParams): Promise<AdminAuthStatus> {
    const status = await changePasswordApi(params)
    applyStatus(status)
    return status
  }

  async function logout() {
    try {
      await logoutApi()
    } finally {
      initialized.value = initialized.value ?? true
      ready.value = true
      clearAuthenticatedState()
    }
  }

  return {
    ready,
    initialized,
    username,
    bootstrapError,
    isAuthenticated,
    needsInitialization,
    bootstrap,
    initializeAdmin,
    login,
    updateUsername,
    changePassword,
    logout,
    resetState,
  }
})
