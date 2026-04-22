<template>
  <ConsoleLayout>
    <div class="dashboard-page">
      <!-- ====== 顶部：页面标题 + 状态/操作区 ====== -->
      <div class="dashboard-header">
        <div class="dashboard-header__left">
          <div class="dashboard-header__status">
            <span class="status-capsule" :class="systemHealthy ? 'status-capsule--up' : 'status-capsule--down'">
              <span class="status-capsule__dot" />
              {{ systemHealthy ? '系统正常' : '系统异常' }}
            </span>
          </div>
        </div>
        <div class="dashboard-header__right">
          <el-icon :size="18" class="dashboard-header__notify">
            <Bell />
          </el-icon>
          <div class="period-switcher">
            <button
              v-for="opt in periodOptions"
              :key="opt.value"
              class="period-switcher__btn"
              :class="{ 'period-switcher__btn--active': activePeriod === opt.value }"
              :disabled="loading"
              @click="switchPeriod(opt.value)"
            >
              {{ opt.label }}
            </button>
          </div>
        </div>
      </div>

      <!-- ====== 中部：数据概览卡片 ====== -->
      <div class="overview-section">
        <div class="section-label">数据概览</div>
        <div class="overview-grid" v-loading="loading">
          <div
            v-for="card in overviewCards"
            :key="card.key"
            class="overview-card"
            :style="{ '--card-accent': card.accent, '--card-accent-bg': card.accentBg }"
          >
            <!-- 装饰背景 -->
            <div class="overview-card__deco" />
            <div class="overview-card__top">
              <div class="overview-card__icon-wrap">
                <el-icon :size="20" :color="card.accent">
                  <component :is="card.icon" />
                </el-icon>
              </div>
              <span class="overview-card__trend" :class="trendClass(card.metric.changePercent)">
                {{ trendText(card.metric.changePercent) }}
              </span>
            </div>
            <div class="overview-card__value">{{ card.displayValue }}</div>
            <div class="overview-card__sub">{{ card.subLabel }}</div>
          </div>
        </div>
      </div>

      <!-- ====== 底部：双列表格区 ====== -->
      <div class="tables-section">
        <!-- 左侧：模型调用排行 -->
        <div class="table-module">
          <div class="table-module__header">
            <div class="table-module__title">
              <el-icon :size="16"><TrendCharts /></el-icon>
              <span>模型调用排行</span>
            </div>
          </div>
          <div class="table-module__body">
            <el-table :data="modelRank" size="default" class="dashboard-table" :show-header="true">
              <el-table-column label="#" width="52" align="center">
                <template #default="{ row }">
                  <span class="rank-badge" :class="rankClass(row.rank)">{{ row.rank }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="modelName" label="请求模型" min-width="120" show-overflow-tooltip align="center" />
              <el-table-column prop="targetModel" label="目标模型" min-width="120" show-overflow-tooltip align="center" />
              <el-table-column prop="callCount" label="调用次数" min-width="90" align="center">
                <template #default="{ row }">{{ formatNumber(row.callCount) }}</template>
              </el-table-column>
              <el-table-column prop="tokenCount" label="Token" min-width="80" align="center">
                <template #default="{ row }">{{ formatTokenCount(row.tokenCount) }}</template>
              </el-table-column>
              <el-table-column label="缓存 Token" min-width="80" align="center">
                <template #default="{ row }">
                  <span v-if="row.cachedTokens > 0" class="cache-highlight">
                    {{ formatTokenCount(row.cachedTokens) }}
                  </span>
                  <span v-else class="text-muted">-</span>
                </template>
              </el-table-column>
              <el-table-column prop="cost" label="费用" min-width="72" align="center">
                <template #default="{ row }">${{ row.cost.toFixed(2) }}</template>
              </el-table-column>
            </el-table>
          </div>
        </div>

        <!-- 右侧：最近请求 -->
        <div class="table-module">
          <div class="table-module__header">
            <div class="table-module__title">
              <el-icon :size="16"><Document /></el-icon>
              <span>最近请求</span>
            </div>
            <router-link to="/request-log" class="table-module__link">
              查看全部
              <el-icon :size="12"><ArrowRight /></el-icon>
            </router-link>
          </div>
          <div class="table-module__body">
            <el-table :data="recentRequests" size="default" class="dashboard-table" :show-header="true">
              <el-table-column prop="time" label="时间" width="80" align="center"/>
              <el-table-column prop="model" label="模型" min-width="130" show-overflow-tooltip align="center"/>
              <el-table-column prop="provider" label="通道" min-width="90" show-overflow-tooltip align="center"/>
              <el-table-column prop="duration" label="耗时" width="72" align="center">
                <template #default="{ row }">
                  <span :class="{ 'duration-warn': row.duration > 5000 }">
                    {{ formatMs(row.duration) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="68" align="center">
                <template #default="{ row }">
                  <el-tag
                    :type="statusTagType(row.status)"
                    size="small"
                    class="status-tag"
                    effect="light"
                  >
                    {{ row.status === 'success' ? '成功' : '失败' }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </div>
    </div>
  </ConsoleLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ArrowRight,
  Bell,
  Coin,
  Connection,
  Document,
  Lightning,
  Timer,
  TrendCharts,
} from '@element-plus/icons-vue'
import ConsoleLayout from '../../layout/ConsoleLayout.vue'
import {
  fetchDashboardStats,
  fetchModelUsageRank,
  fetchRecentRequests,
  fetchSystemHealth,
} from '../../api/dashboard'
import type { DashboardPeriod, DashboardStats, ModelUsageRank, RecentRequest } from '../../types/dashboard'

// ==================== 状态 ====================

const activePeriod = ref<DashboardPeriod>('7d')
const systemHealthy = ref(true)
const loading = ref(false)
let latestLoadRequestId = 0

const stats = reactive<DashboardStats>({
  requests: { current: 0, previous: 0, changePercent: 0 },
  cost: { current: 0, previous: 0, changePercent: 0 },
  tokens: { current: 0, previous: 0, changePercent: 0 },
  cacheTokens: { current: 0, previous: 0, changePercent: 0 },
  avgResponseMs: { current: 0, previous: 0, changePercent: 0 },
})
const modelRank = ref<ModelUsageRank[]>([])
const recentRequests = ref<RecentRequest[]>([])

const periodOptions: { label: string; value: DashboardPeriod }[] = [
  { label: '今天', value: 'today' },
  { label: '近7天', value: '7d' },
  { label: '近30天', value: '30d' },
]

// ==================== 格式化 ====================

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
  return Math.round(ms) + 'ms'
}

function trendText(pct: number): string {
  if (pct > 0) return '+' + pct.toFixed(1) + '%'
  if (pct < 0) return pct.toFixed(1) + '%'
  return '0%'
}

function trendClass(pct: number): string {
  if (pct > 0) return 'trend--up'
  if (pct < 0) return 'trend--down'
  return 'trend--flat'
}

function rankClass(rank: number): string {
  if (rank === 1) return 'rank-badge--gold'
  if (rank === 2) return 'rank-badge--silver'
  if (rank === 3) return 'rank-badge--bronze'
  return ''
}

function statusTagType(status: string): string {
  return status === 'success' ? 'success' : 'danger'
}

// ==================== 计算属性 ====================

/** 上周期文案（用于卡片底部说明） */
const periodPrevLabel = computed(() => {
  if (activePeriod.value === 'today') return '昨日'
  if (activePeriod.value === '7d') return '前7天'
  return '前30天'
})

const overviewCards = computed(() => [
  {
    key: 'requests',
    label: '请求数',
    displayValue: formatNumber(stats.requests.current),
    subLabel: `${periodPrevLabel.value} ${formatNumber(stats.requests.previous)}`,
    metric: stats.requests,
    icon: Connection,
    accent: '#4361ee',
    accentBg: 'rgba(67, 97, 238, 0.06)',
  },
  {
    key: 'cost',
    label: '消费金额',
    displayValue: formatCost(stats.cost.current),
    subLabel: `${periodPrevLabel.value} ${formatCost(stats.cost.previous)}`,
    metric: stats.cost,
    icon: Coin,
    accent: '#f59e0b',
    accentBg: 'rgba(245, 158, 11, 0.06)',
  },
  {
    key: 'tokens',
    label: 'Token 消耗',
    displayValue: formatTokenCount(stats.tokens.current),
    subLabel: `${periodPrevLabel.value} ${formatTokenCount(stats.tokens.previous)}`,
    metric: stats.tokens,
    icon: TrendCharts,
    accent: '#10b981',
    accentBg: 'rgba(16, 185, 129, 0.06)',
  },
  {
    key: 'cacheTokens',
    label: '缓存命中',
    displayValue: formatTokenCount(stats.cacheTokens.current),
    subLabel: `${periodPrevLabel.value} ${formatTokenCount(stats.cacheTokens.previous)}`,
    metric: stats.cacheTokens,
    icon: Lightning,
    accent: '#06b6d4',
    accentBg: 'rgba(6, 182, 212, 0.06)',
  },
  {
    key: 'duration',
    label: '平均响应耗时',
    displayValue: formatMs(stats.avgResponseMs.current),
    subLabel: `${periodPrevLabel.value} ${formatMs(stats.avgResponseMs.previous)}`,
    metric: stats.avgResponseMs,
    icon: Timer,
    accent: '#8b5cf6',
    accentBg: 'rgba(139, 92, 246, 0.06)',
  },
])

// ==================== 数据加载 ====================

async function loadAll() {
  const requestId = ++latestLoadRequestId
  loading.value = true
  try {
    const period = activePeriod.value
    const [s, rank, recent] = await Promise.all([
      fetchDashboardStats(period),
      fetchModelUsageRank(period),
      fetchRecentRequests(period),
    ])
    if (requestId !== latestLoadRequestId) {
      return
    }
    Object.assign(stats, s)
    modelRank.value = rank
    recentRequests.value = recent
  } catch {
    if (requestId === latestLoadRequestId) {
      ElMessage.error('仪表盘数据加载失败，请稍后重试')
    }
  } finally {
    if (requestId === latestLoadRequestId) {
      loading.value = false
    }
  }
}

async function loadHealth() {
  try {
    const res = await fetchSystemHealth()
    systemHealthy.value = res.status === 'UP'
  } catch {
    systemHealthy.value = false
  }
}

function switchPeriod(p: DashboardPeriod) {
  if (activePeriod.value === p || loading.value) {
    return
  }
  activePeriod.value = p
  loadAll()
}

onMounted(() => {
  loadAll()
  loadHealth()
})
</script>

<style scoped>
/* 缓存 Token 高亮 */
.cache-highlight {
  color: #06b6d4;
  font-weight: 500;
}

/* 次要文本 */
.text-muted {
  color: var(--text-placeholder);
}
</style>
