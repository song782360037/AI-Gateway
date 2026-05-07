import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

const {
  mockGetAuthStatus,
  mockInitializeAdmin,
  mockLogin,
  mockLogout,
  mockUpdateUsername,
  mockChangePassword,
  mockConsumeAuthRefreshFlag,
} = vi.hoisted(() => ({
  mockGetAuthStatus: vi.fn(),
  mockInitializeAdmin: vi.fn(),
  mockLogin: vi.fn(),
  mockLogout: vi.fn(),
  mockUpdateUsername: vi.fn(),
  mockChangePassword: vi.fn(),
  mockConsumeAuthRefreshFlag: vi.fn(),
}))

vi.mock('../../api/auth', () => ({
  getAuthStatus: mockGetAuthStatus,
  initializeAdmin: mockInitializeAdmin,
  login: mockLogin,
  logout: mockLogout,
  updateUsername: mockUpdateUsername,
  changePassword: mockChangePassword,
}))

vi.mock('../../utils/auth-state', () => ({
  consumeAuthRefreshFlag: mockConsumeAuthRefreshFlag,
  markAuthRefreshRequired: vi.fn(),
}))

import { useAuthStore } from '../../stores/auth'

const MOCK_AUTH_STATUS = {
  initialized: true,
  authenticated: true,
  username: 'admin',
} as const

/**
 * auth store 测试
 * 认证状态通过 `isAuthenticated`（computed）暴露，不直接暴露内部 ref。
 */
describe('auth store 认证状态管理', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockConsumeAuthRefreshFlag.mockReturnValue(false)
  })

  describe('初始状态', () => {
    it('store 创建后具有正确的初始状态', () => {
      const store = useAuthStore()
      expect(store.ready).toBe(false)
      expect(store.initialized).toBeNull()
      expect(store.isAuthenticated).toBe(false)
      expect(store.username).toBe('')
      expect(store.bootstrapError).toBe('')
    })
  })

  describe('bootstrap', () => {
    it('首次调用时从 API 获取状态', async () => {
      mockGetAuthStatus.mockResolvedValue({ ...MOCK_AUTH_STATUS })

      const store = useAuthStore()
      const status = await store.bootstrap()

      expect(status.authenticated).toBe(true)
      expect(store.ready).toBe(true)
      expect(store.isAuthenticated).toBe(true)
      expect(store.username).toBe('admin')
      expect(mockGetAuthStatus).toHaveBeenCalledTimes(1)
    })

    it('未初始化时 needsInitialization 为 true', async () => {
      mockGetAuthStatus.mockResolvedValue({
        initialized: false,
        authenticated: false,
        username: null,
      })

      const store = useAuthStore()
      await store.bootstrap()

      expect(store.needsInitialization).toBe(true)
      expect(store.isAuthenticated).toBe(false)
    })

    it('已就绪时直接返回缓存状态，不重复请求', async () => {
      mockGetAuthStatus.mockResolvedValue({ ...MOCK_AUTH_STATUS })

      const store = useAuthStore()
      await store.bootstrap()
      await store.bootstrap()

      expect(mockGetAuthStatus).toHaveBeenCalledTimes(1)
    })

    it('force=true 时强制刷新', async () => {
      mockGetAuthStatus.mockResolvedValue({ ...MOCK_AUTH_STATUS })

      const store = useAuthStore()
      await store.bootstrap()
      await store.bootstrap(true)

      expect(mockGetAuthStatus).toHaveBeenCalledTimes(2)
    })

    it('consumeAuthRefreshFlag 返回 true 时触发刷新', async () => {
      mockGetAuthStatus.mockResolvedValue({ ...MOCK_AUTH_STATUS })
      mockConsumeAuthRefreshFlag.mockReturnValue(true)

      const store = useAuthStore()
      await store.bootstrap()

      expect(mockGetAuthStatus).toHaveBeenCalledTimes(1)
    })

    it('并发调用共享同一个请求（去重）', async () => {
      let resolvePromise!: (value: typeof MOCK_AUTH_STATUS) => void
      mockGetAuthStatus.mockImplementation(
        () =>
          new Promise((resolve) => {
            resolvePromise = resolve
          }),
      )

      const store = useAuthStore()
      const p1 = store.bootstrap()
      const p2 = store.bootstrap()

      resolvePromise({ ...MOCK_AUTH_STATUS })
      await Promise.all([p1, p2])

      expect(mockGetAuthStatus).toHaveBeenCalledTimes(1)
    })

    it('API 失败时记录错误并标记 ready', async () => {
      mockGetAuthStatus.mockRejectedValue(new Error('Network error'))

      const store = useAuthStore()
      await expect(store.bootstrap()).rejects.toThrow('Network error')

      expect(store.ready).toBe(true)
      expect(store.bootstrapError).toBe('Network error')
    })

    it('有 bootstrapError 时再次调用触发刷新', async () => {
      mockGetAuthStatus
        .mockRejectedValueOnce(new Error('Network error'))
        .mockResolvedValueOnce({ ...MOCK_AUTH_STATUS })

      const store = useAuthStore()
      await expect(store.bootstrap()).rejects.toThrow('Network error')
      expect(store.bootstrapError).toBe('Network error')

      const status = await store.bootstrap()
      expect(status.authenticated).toBe(true)
      expect(store.bootstrapError).toBe('')
    })
  })

  describe('login', () => {
    it('登录成功后更新本地状态', async () => {
      mockLogin.mockResolvedValue({ ...MOCK_AUTH_STATUS })

      const store = useAuthStore()
      await store.login({ username: 'admin', password: 'pass' })

      expect(store.isAuthenticated).toBe(true)
      expect(store.username).toBe('admin')
    })
  })

  describe('initializeAdmin', () => {
    it('初始化成功后更新本地状态', async () => {
      mockInitializeAdmin.mockResolvedValue({ ...MOCK_AUTH_STATUS })

      const store = useAuthStore()
      await store.initializeAdmin({ username: 'admin', password: 'pass' })

      expect(store.initialized).toBe(true)
      expect(store.isAuthenticated).toBe(true)
    })
  })

  describe('logout', () => {
    it('登出成功后清除认证状态', async () => {
      mockGetAuthStatus.mockResolvedValue({ ...MOCK_AUTH_STATUS })
      mockLogout.mockResolvedValue(undefined)

      const store = useAuthStore()
      await store.bootstrap()
      expect(store.isAuthenticated).toBe(true)

      await store.logout()

      expect(store.isAuthenticated).toBe(false)
      expect(store.username).toBe('')
      expect(store.ready).toBe(true)
    })

    it('API 失败时仍然清除本地状态（finally 保证）', async () => {
      mockGetAuthStatus.mockResolvedValue({ ...MOCK_AUTH_STATUS })
      mockLogout.mockRejectedValue(new Error('Server error'))

      const store = useAuthStore()
      await store.bootstrap()
      // logout 内部 try/finally 不 catch，错误仍冒泡；但 finally 确保状态已清理
      await expect(store.logout()).rejects.toThrow('Server error')

      expect(store.isAuthenticated).toBe(false)
      expect(store.username).toBe('')
      expect(store.ready).toBe(true)
    })
  })

  describe('updateUsername', () => {
    it('更新成功后同步本地用户名', async () => {
      const updatedStatus = { ...MOCK_AUTH_STATUS, username: 'newadmin' }
      mockUpdateUsername.mockResolvedValue(updatedStatus)

      const store = useAuthStore()
      await store.updateUsername({ currentPassword: 'pass', newUsername: 'newadmin' })

      expect(store.username).toBe('newadmin')
    })
  })

  describe('changePassword', () => {
    it('修改密码成功后保持认证状态', async () => {
      mockChangePassword.mockResolvedValue({ ...MOCK_AUTH_STATUS })

      const store = useAuthStore()
      await store.changePassword({ currentPassword: 'old', newPassword: 'new' })

      expect(store.isAuthenticated).toBe(true)
    })
  })

  describe('resetState', () => {
    it('重置后回到初始状态', async () => {
      mockGetAuthStatus.mockResolvedValue({ ...MOCK_AUTH_STATUS })

      const store = useAuthStore()
      await store.bootstrap()
      expect(store.ready).toBe(true)

      store.resetState()

      expect(store.ready).toBe(false)
      expect(store.initialized).toBeNull()
      expect(store.isAuthenticated).toBe(false)
      expect(store.username).toBe('')
      expect(store.bootstrapError).toBe('')
    })
  })
})
