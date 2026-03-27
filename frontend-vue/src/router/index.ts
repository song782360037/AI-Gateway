import { createRouter, createWebHashHistory } from 'vue-router'

const router = createRouter({
  // 改为 hash 路由，避免 Spring Boot 额外增加 history fallback 映射。
  history: createWebHashHistory('/frontend-vue/'),
  routes: [
    {
      path: '/',
      redirect: '/runtime',
    },
    {
      path: '/runtime',
      name: 'runtime',
      component: () => import('../views/runtime/RuntimeStatusView.vue'),
    },
    {
      path: '/provider',
      name: 'provider',
      component: () => import('../views/provider/ProviderConfigView.vue'),
    },
    {
      path: '/model-redirect',
      name: 'model-redirect',
      component: () => import('../views/model/ModelRedirectConfigView.vue'),
    },
  ],
})

export default router
