<template>
  <ConsoleLayout>
    <div class="page-card">
      <div class="card-header">
        <span class="card-header__title">请求日志</span>
        <div class="card-header__actions">
          <el-button size="small" @click="loadData">刷新</el-button>
        </div>
      </div>

      <el-form :inline="true" :model="query" class="filter-bar">
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width: 360px"
            size="default"
            value-format="YYYY-MM-DDTHH:mm:ss"
            @change="onDateRangeChange"
          />
        </el-form-item>
        <el-form-item label="请求 ID">
          <el-input
            v-model="query.requestId"
            placeholder="精确搜索"
            clearable
            size="default"
            style="width: 220px"
          />
        </el-form-item>
        <el-form-item label="提供商类型">
          <el-select
            v-model="query.providerType"
            placeholder="全部"
            style="width: 150px"
            size="default"
            clearable
          >
            <el-option
              v-for="provider in PROVIDER_OPTIONS"
              :key="provider.value"
              :label="provider.label"
              :value="provider.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="通道">
          <el-input
            v-model="query.providerCode"
            placeholder="providerCode"
            clearable
            size="default"
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="全部"
            style="width: 120px"
            size="default"
            clearable
          >
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="ERROR" />
            <el-option label="已取消" value="CANCELLED" />
            <el-option label="已拒绝" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型别名">
          <el-input
            v-model="query.aliasModel"
            placeholder="模糊搜索"
            clearable
            size="default"
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item label="流式">
          <el-select v-model="query.isStream" placeholder="全部" style="width: 100px" clearable>
            <el-option label="是" :value="true" />
            <el-option label="否" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item label="重试">
          <el-select v-model="query.hasRetry" placeholder="全部" style="width: 100px" clearable>
            <el-option label="有" :value="true" />
            <el-option label="无" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item label="Failover">
          <el-select v-model="query.hasFailover" placeholder="全部" style="width: 110px" clearable>
            <el-option label="有" :value="true" />
            <el-option label="无" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="default" @click="search">查询</el-button>
          <el-button size="default" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="page.list" stripe element-loading-text="正在加载...">
        <el-table-column label="请求时间" min-width="170" align="center">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column prop="aliasModel" label="请求模型" min-width="140" show-overflow-tooltip align="center" />
        <el-table-column prop="targetModel" label="目标模型" min-width="140" show-overflow-tooltip align="center" />
        <el-table-column prop="providerCode" label="通道" min-width="120" show-overflow-tooltip align="center" />
        <el-table-column label="提供商类型" min-width="127" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="providerTagType(row.providerType)">
              {{ providerLabel(row.providerType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="治理" min-width="180" align="center">
          <template #default="{ row }">
            <div class="governance-tags">
              <el-tag v-if="row.retryCount && row.retryCount > 0" type="warning" size="small">
                重试 {{ row.retryCount }}
              </el-tag>
              <el-tag v-if="row.failoverCount && row.failoverCount > 0" type="danger" size="small">
                Failover {{ row.failoverCount }}
              </el-tag>
              <el-tag v-if="row.rateLimitTriggered" type="info" size="small">限流</el-tag>
              <span
                v-if="!hasGovernanceSignals(row)"
                class="text-muted"
              >-</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="流式" min-width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isStream" type="success" size="small">是</el-tag>
            <el-tag v-else type="info" size="small">否</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Token 用量" min-width="190" align="center">
          <template #default="{ row }">
            <span v-if="row.totalTokens != null" class="token-usage">
              <span class="token-total">{{ row.totalTokens.toLocaleString() }}</span>
              <span class="token-detail">({{ row.promptTokens ?? 0 }} / {{ row.completionTokens ?? 0 }})</span>
              <span v-if="(row.cachedInputTokens ?? 0) > 0" class="token-cached">
                缓存: {{ row.cachedInputTokens?.toLocaleString() }}
              </span>
            </span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" min-width="90" align="center">
          <template #default="{ row }">
            <span v-if="row.durationMs != null">{{ formatDuration(row.durationMs) }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="terminalStage" label="终止阶段" min-width="110" show-overflow-tooltip align="center">
          <template #default="{ row }">
            <span v-if="row.terminalStage">{{ terminalStageLabel(row.terminalStage) }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="errorCode" label="错误码" min-width="100" show-overflow-tooltip align="center">
          <template #default="{ row }">
            <span v-if="row.errorCode">{{ row.errorCode }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="错误详情" min-width="170" show-overflow-tooltip align="center">
          <template #default="{ row }">
            <span v-if="row.errorMessage" class="error-detail">{{ row.errorMessage }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.requestId)">详情</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <div class="table-empty-state">
            <template v-if="loadError">
              <strong>加载失败</strong>
              <p>请检查后端服务后重试</p>
              <div class="table-empty-state__actions">
                <el-button type="primary" size="small" @click="loadData">重新加载</el-button>
              </div>
            </template>
            <template v-else>
              <strong>{{ hasActiveFilters ? '没有匹配的请求日志' : '暂无请求日志' }}</strong>
              <p>{{ hasActiveFilters ? '尝试重置筛选条件' : '当有 API 请求经过网关时，日志将自动记录在此' }}</p>
              <div class="table-empty-state__actions">
                <el-button v-if="hasActiveFilters" size="small" @click="resetQuery">重置筛选</el-button>
              </div>
            </template>
          </div>
        </template>
      </el-table>

      <div class="pager-bar">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50, 100]"
          :total="page.total"
          @current-change="loadData"
          @size-change="onPageSizeChange"
        />
      </div>
    </div>

    <el-drawer v-model="detailVisible" title="请求日志详情" size="720px" destroy-on-close>
      <template v-if="detailLoading">
        <div class="detail-loading">
          <el-skeleton :rows="12" animated />
        </div>
      </template>
      <template v-else-if="detailData">
        <div class="detail-panel">
          <div class="detail-section">
            <div class="detail-section__title">基础信息</div>
            <div class="detail-grid">
              <DetailItem label="请求 ID" :value="detailData.requestId" copyable />
              <DetailItem label="请求时间" :value="formatTime(detailData.createTime)" />
              <DetailItem label="请求路径" :value="detailData.requestPath" />
              <DetailItem label="请求方法" :value="detailData.httpMethod" />
              <DetailItem label="提供商类型" :value="providerLabel(detailData.providerType)" />
              <DetailItem label="响应协议" :value="detailData.responseProtocol" />
              <DetailItem label="模型别名" :value="detailData.aliasModel" />
              <DetailItem label="目标模型" :value="detailData.targetModel" />
              <DetailItem label="通道" :value="detailData.providerCode" />
              <DetailItem label="来源 IP" :value="maskIp(detailData.sourceIp)" />
              <DetailItem label="API Key 前缀" :value="detailData.apiKeyPrefix" />
              <DetailItem label="流式请求" :value="booleanLabel(detailData.isStream)" />
            </div>
          </div>

          <div class="detail-section">
            <div class="detail-section__title">执行结果</div>
            <div class="detail-grid">
              <DetailItem label="状态" :value="statusLabel(detailData.status)" :highlight="isErrorStatus(detailData.status)" />
              <DetailItem label="终止阶段" :value="terminalStageLabel(detailData.terminalStage)" :highlight="isErrorStatus(detailData.status)" />
              <DetailItem label="总耗时" :value="formatNullableDuration(detailData.durationMs)" />
              <DetailItem label="错误码" :value="detailData.errorCode" :highlight="Boolean(detailData.errorCode)" />
              <DetailItem label="错误详情" :value="detailData.errorMessage" :highlight="Boolean(detailData.errorMessage)" />
              <DetailItem label="上游状态码" :value="formatNullableNumber(detailData.upstreamHttpStatus)" />
              <DetailItem label="上游错误类型" :value="detailData.upstreamErrorType" />
            </div>
          </div>

          <div class="detail-section">
            <div class="detail-section__title">路由与治理</div>
            <div class="detail-grid">
              <DetailItem label="候选路由数" :value="formatNullableNumber(detailData.candidateCount)" />
              <DetailItem label="尝试次数" :value="formatNullableNumber(detailData.attemptCount)" />
              <DetailItem label="重试次数" :value="formatNullableNumber(detailData.retryCount)" :highlight="(detailData.retryCount ?? 0) > 0" />
              <DetailItem label="Failover 次数" :value="formatNullableNumber(detailData.failoverCount)" :highlight="(detailData.failoverCount ?? 0) > 0" />
              <DetailItem label="熔断跳过次数" :value="formatNullableNumber(detailData.circuitOpenSkippedCount)" :highlight="(detailData.circuitOpenSkippedCount ?? 0) > 0" />
              <DetailItem label="命中限流" :value="booleanLabel(detailData.rateLimitTriggered)" :highlight="Boolean(detailData.rateLimitTriggered)" />
            </div>
          </div>

          <div class="detail-section">
            <div class="detail-section__title">Token 统计</div>
            <div class="detail-grid">
              <DetailItem label="输入 Token" :value="formatNullableNumber(detailData.promptTokens)" />
              <DetailItem label="缓存输入 Token" :value="formatNullableNumber(detailData.cachedInputTokens)" />
              <DetailItem label="输出 Token" :value="formatNullableNumber(detailData.completionTokens)" />
              <DetailItem label="总 Token" :value="formatNullableNumber(detailData.totalTokens)" />
            </div>
          </div>
        </div>
      </template>
      <template v-else>
        <el-empty description="暂无详情数据" />
      </template>
    </el-drawer>
  </ConsoleLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import ConsoleLayout from '../../layout/ConsoleLayout.vue'
import DetailItem from './DetailItem.vue'
import { fetchRequestLogDetail, fetchRequestLogPage } from '../../api/request-log'
import type { PageResult } from '../../types/common'
import type { RequestLogQueryReq, RequestLogRsp } from '../../types/request-log'

const PROVIDER_OPTIONS = [
  { label: 'OpenAI', value: 'OPENAI', tagType: 'primary' },
  { label: 'OpenAI Responses', value: 'OPENAI_RESPONSES', tagType: '' },
  { label: 'Anthropic', value: 'ANTHROPIC', tagType: 'warning' },
  { label: 'Gemini', value: 'GEMINI', tagType: 'success' },
] as const

const query = reactive<RequestLogQueryReq>({
  startTime: undefined,
  endTime: undefined,
  providerType: undefined,
  providerCode: '',
  status: undefined,
  aliasModel: '',
  requestId: '',
  isStream: undefined,
  hasRetry: undefined,
  hasFailover: undefined,
  page: 1,
  pageSize: 20,
})

const page = reactive<PageResult<RequestLogRsp>>({ list: [], total: 0, page: 1, pageSize: 20 })
const loading = ref(false)
const loadError = ref(false)
const dateRange = ref<[string, string] | null>(null)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref<RequestLogRsp | null>(null)
let latestLoadRequestId = 0

const hasActiveFilters = computed(() =>
  Boolean(
    query.startTime ||
      query.endTime ||
      query.providerType ||
      query.providerCode ||
      query.status ||
      query.aliasModel ||
      query.requestId ||
      query.isStream !== undefined ||
      query.hasRetry !== undefined ||
      query.hasFailover !== undefined,
  ),
)

function onDateRangeChange(val: [string, string] | null) {
  if (val) {
    query.startTime = val[0]
    query.endTime = val[1]
    return
  }
  query.startTime = undefined
  query.endTime = undefined
}

async function loadData() {
  const requestId = ++latestLoadRequestId
  loading.value = true
  loadError.value = false
  try {
    const result = await fetchRequestLogPage(query)
    if (requestId !== latestLoadRequestId) {
      return
    }
    Object.assign(page, result)
  } catch {
    if (requestId !== latestLoadRequestId) {
      return
    }
    loadError.value = true
    Object.assign(page, { list: [], total: 0, page: query.page, pageSize: query.pageSize })
  } finally {
    if (requestId === latestLoadRequestId) {
      loading.value = false
    }
  }
}

async function search() {
  query.page = 1
  await loadData()
}

async function resetQuery() {
  dateRange.value = null
  query.startTime = undefined
  query.endTime = undefined
  query.providerType = undefined
  query.providerCode = ''
  query.status = undefined
  query.aliasModel = ''
  query.requestId = ''
  query.isStream = undefined
  query.hasRetry = undefined
  query.hasFailover = undefined
  query.page = 1
  await loadData()
}

async function onPageSizeChange() {
  query.page = 1
  await loadData()
}

async function openDetail(requestId: string) {
  detailVisible.value = true
  detailLoading.value = true
  detailData.value = null
  try {
    detailData.value = await fetchRequestLogDetail(requestId)
  } catch {
    ElMessage.error('加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

const providerLabelMap: Record<string, string> = {
  OPENAI: 'OpenAI',
  OPENAI_RESPONSES: 'OpenAI Responses',
  ANTHROPIC: 'Anthropic',
  GEMINI: 'Gemini',
}

const providerTagTypeMap: Record<string, string> = {
  OPENAI: 'primary',
  OPENAI_RESPONSES: '',
  ANTHROPIC: 'warning',
  GEMINI: 'success',
}

function providerLabel(type?: string | null): string {
  if (!type) {
    return '-'
  }
  return providerLabelMap[type] ?? type
}

function providerTagType(type?: string | null): string {
  if (!type) {
    return 'info'
  }
  return providerTagTypeMap[type] ?? 'info'
}

const statusLabelMap: Record<string, string> = {
  SUCCESS: '成功',
  ERROR: '失败',
  CANCELLED: '已取消',
  REJECTED: '已拒绝',
}

const statusTagTypeMap: Record<string, string> = {
  SUCCESS: 'success',
  ERROR: 'danger',
  CANCELLED: 'warning',
  REJECTED: 'info',
}

const terminalStageLabelMap: Record<string, string> = {
  AUTH: '鉴权',
  RATE_LIMIT: '限流',
  ROUTING: '路由',
  FAILOVER: 'Failover',
  UPSTREAM: '上游调用',
  STREAMING: '流式输出',
}

function statusLabel(status?: string | null): string {
  if (!status) {
    return '-'
  }
  return statusLabelMap[status] ?? status
}

function statusTagType(status?: string | null): string {
  if (!status) {
    return 'info'
  }
  return statusTagTypeMap[status] ?? 'info'
}

function terminalStageLabel(stage?: string | null): string {
  if (!stage) {
    return '-'
  }
  return terminalStageLabelMap[stage] ?? stage
}

function formatDuration(ms: number): string {
  if (ms >= 1000) {
    return `${(ms / 1000).toFixed(1)}s`
  }
  return `${ms}ms`
}

function formatNullableDuration(ms?: number | null): string {
  if (ms === null || ms === undefined) {
    return '-'
  }
  return formatDuration(ms)
}

function formatTime(dateTime?: string | null): string {
  if (!dateTime) return '-'
  return dateTime.replace('T', ' ').replace(/\.\d{1,3}$/, '')
}

function formatNullableNumber(value?: number | null): string {
  if (value === null || value === undefined) {
    return '-'
  }
  return value.toLocaleString()
}

function booleanLabel(value?: boolean | null): string {
  if (value === null || value === undefined) {
    return '-'
  }
  return value ? '是' : '否'
}

function maskIp(ip?: string | null): string {
  if (!ip) {
    return '-'
  }
  if (ip.includes(':')) {
    const segments = ip.split(':')
    if (segments.length <= 2) {
      return ip
    }
    return `${segments[0]}:${segments[1]}:****:****`
  }
  const segments = ip.split('.')
  if (segments.length !== 4) {
    return ip
  }
  return `${segments[0]}.${segments[1]}.*.*`
}

function hasGovernanceSignals(row: RequestLogRsp): boolean {
  return Boolean((row.retryCount ?? 0) > 0 || (row.failoverCount ?? 0) > 0 || row.rateLimitTriggered)
}

function isErrorStatus(status?: string | null): boolean {
  return status === 'ERROR' || status === 'REJECTED'
}

onMounted(loadData)
</script>

<style scoped>
.token-usage {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  flex-wrap: wrap;
}

.token-total {
  font-weight: 600;
  color: var(--text-primary);
}

.token-detail {
  font-size: 12px;
  color: var(--text-secondary);
}

.token-cached {
  font-size: 12px;
  color: #10b981;
  background: rgba(16, 185, 129, 0.08);
  padding: 1px 6px;
  border-radius: 4px;
}

.text-muted {
  color: var(--text-placeholder);
}

.error-detail {
  color: var(--el-color-danger);
  font-size: 12px;
}

.governance-tags {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  flex-wrap: wrap;
}

.detail-loading {
  padding: 8px 0;
}

.detail-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-section {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 16px;
  background: var(--el-bg-color-page);
}

.detail-section__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 12px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

@media (max-width: 900px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
