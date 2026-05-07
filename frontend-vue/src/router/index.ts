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
      redirect: '/home',
    },
    {
      path: '/home',
      name: 'home',
      component: () => import('../views/home/HomeView.vue'),
      meta: { title: '首页', public: true },
    },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: () => import('../views/dashboard/DashboardView.vue'),
      meta: { title: '数据仪表盘' },
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
      path: '/model-config',
      name: 'model-config',
      component: () => import('../views/model-config/ModelConfigView.vue'),
      meta: {
        eyebrow: '模型配置',
        title: '模型配置',
        description:
          '配置 auto 智能路由规则和对外支持的模型列表，管理路由策略与 /v1/models 接口返回的模型。',
        tag: '模型',
      },
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
 * - 首次进入先向后端探测：是否已初始化管理员、当前 Cookie 是否已登录
 * - 系统未初始化时，统一引导到登录页中的“首次创建管理员”态
 * - 已登录用户访问登录页时，重定向到仪表盘
 * - 未登录用户访问受保护路由时，重定向到登录页（携带 redirect 参数）
 */
router.beforeEach(async (to) => {
  const authStore = useAuthStore()

  try {
    await authStore.bootstrap()
  } catch {
    if (!to.meta.public) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
    return true
  }

  if (authStore.needsInitialization) {
    if (to.path !== '/login') {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
    return true
  }

  if (to.path === '/login' && authStore.isAuthenticated) {
    return { path: '/dashboard' }
  }

  // public 路由（首页、登录页）未登录时直接放行，避免死循环
  if (to.meta.public) {
    return true
  }

  if (!authStore.isAuthenticated) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  return true
})

export default router
