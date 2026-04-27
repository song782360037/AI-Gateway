import request from '../utils/request'

export interface LoginParams {
  username: string
  password: string
}

export interface SetupParams {
  username: string
  password: string
}

export interface UpdateUsernameParams {
  currentPassword: string
  newUsername: string
}

export interface ChangePasswordParams {
  currentPassword: string
  newPassword: string
}

export interface AdminAuthStatus {
  initialized: boolean
  authenticated: boolean
  username: string | null
}

/** 获取系统初始化状态与当前登录状态 */
export function getAuthStatus(): Promise<AdminAuthStatus> {
  return request.get('/admin/bootstrap/status')
}

/** 首次初始化管理员 */
export function initializeAdmin(params: SetupParams): Promise<AdminAuthStatus> {
  return request.post('/admin/bootstrap/setup', params)
}

/** 管理员登录 */
export function login(params: LoginParams): Promise<AdminAuthStatus> {
  return request.post('/admin/login', params)
}

/** 修改管理员用户名 */
export function updateUsername(params: UpdateUsernameParams): Promise<AdminAuthStatus> {
  return request.post('/admin/account/username', params)
}

/** 修改管理员密码 */
export function changePassword(params: ChangePasswordParams): Promise<AdminAuthStatus> {
  return request.post('/admin/account/password', params)
}

/** 退出登录 */
export function logout(): Promise<void> {
  return request.post('/admin/logout')
}
