<template>
  <ConsoleLayout>
    <div class="dashboard-page">
      <!-- ====== 顶部：系统状态 + 时间切换 ====== -->
      <div class="dashboard-header">
        <div class="dashboard-header__left">
          <div class="dashboard-header__status">
            <span
              class="status-capsule"
              :class="systemHealthy ? 'status-capsule--up' : 'status-capsule--down'"
            >
              <span class="status-capsule__dot" />
              {{ systemHealthy ? '系统正常' : '系统异常' }}
            </span>
          </div>
        </div>
        <div class="dashboard-header__right">
          <!-- 实时指标条 -->
          <div class="realtime-bar">
            <div class="realtime-item">
              <span class="realtime-item__label">RPM</span>
              <span class="realtime-item__value">{{ formatNumber(realtime?.rpm ?? 0) }}</span>
            </div>
            <div class="realtime-divider" />
            <div class="realtime-item">
              <span class="realtime-item__label">TPM</span>
              <span class="realtime-item__value">{{ formatTokenCount(realtime?.tpm ?? 0) }}</span>
            </div>
            <div class="realtime-divider" />
            <div class="realtime-item">
              <span class="realtime-item__label">成功率</span>
              <span class="realtime-item__value" :class="(realtime?.successRate ?? 100) >= 99 ? 'text-success' : 'text-warning'">
                {{ (realtime?.successRate ?? 100).toFixed(1) }}%
              </span>
            </div>
            <div class="realtime-divider" />
            <div class="realtime-item">
              <span class="realtime-item__label">活跃通道</span>
              <span class="realtime-item__value">{{ realtime?.activeProviders ?? 0 }}</span>
            </div>
            <div class="realtime-divider group-divider" />
            <div class="realtime-item">
              <span class="realtime-item__label">缓存命中</span>
              <span class="realtime-item__value">{{ formatTokenCount(stats.cacheTokens.current) }}</span>
            </div>
            <div class="realtime-divider" />
            <div class="realtime-item">
              <span class="realtime-item__label">提供商</span>
              <span class="realtime-item__value">{{ formatNumber(stats.providerCount) }}</span>
            </div>
            <div class="realtime-divider" />
            <div class="realtime-item">
              <span class="realtime-item__label">重定向规则</span>
              <span class="realtime-item__value">{{ formatNumber(stats.redirectCount) }}</span>
            </div>
          </div>
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

      <!-- ====== 核心 KPI 卡片（第一行：5张核心指标） ====== -->
      <div class="overview-section">
        <div class="section-label">核心指标</div>
        <div v-loading="loading" class="overview-grid overview-grid--primary">
          <div
            v-for="card in primaryCards"
            :key="card.key"
            class="overview-card overview-card--primary"
            :style="{ '--card-accent': card.accent, '--card-accent-bg': card.accentBg }"
          >
            <div class="overview-card__deco" />
            <div class="overview-card__top">
              <div class="overview-card__icon-wrap">
                <el-icon :size="20" :color="card.accent">
                  <component :is="card.icon" />
                </el-icon>
              </div>
              <span
                v-if="!card.hideTrend"
                class="overview-card__trend"
                :class="trendClass(card.metric.changePercent)"
              >
                {{ trendText(card.metric.changePercent) }}
              </span>
            </div>
            <div class="overview-card__value">{{ card.displayValue }}</div>
            <div class="overview-card__sub">{{ card.subLabel }}</div>
            <!-- 迷你趋势条 -->
            <div v-if="card.sparklineData && card.sparklineData.length > 1" class="sparkline">
              <v-chart
                class="sparkline-chart"
                :option="buildSparklineOption(card.sparklineData, card.accent)"
                autoresize
              />
            </div>
          </div>
        </div>
      </div>

      <!-- ====== 图表区：2x2 网格 ====== -->
      <div class="charts-section">
        <!-- 请求量趋势 -->
        <div class="chart-card">
          <div class="chart-card__header">
            <div class="chart-card__title">
              <el-icon :size="16"><TrendCharts /></el-icon>
              <span>请求量趋势</span>
            </div>
          </div>
          <div class="chart-card__body">
            <v-chart
              v-if="trend && trend.labels.length"
              class="chart-instance"
              :option="requestTrendOption"
              autoresize
            />
            <div v-else class="chart-empty">暂无数据</div>
          </div>
        </div>

        <!-- Token / 费用趋势 -->
        <div class="chart-card">
          <div class="chart-card__header">
            <div class="chart-card__title">
              <el-icon :size="16"><Coin /></el-icon>
              <span>Token & 费用趋势</span>
            </div>
          </div>
          <div class="chart-card__body">
            <v-chart
              v-if="trend && trend.labels.length"
              class="chart-instance"
              :option="tokenCostTrendOption"
              autoresize
            />
            <div v-else class="chart-empty">暂无数据</div>
          </div>
        </div>

        <!-- 提供商调用分布 -->
        <div class="chart-card">
          <div class="chart-card__header">
            <div class="chart-card__title">
              <el-icon :size="16"><OfficeBuilding /></el-icon>
              <span>提供商调用分布</span>
            </div>
          </div>
          <div class="chart-card__body">
            <v-chart
              v-if="providerDist && providerDist.items.length"
              class="chart-instance"
              :option="providerDistOption"
              autoresize
            />
            <div v-else class="chart-empty">暂无数据</div>
          </div>
        </div>

        <!-- 缓存命中率趋势 -->
        <div class="chart-card">
          <div class="chart-card__header">
            <div class="chart-card__title">
              <el-icon :size="16"><Lightning /></el-icon>
              <span>缓存命中率趋势</span>
            </div>
          </div>
          <div class="chart-card__body">
            <v-chart
              v-if="trend && trend.labels.length"
              class="chart-instance"
              :option="cacheHitRateOption"
              autoresize
            />
            <div v-else class="chart-empty">暂无数据</div>
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
              <el-table-column
                prop="modelName"
                label="请求模型"
                min-width="120"
                show-overflow-tooltip
                align="center"
              />
              <el-table-column
                prop="targetModel"
                label="目标模型"
                min-width="120"
                show-overflow-tooltip
                align="center"
              />
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
            <el-table
              :data="recentRequests"
              size="default"
              class="dashboard-table"
              :show-header="true"
            >
              <el-table-column prop="time" label="时间" width="80" align="center" />
              <el-table-column
                prop="model"
                label="模型"
                min-width="130"
                show-overflow-tooltip
                align="center"
              />
              <el-table-column
                prop="provider"
                label="通道"
                min-width="90"
                show-overflow-tooltip
                align="center"
              />
              <el-table-column prop="tokens" label="Token" width="72" align="center">
                <template #default="{ row }">{{ formatTokenCount(row.tokens) }}</template>
              </el-table-column>
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

      <!-- ====== 错误分析（可折叠） ====== -->
      <div v-if="errorSummary && errorSummary.totalErrors > 0" class="error-section">
        <el-collapse>
          <el-collapse-item>
            <template #title>
              <div class="error-section__title">
                <el-icon :size="16" color="#ef4444"><Warning /></el-icon>
                <span>错误分析</span>
                <el-tag type="danger" size="small" effect="light" class="error-count-tag">
                  {{ errorSummary.totalErrors }} 次错误
                </el-tag>
              </div>
            </template>
            <div class="error-section__body">
              <div class="error-chart-wrapper">
                <v-chart
                  class="error-chart"
                  :option="errorSummaryOption"
                  autoresize
                />
              </div>
              <el-table :data="errorSummary.items" size="small" class="dashboard-table">
                <el-table-column prop="errorCode" label="错误码" min-width="140" />
                <el-table-column prop="errorCount" label="次数" width="90" align="right">
                  <template #default="{ row }">{{ formatNumber(row.errorCount) }}</template>
                </el-table-column>
                <el-table-column prop="percent" label="占比" width="90" align="right">
                  <template #default="{ row }">{{ row.percent.toFixed(1) }}%</template>
                </el-table-column>
              </el-table>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>
    </div>
  </ConsoleLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ArrowRight,
  Bell,
  Coin,
  Connection,
  Document,
  Lightning,
  OfficeBuilding,
  Timer,
  TrendCharts,
  Warning,
} from '@element-plus/icons-vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  DataZoomComponent,
} from 'echarts/components'
import VChart from 'vue-echarts'
import ConsoleLayout from '../../layout/ConsoleLayout.vue'
import {
  fetchDashboardStats,
  fetchDashboardTrend,
  fetchErrorSummary,
  fetchModelUsageRank,
  fetchProviderDistribution,
  fetchRealtimeMetrics,
  fetchRecentRequests,
  fetchSystemHealth,
} from '../../api/dashboard'
import type {
  DashboardPeriod,
  DashboardStats,
  DashboardTrend,
  ErrorSummary,
  ModelUsageRank,
  ProviderDistribution,
  RealtimeMetrics,
  RecentRequest,
} from '../../types/dashboard'

// 注册 ECharts 组件
use([CanvasRenderer, LineChart, BarChart, PieChart, GridComponent, TooltipComponent, LegendComponent, DataZoomComponent])

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
  successRate: { current: 100, previous: 100, changePercent: 0 },
  providerCount: 0,
  redirectCount: 0,
})
const modelRank = ref<ModelUsageRank[]>([])
const recentRequests = ref<RecentRequest[]>([])
const trend = ref<DashboardTrend | null>(null)
const providerDist = ref<ProviderDistribution | null>(null)
const errorSummary = ref<ErrorSummary | null>(null)
const realtime = ref<RealtimeMetrics | null>(null)

const periodOptions: { label: string; value: DashboardPeriod }[] = [
  { label: '今天', value: 'today' },
  { label: '近7天', value: '7d' },
  { label: '近30天', value: '30d' },
]

let realtimeTimer: ReturnType<typeof setInterval> | null = null

function onVisibilityChange() {
  if (document.hidden) {
    if (realtimeTimer) {
      clearInterval(realtimeTimer)
      realtimeTimer = null
    }
  } else {
    loadRealtime()
    if (!realtimeTimer) {
      realtimeTimer = setInterval(loadRealtime, 15000)
    }
  }
}

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

const periodPrevLabel = computed(() => {
  if (activePeriod.value === 'today') return '昨日'
  if (activePeriod.value === '7d') return '前7天'
  return '前30天'
})



const primaryCards = computed(() => [
  {
    key: 'requests',
    label: '请求数',
    displayValue: formatNumber(stats.requests.current),
    subLabel: `${periodPrevLabel.value} ${formatNumber(stats.requests.previous)}`,
    metric: stats.requests,
    icon: Connection,
    accent: '#4361ee',
    accentBg: 'rgba(67, 97, 238, 0.06)',
    sparklineData: trend.value?.requestCounts ?? [],
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
    sparklineData: trend.value?.costs ?? [],
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
    sparklineData: trend.value?.tokenCounts ?? [],
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
    sparklineData: [],
    hideTrend: true,
  },
  {
    key: 'successRate',
    label: '请求成功率',
    displayValue: stats.successRate.current.toFixed(1) + '%',
    subLabel: `${periodPrevLabel.value} ${stats.successRate.previous.toFixed(1)}%`,
    metric: stats.successRate,
    icon: Lightning,
    accent: '#06b6d4',
    accentBg: 'rgba(6, 182, 212, 0.06)',
    sparklineData: trend.value?.successRates ?? [],
  },
])

// ==================== ECharts 配置 ====================

function buildSparklineOption(data: number[], color: string) {
  return {
    grid: { left: 0, right: 0, top: 2, bottom: 2 },
    xAxis: { type: 'category', show: false, data: data.map((_, i) => i) },
    yAxis: { type: 'value', show: false },
    tooltip: { show: false },
    series: [{
      type: 'line',
      data,
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 2, color },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: color + '40' },
            { offset: 1, color: color + '05' },
          ],
        },
      },
    }],
  }
}

const requestTrendOption = computed(() => {
  if (!trend.value) return {}
  const t = trend.value
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: { data: ['请求数', '成功率'], bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 11 } },
    grid: { left: 10, right: 10, top: 30, bottom: 30, containLabel: true },
    xAxis: { type: 'category', data: t.labels, axisLine: { lineStyle: { color: '#e2e8f0' } }, axisLabel: { color: '#94a3b8', fontSize: 11 } },
    yAxis: [
      { type: 'value', name: '请求', nameTextStyle: { padding: [0, 0, 0, -20] }, axisLine: { show: false }, splitLine: { lineStyle: { color: '#f1f5f9' } }, axisLabel: { color: '#94a3b8', fontSize: 11 } },
      { type: 'value', name: '%', nameTextStyle: { padding: [0, -20, 0, 0] }, min: 0, max: 100, axisLine: { show: false }, splitLine: { show: false }, axisLabel: { color: '#94a3b8', fontSize: 11, formatter: '{value}%' } },
    ],
    series: [
      {
        name: '请求数',
        type: 'bar',
        data: t.requestCounts,
        itemStyle: { color: '#4361ee', borderRadius: [4, 4, 0, 0] },
        barMaxWidth: 24,
      },
      {
        name: '成功率',
        type: 'line',
        yAxisIndex: 1,
        data: t.successRates,
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        itemStyle: { color: '#10b981' },
        lineStyle: { width: 2 },
      },
    ],
  }
})

const tokenCostTrendOption = computed(() => {
  if (!trend.value) return {}
  const t = trend.value
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: { data: ['Token', '费用'], bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 11 } },
    grid: { left: 10, right: 10, top: 30, bottom: 30, containLabel: true },
    xAxis: { type: 'category', data: t.labels, axisLine: { lineStyle: { color: '#e2e8f0' } }, axisLabel: { color: '#94a3b8', fontSize: 11 } },
    yAxis: [
      { type: 'value', name: 'Token', nameTextStyle: { padding: [0, 0, 0, -20] }, axisLine: { show: false }, splitLine: { lineStyle: { color: '#f1f5f9' } }, axisLabel: { color: '#94a3b8', fontSize: 11 } },
      { type: 'value', name: '$', nameTextStyle: { padding: [0, -20, 0, 0] }, axisLine: { show: false }, splitLine: { show: false }, axisLabel: { color: '#94a3b8', fontSize: 11 } },
    ],
    series: [
      {
        name: 'Token',
        type: 'bar',
        data: t.tokenCounts,
        itemStyle: { color: '#10b981', borderRadius: [4, 4, 0, 0] },
        barMaxWidth: 24,
      },
      {
        name: '费用',
        type: 'line',
        yAxisIndex: 1,
        data: t.costs,
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        itemStyle: { color: '#f59e0b' },
        lineStyle: { width: 2 },
      },
    ],
  }
})

const providerDistOption = computed(() => {
  if (!providerDist.value) return {}
  const items = providerDist.value.items
  const colors = ['#4361ee', '#10b981', '#f59e0b', '#8b5cf6', '#06b6d4', '#ec4899', '#6366f1']
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} 次 ({d}%)' },
    legend: { orient: 'vertical', right: 10, top: 'center', icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 11 } },
    series: [
      {
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['35%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        emphasis: { label: { show: true, fontSize: 13, fontWeight: 'bold' } },
        data: items.map((item, idx) => ({
          name: item.providerCode,
          value: item.requestCount,
          itemStyle: { color: colors[idx % colors.length] },
        })),
      },
    ],
  }
})

const cacheHitRateOption = computed(() => {
  if (!trend.value) return {}
  const t = trend.value
  return {
    tooltip: { trigger: 'axis', formatter: (params: unknown) => {
      const p = params as Array<{ name: string; value: number }>
      return `${p[0].name}<br/>命中率: ${p[0].value.toFixed(1)}%`
    } },
    grid: { left: 10, right: 10, top: 20, bottom: 20, containLabel: true },
    xAxis: { type: 'category', data: t.labels, axisLine: { lineStyle: { color: '#e2e8f0' } }, axisLabel: { color: '#94a3b8', fontSize: 11 } },
    yAxis: { type: 'value', min: 0, max: 100, axisLine: { show: false }, splitLine: { lineStyle: { color: '#f1f5f9' } }, axisLabel: { color: '#94a3b8', fontSize: 11, formatter: '{value}%' } },
    series: [
      {
        type: 'line',
        data: t.cacheHitRates,
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 2, color: '#06b6d4' },
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(6, 182, 212, 0.25)' },
              { offset: 1, color: 'rgba(6, 182, 212, 0.02)' },
            ],
          },
        },
      },
    ],
  }
})

const errorSummaryOption = computed(() => {
  if (!errorSummary.value) return {}
  const items = errorSummary.value.items
  const colors = ['#ef4444', '#f59e0b', '#f97316', '#eab308', '#84cc16', '#10b981', '#06b6d4']
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 10, right: 30, top: 10, bottom: 10, containLabel: true },
    xAxis: { type: 'value', axisLine: { show: false }, splitLine: { lineStyle: { color: '#f1f5f9' } }, axisLabel: { color: '#94a3b8', fontSize: 11 } },
    yAxis: { type: 'category', data: [...items].reverse().map(i => i.errorCode), axisLine: { lineStyle: { color: '#e2e8f0' } }, axisLabel: { color: '#64748b', fontSize: 12 } },
    series: [
      {
        type: 'bar',
        data: [...items].reverse().map((item, idx) => ({
          value: item.errorCount,
          itemStyle: { color: colors[idx % colors.length], borderRadius: [0, 4, 4, 0] },
        })),
        barMaxWidth: 20,
        label: { show: true, position: 'right', formatter: '{c}', color: '#64748b', fontSize: 11 },
      },
    ],
  }
})

// ==================== 数据加载 ====================

async function loadAll() {
  const requestId = ++latestLoadRequestId
  loading.value = true
  try {
    const period = activePeriod.value
    const [s, rank, recent, tr, pd, es] = await Promise.all([
      fetchDashboardStats(period),
      fetchModelUsageRank(period),
      fetchRecentRequests(period),
      fetchDashboardTrend(period),
      fetchProviderDistribution(period),
      fetchErrorSummary(period),
    ])
    if (requestId !== latestLoadRequestId) {
      return
    }
    Object.assign(stats, s)
    modelRank.value = rank
    recentRequests.value = recent
    trend.value = tr
    providerDist.value = pd
    errorSummary.value = es
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

async function loadRealtime() {
  try {
    const res = await fetchRealtimeMetrics()
    realtime.value = res
  } catch {
    // 静默失败，实时指标非关键
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
  loadRealtime()
  realtimeTimer = setInterval(loadRealtime, 15000)
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  if (realtimeTimer) {
    clearInterval(realtimeTimer)
  }
  document.removeEventListener('visibilitychange', onVisibilityChange)
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

.text-success {
  color: #10b981;
}

.text-warning {
  color: #f59e0b;
}

/* 实时指标条 */
.realtime-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 4px 0;
}

.realtime-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}

.realtime-item__label {
  font-size: 10px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.realtime-item__value {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.realtime-divider {
  width: 1px;
  height: 22px;
  background: var(--border-light);
}

/* 迷你趋势条 */
.sparkline {
  margin-top: 12px;
  height: 40px;
  position: relative;
}

.sparkline-chart {
  width: 100%;
  height: 100%;
}

/* 分组分隔线 */
.realtime-divider.group-divider {
  height: 28px;
  background: var(--border-default);
  margin: 0 4px;
}

/* 图表区 */
.charts-section {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.chart-card {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 14px;
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.chart-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 18px 10px;
  border-bottom: 1px solid var(--border-light);
}

.chart-card__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.chart-card__body {
  flex: 1;
  padding: 10px 14px 14px;
  min-height: 240px;
  position: relative;
}

.chart-instance {
  width: 100%;
  height: 240px;
}

.chart-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 240px;
  color: var(--text-placeholder);
  font-size: 13px;
}

/* 错误分析 */
.error-section {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 14px;
  box-shadow: var(--shadow-card);
  overflow: hidden;
}

.error-section :deep(.el-collapse) {
  border: none;
}

.error-section :deep(.el-collapse-item__header) {
  padding: 14px 18px;
  border-bottom: 1px solid var(--border-light);
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.error-section :deep(.el-collapse-item__wrap) {
  border-bottom: none;
}

.error-section :deep(.el-collapse-item__content) {
  padding: 0;
}

.error-section__title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.error-count-tag {
  margin-left: 8px;
  font-weight: 600;
}

.error-section__body {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  padding: 16px 18px;
}

.error-chart-wrapper {
  min-height: 200px;
}

.error-chart {
  width: 100%;
  height: 200px;
}

/* 响应式 */
@media (max-width: 1280px) {
  .charts-section {
    grid-template-columns: 1fr;
  }

  .error-section__body {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 1024px) {
  .overview-grid--primary {
    grid-template-columns: repeat(3, 1fr);
  }

  .overview-grid--secondary {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 768px) {
  .dashboard-header {
    flex-direction: column;
    align-items: stretch;
  }

  .dashboard-header__left {
    flex-wrap: wrap;
  }

  .realtime-bar {
    flex-wrap: wrap;
  }

  .overview-grid--primary,
  .overview-grid--secondary {
    grid-template-columns: repeat(2, 1fr);
  }

  .tables-section {
    grid-template-columns: 1fr;
  }
}
</style>
