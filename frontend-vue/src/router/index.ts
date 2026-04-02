import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  // hash 路由，避免 Spring Boot 额外增加 history fallback 映射
  history: createWebHashHistory('/frontend-vue/'),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/login/LoginView.vue'),
      meta: { title: '登录', public: true },
    },
    {
      path: '/',
      redirect: '/dashboard',
    },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: () => import('../views/dashboard/DashboardView.vue'),
      meta: { title: '仪表盘' },
    },
    {
      path: '/runtime',
      name: 'runtime',
      component: () => import('../views/runtime/RuntimeStatusView.vue'),
      meta: {
        eyebrow: '运行时概览',
        title: '运行时状态',
        description: '查看快照版本、脏标记和刷新来源，帮助后台管理员快速判断当前配置是否处于健康状态。',
        tag: '运行时',
      },
    },
    {
      path: '/provider',
      name: 'provider',
      component: () => import('../views/provider/ProviderManagementView.vue'),
      meta: {
        eyebrow: '提供商管理',
        title: '提供商管理',
        description: '统一管理接入通道和模型路由规则，在一个页面内完成提供商的完整配置。',
        tag: '提供商',
      },
    },
    // 旧路径兼容重定向
    {
      path: '/model-redirect',
      redirect: '/provider',
    },
    {
      path: '/api-key',
      name: 'api-key',
      component: () => import('../views/api-key/ApiKeyConfigView.vue'),
      meta: {
        eyebrow: 'API Key 管理',
        title: 'API Key 配置',
        description: '管理 API Key 的创建、状态、限额和过期时间，密钥创建后仅展示一次。',
        tag: '密钥',
      },
    },
    {
      path: '/request-log',
      name: 'request-log',
      component: () => import('../views/log/RequestLogView.vue'),
      meta: {
        eyebrow: '请求日志',
        title: '请求日志',
        description: '查看 API 请求的历史记录，支持按时间、协议、状态等条件筛选。',
        tag: '日志',
      },
    },
  ],
})

/**
 * 全局前置守卫
 *
 * - public 路由（如登录页）始终可访问
 * - 已认证用户访问登录页时，重定向到仪表盘
 * - 未认证用户访问受保护路由时，重定向到登录页（携带 redirect 参数）
 */
router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore()

  if (to.meta.public) {
    // 已登录用户访问登录页，直接跳转首页
    if (authStore.isAuthenticated) {
      next({ path: '/dashboard' })
    } else {
      next()
    }
    return
  }

  if (!authStore.isAuthenticated) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  next()
})

export default router
