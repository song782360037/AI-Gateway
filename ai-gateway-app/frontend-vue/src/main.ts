import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './style.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)

// 挂载状态管理、路由与 UI 组件库，形成独立前端应用入口。
app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.mount('#app')
