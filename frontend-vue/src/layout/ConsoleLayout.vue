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
      </el-menu>
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
import { useRoute } from 'vue-router'
import { Connection, DataAnalysis, Odometer, Share } from '@element-plus/icons-vue'

const route = useRoute()

const activeMenu = computed(() => route.path)

const pageTitle = computed(() => {
  const meta = route.meta ?? {}
  return typeof meta.title === 'string' ? meta.title : ''
})
</script>
