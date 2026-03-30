<template>
  <ConsoleLayout>
    <!-- 状态概览 -->
    <div class="page-card">
      <div class="card-header">
        <span class="card-header__title">运行时快照状态</span>
        <div class="card-header__actions">
          <el-button size="small" :loading="loading" @click="loadStatus">刷新状态</el-button>
          <el-button type="primary" size="small" :loading="loading" @click="reloadRuntime">重载运行时快照</el-button>
        </div>
      </div>

      <!-- 加载失败提示 -->
      <div v-if="loadError && !loading" style="padding: 20px; text-align: center; color: var(--text-secondary);">
        <p>无法获取运行时状态，请检查后端服务后重试。</p>
        <el-button type="primary" size="small" style="margin-top: 12px;" @click="loadStatus">重试</el-button>
      </div>

      <template v-else>
        <div class="status-grid">
          <div class="status-item">
            <div class="status-item__label">快照状态</div>
            <div :class="['status-item__value', status.hasSnapshot ? 'status-item__value--success' : 'status-item__value--warning']">
              {{ status.hasSnapshot ? '已加载' : '未初始化' }}
            </div>
          </div>
          <div class="status-item">
            <div class="status-item__label">快照版本</div>
            <div class="status-item__value">{{ status.version || '--' }}</div>
          </div>
          <div class="status-item">
            <div class="status-item__label">接入通道</div>
            <div class="status-item__value">{{ status.providerCount || 0 }}</div>
          </div>
          <div class="status-item">
            <div class="status-item__label">模型规则</div>
            <div class="status-item__value">{{ status.aliasCount || 0 }}</div>
          </div>
          <div class="status-item">
            <div class="status-item__label">刷新来源</div>
            <div class="status-item__value">{{ status.source || '--' }}</div>
          </div>
          <div class="status-item">
            <div class="status-item__label">同步状态</div>
            <div :class="['status-item__value', status.dirty ? 'status-item__value--danger' : 'status-item__value--success']">
              {{ status.dirty ? '待排查' : '正常' }}
            </div>
          </div>
        </div>
      </template>
    </div>
  </ConsoleLayout>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ConsoleLayout from '../../layout/ConsoleLayout.vue'
import { fetchRuntimeStatus, reloadRuntimeConfig } from '../../api/runtime-config'
import type { RuntimeStatus } from '../../types/runtime'

const status = reactive<RuntimeStatus>({
  hasSnapshot: false,
  dirty: false,
})
const loading = ref(false)
const loadError = ref(false)

async function loadStatus() {
  loading.value = true
  loadError.value = false

  try {
    Object.assign(status, await fetchRuntimeStatus())
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

async function reloadRuntime() {
  try {
    await ElMessageBox.confirm(
      '重载将从数据库重新读取全部配置并刷新内存快照与缓存，是否继续？',
      '重载运行时快照',
      { type: 'warning' },
    )
  } catch {
    return
  }

  loading.value = true

  try {
    const success = await reloadRuntimeConfig()
    if (success) {
      ElMessage.success('运行时快照重载成功')
    } else {
      ElMessage.warning('运行时快照重载失败')
    }
  } catch {
    // 请求层已统一处理错误提示
  } finally {
    await loadStatus()
  }
}

onMounted(loadStatus)
</script>
