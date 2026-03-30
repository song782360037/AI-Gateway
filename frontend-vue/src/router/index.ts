import { createRouter, createWebHashHistory } from 'vue-router'

const router = createRouter({
  // 改为 hash 路由，避免 Spring Boot 额外增加 history fallback 映射。
  history: createWebHashHistory('/frontend-vue/'),
  routes: [
    {
      path: '/',
      redirect: '/dashboard',
    },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: () => import('../views/dashboard/DashboardView.vue'),
      meta: {
        title: '仪表盘',
      },
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
      component: () => import('../views/provider/ProviderConfigView.vue'),
      meta: {
        eyebrow: '接入通道管理',
        title: '接入通道',
        description: '统一维护 Provider 编码、基础地址、密钥与优先级，适合日常配置和后台运维操作。',
        tag: '通道',
      },
    },
    {
      path: '/model-redirect',
      name: 'model-redirect',
      component: () => import('../views/model/ModelRedirectConfigView.vue'),
      meta: {
        eyebrow: '模型路由管理',
        title: '模型路由规则',
        description: '管理对外模型名与目标 Provider 的映射关系，用后台化方式集中维护路由决策配置。',
        tag: '路由',
      },
    },
  ],
})

export default router
