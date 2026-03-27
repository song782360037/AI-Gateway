<template>
  <ConsoleLayout>
    <template #hero-actions>
      <el-button type="warning" plain @click="loadStatus">刷新状态</el-button>
      <el-button type="primary" @click="reloadRuntime">重载运行时快照</el-button>
    </template>

    <section class="panel-grid focus-panel">
      <article class="stat-card stat-card-accent">
        <span class="stat-label">快照状态</span>
        <strong>{{ status.hasSnapshot ? 'ACTIVE' : 'EMPTY' }}</strong>
        <p>source: {{ status.source || '--' }}</p>
      </article>
      <article class="stat-card">
        <span class="stat-label">版本号</span>
        <strong>{{ status.version || '--' }}</strong>
        <p>createdAt: {{ status.createdAt || '--' }}</p>
      </article>
      <article class="stat-card">
        <span class="stat-label">Provider 数量</span>
        <strong>{{ status.providerCount || 0 }}</strong>
        <p>当前内存快照中的可用提供商数量</p>
      </article>
      <article class="stat-card">
        <span class="stat-label">Alias 数量</span>
        <strong>{{ status.aliasCount || 0 }}</strong>
        <p>当前路由候选的模型别名总数</p>
      </article>
    </section>

    <section class="single-panel">
      <article class="panel-frame focus-panel runtime-board">
        <div class="panel-head">
          <div>
            <p class="eyebrow">runtime snapshot</p>
            <h3>运行时健康概览</h3>
          </div>
          <el-tag :type="status.dirty ? 'danger' : 'success'" size="large">
            {{ status.dirty ? 'DIRTY' : 'CLEAN' }}
          </el-tag>
        </div>

        <div class="status-ribbon">
          <div>
            <span>内存快照</span>
            <strong>{{ status.hasSnapshot ? '已加载' : '未加载' }}</strong>
          </div>
          <div>
            <span>脏标记</span>
            <strong>{{ status.dirty ? '存在' : '无' }}</strong>
          </div>
          <div>
            <span>刷新来源</span>
            <strong>{{ status.source || '--' }}</strong>
          </div>
        </div>
      </article>
    </section>
  </ConsoleLayout>
</template>

<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import ConsoleLayout from '../../layout/ConsoleLayout.vue'
import { fetchRuntimeStatus, reloadRuntimeConfig } from '../../api/runtime-config'
import type { RuntimeStatus } from '../../types/runtime'

const status = reactive<RuntimeStatus>({
  hasSnapshot: false,
  dirty: false,
})

async function loadStatus() {
  Object.assign(status, await fetchRuntimeStatus())
}

async function reloadRuntime() {
  const success = await reloadRuntimeConfig()
  if (success) {
    ElMessage.success('运行时快照重载成功')
  } else {
    ElMessage.warning('运行时快照重载失败')
  }
  await loadStatus()
}

onMounted(loadStatus)
</script>
