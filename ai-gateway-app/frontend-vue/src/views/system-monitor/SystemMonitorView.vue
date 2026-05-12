<template>
  <ConsoleLayout>
    <div class="monitor-page">
      <!-- ====== 顶部 KPI 概览卡片 ====== -->
      <div class="overview-grid">
        <!-- CPU 使用率 -->
        <div class="kpi-card" style="--accent: #4361ee; --accent-bg: rgba(67,97,238,0.08);">
          <div class="kpi-card__icon">
            <el-icon :size="20"><Cpu /></el-icon>
          </div>
          <div class="kpi-card__info">
            <span class="kpi-card__label">系统 CPU</span>
            <span class="kpi-card__value">{{ overview?.systemCpuUsage?.toFixed(1) ?? 0 }}%</span>
          </div>
          <div class="kpi-card__footer">
            进程 {{ overview?.processCpuUsage?.toFixed(1) ?? 0 }}%
          </div>
        </div>

        <!-- 物理内存 -->
        <div class="kpi-card" style="--accent: #10b981; --accent-bg: rgba(16,185,129,0.08);">
          <div class="kpi-card__icon">
            <el-icon :size="20"><Coin /></el-icon>
          </div>
          <div class="kpi-card__info">
            <span class="kpi-card__label">物理内存</span>
            <span class="kpi-card__value">{{ formatBytes(overview?.usedPhysicalMemory ?? 0) }}</span>
          </div>
          <div class="kpi-card__footer">
            共 {{ formatBytes(overview?.totalPhysicalMemory ?? 0) }}
          </div>
        </div>

        <!-- JVM 堆内存 -->
        <div class="kpi-card" style="--accent: #f59e0b; --accent-bg: rgba(245,158,11,0.08);">
          <div class="kpi-card__icon">
            <el-icon :size="20"><DataLine /></el-icon>
          </div>
          <div class="kpi-card__info">
            <span class="kpi-card__label">JVM 堆内存</span>
            <span class="kpi-card__value">{{ formatBytes(overview?.jvmUsedHeap ?? 0) }}</span>
          </div>
          <div class="kpi-card__footer">
            最大 {{ formatBytes(overview?.jvmMaxHeap ?? 0) }}
          </div>
        </div>

        <!-- 线程 -->
        <div class="kpi-card" style="--accent: #8b5cf6; --accent-bg: rgba(139,92,246,0.08);">
          <div class="kpi-card__icon">
            <el-icon :size="20"><SetUp /></el-icon>
          </div>
          <div class="kpi-card__info">
            <span class="kpi-card__label">活跃线程</span>
            <span class="kpi-card__value">{{ overview?.activeThreadCount ?? 0 }}</span>
          </div>
          <div class="kpi-card__footer">
            峰值 {{ overview?.peakThreadCount ?? 0 }}
          </div>
        </div>

        <!-- 连接池 -->
        <div class="kpi-card" style="--accent: #06b6d4; --accent-bg: rgba(6,182,212,0.08);">
          <div class="kpi-card__icon">
            <el-icon :size="20"><Connection /></el-icon>
          </div>
          <div class="kpi-card__info">
            <span class="kpi-card__label">连接池</span>
            <span class="kpi-card__value">{{ overview?.connectionPool?.activeConnections ?? 0 }}</span>
          </div>
          <div class="kpi-card__footer">
            最大 {{ overview?.connectionPool?.maxConnections ?? 0 }}
          </div>
        </div>

        <!-- 运行时长 -->
        <div class="kpi-card" style="--accent: #ec4899; --accent-bg: rgba(236,72,153,0.08);">
          <div class="kpi-card__icon">
            <el-icon :size="20"><Timer /></el-icon>
          </div>
          <div class="kpi-card__info">
            <span class="kpi-card__label">运行时长</span>
            <span class="kpi-card__value">{{ formatUptime(overview?.uptimeSeconds ?? 0) }}</span>
          </div>
          <div class="kpi-card__footer">
            {{ formatStartupTimeBrief(overview?.startupTime ?? 0) }}
          </div>
        </div>
      </div>

      <!-- ====== 实时趋势图表（2x2 网格） ====== -->
      <div class="charts-section">
        <!-- CPU 趋势 -->
        <div class="chart-card">
          <div class="chart-card__header">
            <div class="chart-card__title">
              <el-icon :size="16"><TrendCharts /></el-icon>
              <span>CPU 使用率趋势</span>
            </div>
            <span class="chart-card__hint">实时</span>
          </div>
          <div class="chart-card__body">
            <v-chart class="chart-instance" :option="cpuTrendOption" autoresize />
          </div>
        </div>

        <!-- JVM 堆内存趋势 -->
        <div class="chart-card">
          <div class="chart-card__header">
            <div class="chart-card__title">
              <el-icon :size="16"><Coin /></el-icon>
              <span>JVM 堆内存趋势</span>
            </div>
            <span class="chart-card__hint">实时</span>
          </div>
          <div class="chart-card__body">
            <v-chart class="chart-instance" :option="heapTrendOption" autoresize />
          </div>
        </div>

        <!-- 线程数趋势 -->
        <div class="chart-card">
          <div class="chart-card__header">
            <div class="chart-card__title">
              <el-icon :size="16"><SetUp /></el-icon>
              <span>活跃线程趋势</span>
            </div>
            <span class="chart-card__hint">实时</span>
          </div>
          <div class="chart-card__body">
            <v-chart class="chart-instance" :option="threadTrendOption" autoresize />
          </div>
        </div>

        <!-- 连接池趋势 -->
        <div class="chart-card">
          <div class="chart-card__header">
            <div class="chart-card__title">
              <el-icon :size="16"><Connection /></el-icon>
              <span>连接池趋势</span>
            </div>
            <span class="chart-card__hint">实时</span>
          </div>
          <div class="chart-card__body">
            <v-chart class="chart-instance" :option="connectionTrendOption" autoresize />
          </div>
        </div>
      </div>

      <!-- ====== JVM 详情区（三列布局） ====== -->
      <div v-if="jvmInfo" class="detail-section">
        <div class="detail-section__header">
          <el-icon :size="16"><DataAnalysis /></el-icon>
          <span>JVM 详情</span>
        </div>

        <div class="detail-grid">
          <!-- 内存详情 -->
          <div class="detail-card">
            <div class="detail-card__title">
              <el-icon :size="14"><Coin /></el-icon>
              <span>内存</span>
            </div>
            <div class="detail-card__body">
              <div class="detail-row">
                <span class="detail-row__label">堆内存</span>
                <div class="detail-row__bar-wrap">
                  <el-progress
                    :percentage="jvmInfo.heapMemory.usagePercent"
                    :stroke-width="8"
                    :color="progressColor(jvmInfo.heapMemory.usagePercent)"
                  />
                </div>
                <span class="detail-row__value">
                  {{ formatBytes(jvmInfo.heapMemory.used) }} / {{ formatBytes(jvmInfo.heapMemory.max) }}
                </span>
              </div>
              <div class="detail-row">
                <span class="detail-row__label">非堆内存</span>
                <div class="detail-row__bar-wrap">
                  <el-progress
                    :percentage="jvmInfo.nonHeapMemory.usagePercent"
                    :stroke-width="8"
                    :color="progressColor(jvmInfo.nonHeapMemory.usagePercent)"
                  />
                </div>
                <span class="detail-row__value">
                  {{ formatBytes(jvmInfo.nonHeapMemory.used) }} / {{ formatBytes(jvmInfo.nonHeapMemory.committed) }}
                </span>
              </div>
            </div>
          </div>

          <!-- GC 信息 -->
          <div class="detail-card">
            <div class="detail-card__title">
              <el-icon :size="14"><RefreshRight /></el-icon>
              <span>GC</span>
            </div>
            <div class="detail-card__body">
              <div v-for="gc in jvmInfo.gcInfos" :key="gc.name" class="detail-row">
                <span class="detail-row__label" :title="gc.name">{{ gc.name }}</span>
                <span class="detail-row__value">
                  {{ gc.count }} 次 · {{ formatDuration(gc.totalTimeMs) }}
                </span>
              </div>
              <div v-if="!jvmInfo.gcInfos?.length" class="detail-empty">暂无 GC 数据</div>
            </div>
          </div>

          <!-- 线程详情 -->
          <div class="detail-card">
            <div class="detail-card__title">
              <el-icon :size="14"><SetUp /></el-icon>
              <span>线程</span>
            </div>
            <div class="detail-card__body">
              <div class="detail-row">
                <span class="detail-row__label">累计创建</span>
                <span class="detail-row__value">{{ jvmInfo.threadDetail.totalCount }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-row__label">活跃</span>
                <span class="detail-row__value">{{ jvmInfo.threadDetail.activeCount }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-row__label">峰值</span>
                <span class="detail-row__value">{{ jvmInfo.threadDetail.peakCount }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-row__label">守护</span>
                <span class="detail-row__value">{{ jvmInfo.threadDetail.daemonCount }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-row__label">死锁</span>
                <span
                  class="detail-row__value"
                  :class="{ 'text-danger': jvmInfo.threadDetail.deadlockedCount > 0 }"
                >
                  {{ jvmInfo.threadDetail.deadlockedCount }}
                </span>
              </div>
            </div>
          </div>

          <!-- 类加载 -->
          <div class="detail-card">
            <div class="detail-card__title">
              <el-icon :size="14"><Document /></el-icon>
              <span>类加载</span>
            </div>
            <div class="detail-card__body">
              <div class="detail-row">
                <span class="detail-row__label">已加载</span>
                <span class="detail-row__value">{{ jvmInfo.loadedClassCount.toLocaleString() }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-row__label">加载总数</span>
                <span class="detail-row__value">{{ jvmInfo.totalLoadedClassCount.toLocaleString() }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-row__label">已卸载</span>
                <span class="detail-row__value">{{ jvmInfo.unloadedClassCount.toLocaleString() }}</span>
              </div>
            </div>
          </div>

          <!-- JVM 信息 -->
          <div class="detail-card detail-card--span2">
            <div class="detail-card__title">
              <el-icon :size="14"><InfoFilled /></el-icon>
              <span>JVM 信息</span>
            </div>
            <div class="detail-card__body">
              <div class="detail-row">
                <span class="detail-row__label">名称</span>
                <span class="detail-row__value">{{ jvmInfo.jvmName }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-row__label">版本</span>
                <span class="detail-row__value">{{ jvmInfo.jvmVersion }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-row__label">供应商</span>
                <span class="detail-row__value">{{ jvmInfo.jvmVendor }}</span>
              </div>
              <div v-if="jvmInfo.vmArguments" class="detail-row">
                <span class="detail-row__label">参数</span>
                <span class="detail-row__value detail-row__value--break">{{ jvmInfo.vmArguments }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </ConsoleLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import {
  Connection,
  Coin,
  Cpu,
  DataAnalysis,
  DataLine,
  Document,
  InfoFilled,
  RefreshRight,
  SetUp,
  Timer,
  TrendCharts,
} from '@element-plus/icons-vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
} from 'echarts/components'
import VChart from 'vue-echarts'
import ConsoleLayout from '../../layout/ConsoleLayout.vue'
import { fetchSystemOverview, fetchJvmInfo, fetchSystemRealtime } from '../../api/system-monitor'
import type { SystemOverview, JvmInfo, SystemRealtime } from '../../types/system-monitor'

// 注册 ECharts 组件
use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent])

// ==================== 状态 ====================

const overview = ref<SystemOverview | null>(null)
const jvmInfo = ref<JvmInfo | null>(null)

/** 实时趋势数据队列（最近60个点，约3分钟） */
const MAX_HISTORY = 60
const realtimeHistory = reactive<{
  timestamps: number[]
  systemCpu: number[]
  processCpu: number[]
  jvmHeapUsage: number[]
  jvmUsedHeap: number[]
  activeThreads: number[]
  activeConnections: number[]
}>({
  timestamps: [],
  systemCpu: [],
  processCpu: [],
  jvmHeapUsage: [],
  jvmUsedHeap: [],
  activeThreads: [],
  activeConnections: [],
})

let realtimeTimer: ReturnType<typeof setInterval> | null = null

// ==================== 格式化工具 ====================

/** 格式化字节数 */
function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
  return (bytes / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1) + ' ' + units[i]
}

/** 格式化运行时长 */
function formatUptime(seconds: number): string {
  if (seconds <= 0) return '-'
  const d = Math.floor(seconds / 86400)
  const h = Math.floor((seconds % 86400) / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  if (d > 0) return `${d}天${h}小时`
  if (h > 0) return `${h}小时${m}分`
  return `${m}分钟`
}

/** 格式化启动时间（简短格式，用于 KPI 卡片） */
function formatStartupTimeBrief(ms: number): string {
  if (!ms) return '-'
  const d = new Date(ms)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** 格式化毫秒时长 */
function formatDuration(ms: number): string {
  if (ms < 1000) return ms + 'ms'
  return (ms / 1000).toFixed(2) + 's'
}

/** 根据使用率返回进度条颜色 */
function progressColor(percent: number): string {
  if (percent >= 90) return '#ef4444'
  if (percent >= 70) return '#f59e0b'
  return '#10b981'
}

// ==================== ECharts 配置 ====================

/** 通用折线趋势配置 */
function buildTrendOption(
  labels: string[],
  series: Array<{ name: string; data: number[]; color: string; area?: boolean }>,
  yUnit: string = ''
) {
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
    },
    legend: {
      data: series.map(s => s.name),
      bottom: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { fontSize: 11 },
    },
    grid: { left: 10, right: 10, top: 30, bottom: 30, containLabel: true },
    xAxis: {
      type: 'category',
      data: labels,
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisLabel: { color: '#94a3b8', fontSize: 11, formatter: (v: string) => v.slice(-5) },
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#f1f5f9' } },
      axisLabel: { color: '#94a3b8', fontSize: 11, formatter: `{value}${yUnit}` },
    },
    series: series.map(s => ({
      name: s.name,
      type: 'line',
      data: s.data,
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 2, color: s.color },
      ...(s.area ? {
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: s.color + '30' },
              { offset: 1, color: s.color + '05' },
            ],
          },
        },
      } : {}),
    })),
  }
}

const cpuTrendOption = computed(() => {
  const labels = realtimeHistory.timestamps.map(t => new Date(t).toLocaleTimeString('zh-CN'))
  return buildTrendOption(labels, [
    { name: '系统 CPU', data: realtimeHistory.systemCpu, color: '#4361ee', area: true },
    { name: '进程 CPU', data: realtimeHistory.processCpu, color: '#8b5cf6' },
  ], '%')
})

const heapTrendOption = computed(() => {
  const labels = realtimeHistory.timestamps.map(t => new Date(t).toLocaleTimeString('zh-CN'))
  return buildTrendOption(labels, [
    { name: '堆使用率', data: realtimeHistory.jvmHeapUsage, color: '#f59e0b', area: true },
  ], '%')
})

const threadTrendOption = computed(() => {
  const labels = realtimeHistory.timestamps.map(t => new Date(t).toLocaleTimeString('zh-CN'))
  return buildTrendOption(labels, [
    { name: '活跃线程', data: realtimeHistory.activeThreads, color: '#8b5cf6', area: true },
  ])
})

const connectionTrendOption = computed(() => {
  const labels = realtimeHistory.timestamps.map(t => new Date(t).toLocaleTimeString('zh-CN'))
  return buildTrendOption(labels, [
    { name: '活跃连接', data: realtimeHistory.activeConnections, color: '#06b6d4', area: true },
  ])
})

// ==================== 数据加载 ====================

async function loadOverview() {
  try {
    overview.value = await fetchSystemOverview()
  } catch {
    // 请求拦截器已统一处理
  }
}

async function loadJvmInfo() {
  try {
    jvmInfo.value = await fetchJvmInfo()
  } catch {
    // 请求拦截器已统一处理
  }
}

async function loadRealtime() {
  try {
    const data: SystemRealtime = await fetchSystemRealtime()
    // 追加到历史队列
    realtimeHistory.timestamps.push(data.timestamp)
    realtimeHistory.systemCpu.push(data.systemCpuUsage)
    realtimeHistory.processCpu.push(data.processCpuUsage)
    realtimeHistory.jvmHeapUsage.push(data.jvmHeapUsage)
    realtimeHistory.jvmUsedHeap.push(data.jvmUsedHeap)
    realtimeHistory.activeThreads.push(data.activeThreadCount)
    realtimeHistory.activeConnections.push(data.activeConnections)
    // 超过最大长度则移除最早的数据（滑动窗口）
    if (realtimeHistory.timestamps.length > MAX_HISTORY) {
      const keys = ['timestamps', 'systemCpu', 'processCpu', 'jvmHeapUsage', 'jvmUsedHeap', 'activeThreads', 'activeConnections'] as const
      keys.forEach(k => realtimeHistory[k].shift())
    }
  } catch {
    // 实时指标非关键数据，静默失败
  }
}

function onVisibilityChange() {
  if (document.hidden) {
    if (realtimeTimer) {
      clearInterval(realtimeTimer)
      realtimeTimer = null
    }
  } else {
    // 恢复可见时刷新所有数据，避免展示过期信息
    loadOverview()
    loadJvmInfo()
    loadRealtime()
    if (!realtimeTimer) {
      realtimeTimer = setInterval(loadRealtime, 3000)
    }
  }
}

onMounted(() => {
  loadOverview()
  loadJvmInfo()
  loadRealtime()
  realtimeTimer = setInterval(loadRealtime, 3000)
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
.monitor-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ====== KPI 概览卡片（一行6个，紧凑布局） ====== */
.overview-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
}

.kpi-card {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 12px;
  padding: 16px;
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: box-shadow var(--transition-normal), transform var(--transition-normal);
}

.kpi-card:hover {
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}

.kpi-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--accent-bg);
  color: var(--accent);
}

.kpi-card__info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.kpi-card__label {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-secondary);
}

.kpi-card__value {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.kpi-card__footer {
  font-size: 11px;
  color: var(--text-placeholder);
  margin-top: auto;
}

/* ====== 图表区（2x2 网格） ====== */
.charts-section {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
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
  transition: box-shadow var(--transition-normal);
}

.chart-card:hover {
  box-shadow: var(--shadow-card-hover);
}

.chart-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
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

.chart-card__hint {
  font-size: 11px;
  color: var(--text-placeholder);
  background: var(--bg-page);
  border: 1px solid var(--border-light);
  padding: 2px 8px;
  border-radius: 999px;
}

.chart-card__body {
  flex: 1;
  padding: 10px 14px 14px;
  min-height: 220px;
}

.chart-instance {
  width: 100%;
  height: 220px;
}

/* ====== JVM 详情区 ====== */
.detail-section {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 14px;
  box-shadow: var(--shadow-card);
  overflow: hidden;
}

.detail-section__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-light);
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  background: var(--border-light);
}

.detail-card {
  background: var(--bg-card);
  padding: 16px 20px;
}

.detail-card--span2 {
  grid-column: span 2;
}

.detail-card__title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: 12px;
}

.detail-card__body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 24px;
}

.detail-row__label {
  font-size: 12px;
  color: var(--text-secondary);
  min-width: 60px;
  flex-shrink: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.detail-row__bar-wrap {
  flex: 1;
  min-width: 60px;
}

.detail-row__value {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  text-align: right;
}

.detail-row__value--break {
  white-space: normal;
  word-break: break-all;
  text-align: left;
  font-weight: 500;
  font-size: 11px;
}

.text-danger {
  color: #ef4444 !important;
  font-weight: 700;
}

.detail-empty {
  font-size: 12px;
  color: var(--text-placeholder);
  text-align: center;
  padding: 4px 0;
}

/* ====== 响应式 ====== */
@media (max-width: 1400px) {
  .overview-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 1100px) {
  .charts-section {
    grid-template-columns: 1fr;
  }

  .detail-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .detail-card--span2 {
    grid-column: span 2;
  }
}

@media (max-width: 768px) {
  .overview-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }

  .detail-card--span2 {
    grid-column: span 1;
  }
}

@media (max-width: 480px) {
  .overview-grid {
    grid-template-columns: 1fr;
  }
}
</style>
