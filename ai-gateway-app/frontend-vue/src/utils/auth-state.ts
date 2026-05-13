const AUTH_REFRESH_FLAG = 'ai_gateway_admin_auth_refresh'

export function markAuthRefreshRequired() {
  sessionStorage.setItem(AUTH_REFRESH_FLAG, '1')
}

export function consumeAuthRefreshFlag() {
  const required = sessionStorage.getItem(AUTH_REFRESH_FLAG) === '1'
  if (required) {
    sessionStorage.removeItem(AUTH_REFRESH_FLAG)
  }
  return required
}
