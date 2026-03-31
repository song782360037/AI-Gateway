<template>
  <div class="admin-layout">
    <!-- 左侧深色侧边栏 -->
    <aside class="sidebar">
      <div class="sidebar-logo">
        <h1>AI Gateway</h1>
      </div>
      <el-menu
        :default-active="activeMenu"
        :router="true"
        background-color="#1d1e2c"
        text-color="#a3a6b7"
        active-text-color="#ffffff"
        class="sidebar-menu"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/runtime">
          <el-icon><Odometer /></el-icon>
          <span>运行时状态</span>
        </el-menu-item>
        <el-menu-item index="/provider">
          <el-icon><Connection /></el-icon>
          <span>接入通道</span>
        </el-menu-item>
        <el-menu-item index="/model-redirect">
          <el-icon><Share /></el-icon>
          <span>模型路由规则</span>
        </el-menu-item>
        <el-menu-item index="/api-key">
          <el-icon><Key /></el-icon>
          <span>API Key 配置</span>
        </el-menu-item>
      </el-menu>

      <!-- 侧边栏底部：用户信息 + 登出 -->
      <div class="sidebar-footer">
        <div class="sidebar-user">
          <el-icon :size="18"><User /></el-icon>
          <span class="sidebar-user__name">{{ username }}</span>
        </div>
        <el-tooltip content="退出登录" placement="right">
          <button class="sidebar-logout" @click="handleLogout">
            <el-icon :size="16"><SwitchButton /></el-icon>
          </button>
        </el-tooltip>
      </div>
    </aside>

    <!-- 右侧主内容区 -->
    <div class="main-wrapper">
      <header class="topbar">
        <h2 class="topbar-title">{{ pageTitle }}</h2>
      </header>
      <main class="content">
        <slot></slot>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Connection, DataAnalysis, Key, Odometer, Share, SwitchButton, User } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const activeMenu = computed(() => route.path)

const pageTitle = computed(() => {
  const meta = route.meta ?? {}
  return typeof meta.title === 'string' ? meta.title : ''
})

/** 从 JWT payload 解析用户名（不依赖后端接口） */
const username = computed(() => {
  const token = authStore.token
  if (!token) return 'admin'
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return payload.sub || 'admin'
  } catch {
    return 'admin'
  }
})

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    authStore.logout()
    router.replace('/login')
  } catch {
    // 用户取消
  }
}
</script>

<style scoped>
/* 侧边栏底部 */
.sidebar-footer {
  margin-top: auto;
  padding: 12px 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sidebar-user {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #a3a6b7;
}

.sidebar-user__name {
  font-size: 13px;
}

.sidebar-logout {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #a3a6b7;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.sidebar-logout:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #f56c6c;
}
</style>
