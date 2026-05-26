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
        <el-form-item label="提供商">
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
        <!-- 1. 提供商类型 -->
        <el-table-column label="提供商类型" min-width="127" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="providerTagType(row.providerType)">
              {{ providerLabel(row.providerType) }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 2. 提供商 -->
        <el-table-column
          prop="providerCode"
          label="提供商"
          min-width="120"
          show-overflow-tooltip
          align="center"
        />
        <!-- 3. 请求模型 -->
        <el-table-column
          prop="aliasModel"
          label="请求模型"
          min-width="140"
          show-overflow-tooltip
          align="center"
        />
        <!-- 4. 目标模型 -->
        <el-table-column
          prop="targetModel"
          label="目标模型"
          min-width="140"
          show-overflow-tooltip
          align="center"
        />
        <!-- 4.5 Provider Key -->
        <el-table-column
          prop="providerApiKeyMasked"
          label="Provider Key"
          min-width="150"
          show-overflow-tooltip
          align="center"
        >
          <template #default="{ row }">
            <template v-if="row.providerApiKeyRemark">
              <el-tooltip :content="row.providerApiKeyMasked || ''" placement="top" :disabled="!row.providerApiKeyMasked">
                <span style="font-size: 13px">{{ row.providerApiKeyRemark }}</span>
              </el-tooltip>
            </template>
            <span v-else-if="row.providerApiKeyMasked" style="font-family: monospace; font-size: 12px">{{ row.providerApiKeyMasked }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <!-- 5. 流式 -->
        <el-table-column label="流式" min-width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isStream" type="success" size="small">是</el-tag>
            <el-tag v-else type="info" size="small">否</el-tag>
          </template>
        </el-table-column>
        <!-- 6. 思考 -->
        <el-table-column label="思考" min-width="130" align="center">
          <template #default="{ row }">
            <div v-if="row.thinkingEnabled === true" class="thinking-tags">
              <el-tag type="primary" size="small">开启</el-tag>
              <el-tag v-if="row.thinkingDepth" type="info" size="small">{{ formatThinkingDepth(row.thinkingDepth) }}</el-tag>
              <el-tag v-if="row.thinkingMapped" type="warning" size="small">映射</el-tag>
            </div>
            <el-tag v-else-if="row.thinkingEnabled === false" type="info" size="small">关闭</el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <!-- 7. Token 用量 -->
        <el-table-column label="Token 用量" min-width="190" align="center">
          <template #default="{ row }">
            <span v-if="row.totalTokens != null" class="token-usage">
              <span class="token-total">{{ row.totalTokens.toLocaleString() }}</span>
              <span class="token-detail"
                >({{ row.promptTokens ?? 0 }} / {{ row.completionTokens ?? 0 }})</span
              >
              <span v-if="(row.cachedInputTokens ?? 0) > 0" class="token-cached">
                缓存: {{ row.cachedInputTokens?.toLocaleString() }}
              </span>
            </span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <!-- 8. 耗时（首Token + 全部耗时） -->
        <el-table-column label="耗时" min-width="130" align="center">
          <template #default="{ row }">
            <div v-if="row.durationMs != null" class="duration-cell">
              <span v-if="row.firstTokenLatencyMs != null" class="duration-first-token">
                首T: {{ formatDuration(row.firstTokenLatencyMs) }}
              </span>
              <span class="duration-total">{{ formatDuration(row.durationMs) }}</span>
            </div>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <!-- 9. 状态 -->
        <el-table-column label="状态" min-width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 10. 请求时间 -->
        <el-table-column label="请求时间" min-width="170" align="center">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <!-- 11. 治理 -->
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
                v-if="
                  !hasGovernanceSignals(row) &&
                  !row.rateLimitTriggered
                "
                class="text-muted"
              >-</span>
            </div>
          </template>
        </el-table-column>
        <!-- 12. 终止阶段 -->
        <el-table-column
          prop="terminalStage"
          label="终止阶段"
          min-width="110"
          show-overflow-tooltip
          align="center"
        >
          <template #default="{ row }">
            <span v-if="row.terminalStage">{{ terminalStageLabel(row.terminalStage) }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <!-- 13. 错误码 -->
        <el-table-column
          prop="errorCode"
          label="错误码"
          min-width="100"
          show-overflow-tooltip
          align="center"
        >
          <template #default="{ row }">
            <span v-if="row.errorCode">{{ row.errorCode }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <!-- 14. 错误详情 -->
        <el-table-column
          prop="errorMessage"
          label="错误详情"
          min-width="170"
          show-overflow-tooltip
          align="center"
        >
          <template #default="{ row }">
            <span v-if="row.errorMessage" class="error-detail">{{ row.errorMessage }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <!-- 15. 操作 -->
        <el-table-column label="操作" min-width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
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
              <p>
                {{
                  hasActiveFilters
                    ? '尝试重置筛选条件'
                    : '当有 API 请求经过网关时，日志将自动记录在此'
                }}
              </p>
              <div class="table-empty-state__actions">
                <el-button v-if="hasActiveFilters" size="small" @click="resetQuery"
                  >重置筛选</el-button
                >
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

    <TraceTimelineDrawer
      v-model="detailVisible"
      :loading="detailLoading"
      :data="detailData"
    />
  </ConsoleLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import ConsoleLayout from '../../layout/ConsoleLayout.vue'
import TraceTimelineDrawer from './TraceTimelineDrawer.vue'
import {
  providerLabel,
  providerTagType,
  statusLabel,
  statusTagType,
  terminalStageLabel,
  formatDuration,
  formatTime,
  hasGovernanceSignals,
  formatThinkingDepth,
} from '../../utils/request-log'
import { fetchRequestLogDetail, fetchRequestLogPage } from '../../api/request-log'
import type { PageResult } from '../../types/common'
import type { RequestLogQueryReq, RequestLogRsp } from '../../types/request-log'

const PROVIDER_OPTIONS = [
  { label: 'OpenAI', value: 'OPENAI' },
  { label: 'OpenAI Responses', value: 'OPENAI_RESPONSES' },
  { label: 'Anthropic', value: 'ANTHROPIC' },
  { label: 'Gemini', value: 'GEMINI' },
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

async function openDetail(id: number) {
  detailVisible.value = true
  detailLoading.value = true
  detailData.value = null
  try {
    detailData.value = await fetchRequestLogDetail(id)
  } catch {
    ElMessage.error('加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.token-usage {
  display: inline-flex;
  flex-direction: column; /* 纵向堆叠，确保每行都水平居中 */
  align-items: center;
  justify-content: center;
  gap: 2px;
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

.duration-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.duration-first-token {
  font-size: 11px;
  color: var(--el-color-primary);
  font-weight: 500;
}

.duration-total {
  font-size: 13px;
  color: var(--text-primary);
  font-weight: 500;
}
</style>
