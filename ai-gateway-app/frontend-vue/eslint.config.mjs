import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import pluginVue from 'eslint-plugin-vue'
import prettierConfig from 'eslint-config-prettier'
import globals from 'globals'

export default tseslint.config(
  // 全局忽略目录
  {
    ignores: ['dist/**', 'node_modules/**', '*.d.ts'],
  },

  // JavaScript 推荐规则
  js.configs.recommended,

  // TypeScript 推荐规则
  ...tseslint.configs.recommended,

  // Vue 推荐规则（flat config 格式）
  ...pluginVue.configs['flat/recommended'],

  // Prettier 配置：关闭所有与 Prettier 冲突的 ESLint 规则
  // 格式问题由 npm run format (prettier --write) 单独处理
  prettierConfig,

  // 浏览器全局变量支持（Vue 组件中使用的 document/window 等）
  {
    languageOptions: {
      globals: {
        ...globals.browser,
      },
    },
  },

  // Vue 文件使用 vue-eslint-parser，脚本块使用 @typescript-eslint/parser
  {
    files: ['**/*.vue'],
    languageOptions: {
      parserOptions: {
        parser: tseslint.parser,
        extraFileExtensions: ['.vue'],
        sourceType: 'module',
      },
    },
  },

  // 自定义规则覆盖
  {
    rules: {
      // TypeScript 规则调整
      '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-empty-object-type': 'off',

      // Vue 规则调整
      'vue/multi-word-component-names': 'off',
      'vue/no-v-html': 'off',
    },
  },

  // 测试文件规则放宽
  {
    files: ['**/*.test.ts', '**/*.spec.ts', 'src/__tests__/**'],
    rules: {
      '@typescript-eslint/no-explicit-any': 'off',
    },
  },
)
