import { describe, it, expect, beforeEach } from 'vitest'
import { markAuthRefreshRequired, consumeAuthRefreshFlag } from '../../utils/auth-state'

describe('auth-state 鉴权状态标记', () => {
  beforeEach(() => {
    // 每个测试前清空 sessionStorage
    sessionStorage.clear()
  })

  describe('markAuthRefreshRequired', () => {
    it('设置标记后 sessionStorage 中存在对应项', () => {
      markAuthRefreshRequired()
      expect(sessionStorage.getItem('ai_gateway_admin_auth_refresh')).toBe('1')
    })

    it('重复调用不会产生副作用', () => {
      markAuthRefreshRequired()
      markAuthRefreshRequired()
      expect(sessionStorage.getItem('ai_gateway_admin_auth_refresh')).toBe('1')
    })
  })

  describe('consumeAuthRefreshFlag', () => {
    it('无标记时返回 false', () => {
      expect(consumeAuthRefreshFlag()).toBe(false)
    })

    it('有标记时返回 true 并清除标记（读后即焚）', () => {
      markAuthRefreshRequired()
      // 第一次消费：返回 true
      expect(consumeAuthRefreshFlag()).toBe(true)
      // 第二次消费：标记已清除，返回 false
      expect(consumeAuthRefreshFlag()).toBe(false)
    })

    it('手动设置其他值时返回 false', () => {
      sessionStorage.setItem('ai_gateway_admin_auth_refresh', '0')
      expect(consumeAuthRefreshFlag()).toBe(false)
    })

    it('标记被消费后 sessionStorage 中不再存在', () => {
      markAuthRefreshRequired()
      consumeAuthRefreshFlag()
      expect(sessionStorage.getItem('ai_gateway_admin_auth_refresh')).toBeNull()
    })
  })
})
