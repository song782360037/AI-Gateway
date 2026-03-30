<template>
  <ConsoleLayout>
    <!-- 顶部统计卡片 -->
    <div class="stat-card-grid">
      <div v-for="item in statCards" :key="item.label" class="stat-card-item">
        <div class="stat-card-item__icon" :style="{ background: item.iconBg }">
          <el-icon :size="20" :color="item.iconColor">
            <component :is="item.icon" />
          </el-icon>
        </div>
        <div class="stat-card-item__body">
          <div class="stat-card-item__label">{{ item.label }}</div>
          <div class="stat-card-item__value">{{ item.value }}</div>
          <div class="stat-card-item__sub">{{ item.sub }}</div>
        </div>
      </div>
    </div>

    <!-- 模型排行 + 最近请求 -->
    <div class="dashboard-tables">
      <div class="page-card dashboard-table-panel">
        <div class="card-header">
          <span class="card-header__title">模型调用排行</span>
        </div>
        <el-table :data="modelRank" stripe size="default" class="dashboard-table">
          <el-table-column prop="rank" label="#" width="50" />
          <el-table-column prop="modelName" label="模型" min-width="160" />
          <el-table-column prop="callCount" label="调用次数" min-width="100">
            <template #default="{ row }">{{ formatNumber(row.callCount) }}</template>
          </el-table-column>
          <el-table-column prop="tokenCount" label="Token 消耗" min-width="100">
            <template #default="{ row }">{{ formatTokenCount(row.tokenCount) }}</template>
          </el-table-column>
          <el-table-column prop="cost" label="费用 (USD)" min-width="100">
            <template #default="{ row }">${{ row.cost.toFixed(2) }}</template>
          </el-table-column>
        </el-table>
      </div>

      <div class="page-card dashboard-table-panel">
        <div class="card-header">
          <span class="card-header__title">最近请求</span>
        </div>
        <el-table :data="recentRequests" stripe size="default" class="dashboard-table">
          <el-table-column prop="time" label="时间" width="90" />
          <el-table-column prop="model" label="模型" min-width="160" />
          <el-table-column prop="provider" label="通道" min-width="120" />
          <el-table-column prop="tokens" label="Token" width="80">
            <template #default="{ row }">{{ formatNumber(row.tokens) }}</template>
          </el-table-column>
          <el-table-column prop="duration" label="耗时" width="80">
            <template #default="{ row }">{{ (row.duration / 1000).toFixed(1) }}s</template>
          </el-table-column>
          <el-table-column label="状态" width="70">
            <template #default="{ row }">
              <el-tag :type="row.status === 'success' ? 'success' : 'danger'" size="small">
                {{ row.status === 'success' ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </ConsoleLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Connection, Timer, Coin, TrendCharts, Odometer } from '@element-plus/icons-vue'
import ConsoleLayout from '../../layout/ConsoleLayout.vue'
import { fetchDashboardStats, fetchModelUsageRank, fetchRecentRequests } from '../../api/dashboard'
import type { DashboardStats, ModelUsageRank, RecentRequest } from '../../types/dashboard'

const stats = reactive<DashboardStats>({
  requests: { today: 0, total: 0 },
  cost: { today: 0, total: 0 },
  tokens: { today: 0, total: 0 },
  tpm: 0,
  rpm: 0,
  avgResponseMs: 0,
})
const modelRank = ref<ModelUsageRank[]>([])
const recentRequests = ref<RecentRequest[]>([])

// 格式化工具函数
function formatNumber(n: number): string {
  return n.toLocaleString()
}

function formatTokenCount(n: number): string {
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M'
  if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K'
  return String(n)
}

function formatCost(n: number): string {
  return '$' + n.toFixed(2)
}

function formatMs(ms: number): string {
  if (ms >= 1000) return (ms / 1000).toFixed(1) + 's'
  return ms + 'ms'
}

// 统计卡片配置
const statCards = computed(() => [
  {
    label: '今日请求',
    value: formatNumber(stats.requests.today),
    sub: `累计 ${formatNumber(stats.requests.total)}`,
    icon: Connection,
    iconBg: '#ecf5ff',
    iconColor: '#409eff',
  },
  {
    label: '今日消费',
    value: formatCost(stats.cost.today),
    sub: `累计 ${formatCost(stats.cost.total)}`,
    icon: Coin,
    iconBg: '#fdf6ec',
    iconColor: '#e6a23c',
  },
  {
    label: '今日 Token',
    value: formatTokenCount(stats.tokens.today),
    sub: `累计 ${formatTokenCount(stats.tokens.total)}`,
    icon: TrendCharts,
    iconBg: '#f0f9eb',
    iconColor: '#67c23a',
  },
  {
    label: 'TPM',
    value: formatNumber(stats.tpm),
    sub: '每分钟 Token 数',
    icon: Odometer,
    iconBg: '#fef0f0',
    iconColor: '#f56c6c',
  },
  {
    label: 'RPM',
    value: formatNumber(stats.rpm),
    sub: '每分钟请求数',
    icon: TrendCharts,
    iconBg: '#f4f4f5',
    iconColor: '#909399',
  },
  {
    label: '平均响应',
    value: formatMs(stats.avgResponseMs),
    sub: '最近 1 小时均值',
    icon: Timer,
    iconBg: '#ecf5ff',
    iconColor: '#409eff',
  },
])

async function loadAll() {
  const [s, rank, recent] = await Promise.all([
    fetchDashboardStats(),
    fetchModelUsageRank(),
    fetchRecentRequests(),
  ])
  Object.assign(stats, s)
  modelRank.value = rank
  recentRequests.value = recent
}

onMounted(loadAll)
</script>
