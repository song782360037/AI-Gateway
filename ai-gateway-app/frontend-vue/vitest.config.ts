import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  test: {
    // 使用 jsdom 模拟浏览器环境
    environment: 'jsdom',
    // 全局 API（describe/it/expect）无需手动导入
    globals: true,
    // 测试文件匹配模式
    include: ['src/__tests__/**/*.test.ts'],
    // 覆盖率配置（需安装 @vitest/coverage-v8 后启用）
    // coverage: {
    //   provider: 'v8',
    //   include: ['src/utils/**', 'src/stores/**'],
    // },
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
})
