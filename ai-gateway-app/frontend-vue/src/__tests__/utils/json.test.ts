import { describe, it, expect } from 'vitest'
import { formatJsonText, isValidJsonText, normalizeJsonText } from '../../utils/json'

describe('json 工具函数', () => {
  describe('formatJsonText', () => {
    it('格式化合法 JSON 字符串', () => {
      const input = '{"name":"test","value":123}'
      const result = formatJsonText(input)
      expect(result).toContain('\n')
      expect(result).toContain('"name"')
      expect(JSON.parse(result)).toEqual({ name: 'test', value: 123 })
    })

    it('null 输入返回空字符串', () => {
      expect(formatJsonText(null)).toBe('')
    })

    it('undefined 输入返回空字符串', () => {
      expect(formatJsonText(undefined)).toBe('')
    })

    it('空字符串输入返回空字符串', () => {
      expect(formatJsonText('')).toBe('')
    })

    it('非法 JSON 原样返回', () => {
      const input = '{invalid json}'
      expect(formatJsonText(input)).toBe('{invalid json}')
    })

    it('已格式化的 JSON 保持格式化', () => {
      const input = '{"a": 1}'
      const result = formatJsonText(input)
      expect(JSON.parse(result)).toEqual({ a: 1 })
    })
  })

  describe('isValidJsonText', () => {
    it('合法 JSON 返回 true', () => {
      expect(isValidJsonText('{"key":"value"}')).toBe(true)
    })

    it('合法 JSON 数组返回 true', () => {
      expect(isValidJsonText('[1,2,3]')).toBe(true)
    })

    it('null 输入返回 true（空值视为合法）', () => {
      expect(isValidJsonText(null)).toBe(true)
    })

    it('undefined 输入返回 true（空值视为合法）', () => {
      expect(isValidJsonText(undefined)).toBe(true)
    })

    it('空字符串返回 true（空值视为合法）', () => {
      expect(isValidJsonText('')).toBe(true)
    })

    it('纯空白字符串返回 true', () => {
      expect(isValidJsonText('   ')).toBe(true)
    })

    it('非法 JSON 返回 false', () => {
      expect(isValidJsonText('{broken')).toBe(false)
    })

    it('普通文本返回 false', () => {
      expect(isValidJsonText('hello world')).toBe(false)
    })

    it('JSON 数字返回 true', () => {
      expect(isValidJsonText('42')).toBe(true)
    })

    it('JSON 布尔值返回 true', () => {
      expect(isValidJsonText('true')).toBe(true)
    })
  })

  describe('normalizeJsonText', () => {
    it('压缩格式化的 JSON', () => {
      const input = '{\n  "name": "test",\n  "value": 123\n}'
      expect(normalizeJsonText(input)).toBe('{"name":"test","value":123}')
    })

    it('null 输入返回空字符串', () => {
      expect(normalizeJsonText(null)).toBe('')
    })

    it('undefined 输入返回空字符串', () => {
      expect(normalizeJsonText(undefined)).toBe('')
    })

    it('纯空白字符串返回空字符串', () => {
      expect(normalizeJsonText('   ')).toBe('')
    })

    it('非法 JSON 去除首尾空白后返回', () => {
      expect(normalizeJsonText('  {broken}  ')).toBe('{broken}')
    })

    it('已压缩的 JSON 保持不变', () => {
      const input = '{"a":1}'
      expect(normalizeJsonText(input)).toBe('{"a":1}')
    })
  })
})
