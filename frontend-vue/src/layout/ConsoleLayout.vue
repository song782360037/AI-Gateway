<template>
  <div class="admin-layout">
    <!-- 左侧深色侧边栏 -->
    <aside class="sidebar">
      <!-- Logo 品牌 -->
      <div class="sidebar-logo">
        <div class="sidebar-logo__mark">
          <!-- 棱镜分流图标：请求 → 棱镜 → 多路输出 -->
          <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
            <!-- 输入信号线 -->
            <path d="M0 9H5" stroke="white" stroke-width="2" stroke-linecap="round" opacity="0.6"/>
            <!-- 棱镜三角形（半透明填充 + 描边） -->
            <path d="M6 3L14 9L6 15Z" fill="white" fill-opacity="0.18"/>
            <path d="M6 3L14 9L6 15Z" stroke="white" stroke-width="1.5" stroke-linejoin="round"/>
            <!-- 输出分流线（上/中/下三路，递减透明度） -->
            <path d="M14 9L18 4.5" stroke="white" stroke-width="1.5" stroke-linecap="round" opacity="0.9"/>
            <path d="M14 9H18" stroke="white" stroke-width="1.5" stroke-linecap="round" opacity="0.55"/>
            <path d="M14 9L18 13.5" stroke="white" stroke-width="1.5" stroke-linecap="round" opacity="0.3"/>
          </svg>
        </div>
        <div class="sidebar-logo__text">
          <h1>AI Gateway</h1>
          <span class="sidebar-logo__sub">Console</span>
        </div>
      </div>

      <!-- 导航菜单 -->
      <el-menu
        :default-active="activeMenu"
        :router="true"
        background-color="transparent"
        text-color="var(--sidebar-text)"
        active-text-color="var(--sidebar-active-text)"
        class="sidebar-menu"
      >
        <div class="sidebar-nav-label">概览</div>
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/runtime">
          <el-icon><Odometer /></el-icon>
          <span>运行时状态</span>
        </el-menu-item>

        <div class="sidebar-nav-label">配置</div>
        <el-menu-item index="/provider">
          <el-icon><Connection /></el-icon>
          <span>提供商管理</span>
        </el-menu-item>
        <el-menu-item index="/api-key">
          <el-icon><Key /></el-icon>
          <span>API Key 配置</span>
        </el-menu-item>

        <div class="sidebar-nav-label">监控</div>
        <el-menu-item index="/request-log">
          <el-icon><Document /></el-icon>
          <span>请求日志</span>
        </el-menu-item>
      </el-menu>

      <!-- 侧边栏底部：用户信息 + 登出 -->
      <div class="sidebar-footer">
        <div class="sidebar-user">
          <div class="sidebar-user__avatar">
            <el-icon :size="14"><User /></el-icon>
          </div>
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
import { Connection, DataAnalysis, Document, Key, Odometer, SwitchButton, User } from '@element-plus/icons-vue'
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
/* 侧边栏底部用户区 */
.sidebar-footer {
  margin-top: auto;
  padding: 14px 16px;
  border-top: 1px solid var(--sidebar-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sidebar-user {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--sidebar-text);
}

/* 用户头像占位圆 */
.sidebar-user__avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(67, 97, 238, 0.15), rgba(6, 182, 212, 0.10));
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.sidebar-user__avatar .el-icon {
  color: var(--color-primary-light);
  font-size: 14px;
}

.sidebar-user__name {
  font-size: 13px;
  font-weight: 450;
  color: #94a3b8;
}

.sidebar-logout {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--sidebar-text);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.sidebar-logout:hover {
  background: rgba(239, 68, 68, 0.08);
  color: #f87171;
}
</style>
