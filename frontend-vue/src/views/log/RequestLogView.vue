<template>
  <ConsoleLayout>
    <div class="page-card">
      <div class="card-header">
        <span class="card-header__title">请求日志</span>
        <div class="card-header__actions">
          <el-button size="small" @click="loadData">刷新</el-button>
        </div>
      </div>

      <!-- 筛选条件 -->
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
        <el-form-item label="协议">
          <el-select
            v-model="query.providerType"
            placeholder="全部"
            style="width: 150px"
            size="default"
            clearable
          >
            <el-option label="OpenAI Chat" value="OPENAI" />
            <el-option label="OpenAI Responses" value="OPENAI_RESPONSES" />
            <el-option label="Anthropic" value="ANTHROPIC" />
            <el-option label="Gemini" value="GEMINI" />
          </el-select>
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
        <el-form-item>
          <el-button type="primary" size="default" @click="search">查询</el-button>
          <el-button size="default" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="page.list" stripe element-loading-text="正在加载...">
        <el-table-column label="请求时间" min-width="170" align="center">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column prop="aliasModel" label="模型别名" min-width="140" show-overflow-tooltip align="center" />
        <el-table-column prop="targetModel" label="目标模型" min-width="140" show-overflow-tooltip align="center" />
        <el-table-column prop="providerCode" label="通道" min-width="100" show-overflow-tooltip align="center" />
        <el-table-column label="协议" min-width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="providerTagType(row.providerType)">
              {{ providerLabel(row.providerType) }}
            </el-tag>
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
              <span class="token-detail">
                ({{ row.promptTokens ?? 0 }} / {{ row.completionTokens ?? 0 }})
              </span>
              <span v-if="row.cachedInputTokens > 0" class="token-cached">
                缓存: {{ row.cachedInputTokens.toLocaleString() }}
              </span>
            </span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" min-width="90" align="center">
          <template #default="{ row }">
            <span v-if="row.durationMs != null">{{ formatDuration(row.durationMs) }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorCode" label="错误码" min-width="100" show-overflow-tooltip align="center">
          <template #default="{ row }">
            <span v-if="row.errorCode">{{ row.errorCode }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="错误详情" min-width="150" show-overflow-tooltip align="center">
          <template #default="{ row }">
            <span v-if="row.errorMessage" class="error-detail">{{ row.errorMessage }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="sourceIp" label="来源 IP" min-width="100" show-overflow-tooltip align="center">
          <template #default="{ row }">
            <span v-if="row.sourceIp">{{ row.sourceIp }}</span>
            <span v-else class="text-muted">-</span>
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

      <!-- 分页 -->
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
  </ConsoleLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import ConsoleLayout from '../../layout/ConsoleLayout.vue'
import { fetchRequestLogPage } from '../../api/request-log'
import type { PageResult } from '../../types/common'
import type { RequestLogQueryReq, RequestLogRsp } from '../../types/request-log'

/** 查询参数 */
const query = reactive<RequestLogQueryReq>({
  startTime: undefined,
  endTime: undefined,
  providerType: undefined,
  status: undefined,
  aliasModel: '',
  requestId: '',
  page: 1,
  pageSize: 20,
})

/** 分页结果 */
const page = reactive<PageResult<RequestLogRsp>>({ list: [], total: 0, page: 1, pageSize: 20 })

const loading = ref(false)
const loadError = ref(false)

/** 日期范围选择器的绑定值 */
const dateRange = ref<[string, string] | null>(null)

/** 是否存在活跃的筛选条件 */
const hasActiveFilters = computed(() =>
  Boolean(query.startTime || query.endTime || query.providerType || query.status || query.aliasModel || query.requestId),
)

/** 日期范围变更回调 */
function onDateRangeChange(val: [string, string] | null) {
  if (val) {
    query.startTime = val[0]
    query.endTime = val[1]
  } else {
    query.startTime = undefined
    query.endTime = undefined
  }
}

/** 加载数据 */
async function loadData() {
  loading.value = true
  loadError.value = false
  try {
    const result = await fetchRequestLogPage(query)
    Object.assign(page, result)
  } catch {
    loadError.value = true
    Object.assign(page, { list: [], total: 0, page: query.page, pageSize: query.pageSize })
  } finally {
    loading.value = false
  }
}

/** 搜索（重置到第一页） */
async function search() {
  query.page = 1
  await loadData()
}

/** 重置筛选条件 */
async function resetQuery() {
  dateRange.value = null
  query.startTime = undefined
  query.endTime = undefined
  query.providerType = undefined
  query.status = undefined
  query.aliasModel = ''
  query.requestId = ''
  query.page = 1
  await loadData()
}

/** 每页大小变更 */
async function onPageSizeChange() {
  query.page = 1
  await loadData()
}

/** 协议类型标签映射 */
const providerLabelMap: Record<string, string> = {
  OPENAI_CHAT: 'OpenAI Chat',
  OPENAI_RESPONSES: 'Response',
  ANTHROPIC: 'Anthropic',
  GEMINI: 'Gemini',
}

/** 协议类型标签样式映射 */
const providerTagTypeMap: Record<string, string> = {
  OPENAI: 'primary',
  OPENAI_RESPONSES: '',
  ANTHROPIC: 'warning',
  GEMINI: 'success',
}

function providerLabel(type: string): string {
  return providerLabelMap[type] ?? type
}

function providerTagType(type: string): string {
  return providerTagTypeMap[type] ?? 'info'
}

/** 请求状态标签映射 */
const statusLabelMap: Record<string, string> = {
  SUCCESS: '成功',
  ERROR: '失败',
  CANCELLED: '已取消',
}

const statusTagTypeMap: Record<string, string> = {
  SUCCESS: 'success',
  ERROR: 'danger',
  CANCELLED: 'warning',
}

function statusLabel(status: string): string {
  return statusLabelMap[status] ?? status
}

function statusTagType(status: string): string {
  return statusTagTypeMap[status] ?? 'info'
}

/** 格式化耗时：大于 1000ms 显示为秒 */
function formatDuration(ms: number): string {
  if (ms >= 1000) {
    return `${(ms / 1000).toFixed(1)}s`
  }
  return `${ms}ms`
}

/** 格式化时间：去掉末尾的毫秒部分 */
function formatTime(dateTime: string): string {
  if (!dateTime) return '-'
  // 处理 "2026-04-01T12:30:45" 或 "2026-04-01T12:30:45.123" 格式
  return dateTime.replace('T', ' ').replace(/\.\d{1,3}$/, '')
}

onMounted(loadData)
</script>

<style scoped>
/* Token 用量展示 */
.token-usage {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
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

/* 次要文本 */
.text-muted {
  color: var(--text-placeholder);
}

/* 错误详情 */
.error-detail {
  color: var(--el-color-danger);
  font-size: 12px;
}
</style>
