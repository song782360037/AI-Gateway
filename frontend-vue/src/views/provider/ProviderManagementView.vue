<template>
  <ConsoleLayout>
    <div class="page-card">
      <!-- 页面摘要区 -->
      <div class="provider-page-hero">
        <div class="provider-page-hero__main">
          <p class="provider-page-hero__eyebrow">Provider Console</p>
          <h3 class="provider-page-hero__title">提供商管理</h3>
          <p class="provider-page-hero__desc">
            统一维护接入通道与模型路由，展开单行即可管理该 Provider 的模型映射规则。
          </p>
        </div>
        <div class="provider-page-hero__meta">
          <div class="provider-page-hero__stat">
            <span class="provider-page-hero__stat-label">当前列表</span>
            <strong class="provider-page-hero__stat-value">{{ providerPage.total }}</strong>
          </div>
          <div class="provider-page-hero__actions">
            <el-button type="primary" @click="openCreateProvider">
              <el-icon style="margin-right: 4px"><Plus /></el-icon>新增提供商
            </el-button>
            <el-button plain @click="loadProviders">
              <el-icon style="margin-right: 4px"><RefreshRight /></el-icon>刷新
            </el-button>
          </div>
        </div>
      </div>

      <!-- 筛选工具条 -->
      <div class="provider-toolbar">
        <el-form :inline="true" :model="providerQuery" class="filter-bar">
          <el-form-item label="Provider 编码">
            <el-input v-model="providerQuery.providerCode" placeholder="openai-main" clearable size="default" />
          </el-form-item>
          <el-form-item label="Provider 类型">
            <el-input v-model="providerQuery.providerType" placeholder="OPENAI" clearable size="default" />
          </el-form-item>
          <el-form-item label="启用状态">
            <el-select v-model="enabledFilter" placeholder="全部" style="width: 120px" size="default">
              <el-option label="全部" value="all" />
              <el-option label="启用" value="true" />
              <el-option label="禁用" value="false" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" size="default" @click="searchProviders">查询</el-button>
            <el-button size="default" @click="resetProviderQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 表头 -->
      <div class="provider-card-header">
        <div class="provider-card-header__main">
          <span class="provider-card-header__title">Provider 列表</span>
          <p class="provider-card-header__desc">展开任意行查看并维护该 Provider 下的模型路由规则。</p>
        </div>
      </div>

      <!-- Provider 主表 + 展开行 -->
      <el-table
        v-loading="providerLoading"
        :data="providerPage.list"
        stripe
        row-key="id"
        element-loading-text="正在加载..."
        @expand-change="handleExpandChange"
      >
        <!-- 展开列：路由规则 -->
        <el-table-column type="expand">
          <template #default="{ row }">
            <ProviderRedirectExpandRow
              v-if="expandedRows.has(row.id)"
              :ref="(el) => setExpandRowRef(row.id, el as ExpandRowExpose | null)"
              :provider-code="row.providerCode"
              @add-redirect="openCreateRedirect"
              @edit-redirect="openEditRedirect"
              @toggle-redirect="toggleRedirect"
              @delete-redirect="removeRedirect"
            />
          </template>
        </el-table-column>

        <el-table-column prop="providerCode" label="Provider 编码" min-width="140" />
        <el-table-column prop="providerType" label="类型" min-width="110" />
        <el-table-column prop="displayName" label="显示名称" min-width="140" />
        <el-table-column label="状态" min-width="80">
          <template #default="{ row }">
            <el-tag
              :class="row.enabled ? 'status-chip status-chip--success' : 'status-chip status-chip--muted'"
              :type="row.enabled ? 'success' : 'info'"
              size="small"
            >
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" min-width="80" />
        <el-table-column prop="apiKeyMasked" label="密钥摘要" min-width="120" />
        <el-table-column label="操作" fixed="right" width="180">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEditProvider(row)">编辑</el-button>
            <el-button link :type="row.enabled ? 'warning' : 'success'" size="small" @click="handleToggleProvider(row)">
              {{ row.enabled ? '禁用' : '启用' }}
            </el-button>
            <el-button link type="danger" size="small" @click="removeProvider(row.id)">删除</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <div class="table-empty-state provider-empty-state">
            <template v-if="providerLoadError">
              <div class="provider-empty-state__icon provider-empty-state__icon--error">!</div>
              <strong>加载失败</strong>
              <p>请检查后端服务后重试</p>
              <div class="table-empty-state__actions">
                <el-button type="primary" size="small" @click="loadProviders">重新加载</el-button>
              </div>
            </template>
            <template v-else>
              <div class="provider-empty-state__icon">P</div>
              <strong>{{ hasActiveFilters ? '没有匹配的提供商' : '暂无提供商' }}</strong>
              <p>{{ hasActiveFilters ? '尝试重置筛选条件' : '点击"新增提供商"开始配置' }}</p>
              <div class="table-empty-state__actions">
                <el-button v-if="hasActiveFilters" size="small" @click="resetProviderQuery">重置筛选</el-button>
                <el-button type="primary" size="small" @click="openCreateProvider">新增提供商</el-button>
              </div>
            </template>
          </div>
        </template>
      </el-table>

      <div class="pager-bar">
        <el-pagination
          v-model:current-page="providerQuery.page"
          v-model:page-size="providerQuery.pageSize"
          layout="total, prev, pager, next"
          :total="providerPage.total"
          @current-change="loadProviders"
        />
      </div>
    </div>

    <!-- Provider 新增/编辑弹窗 -->
    <ProviderFormDialog
      v-if="providerDialogVisible"
      :visible="providerDialogVisible"
      :model-value="currentProvider"
      @close="closeProviderDialog"
      @submit="submitProviderForm"
    />

    <!-- 路由规则 新增/编辑弹窗（providerCode 锁定） -->
    <ModelRedirectFormDialog
      v-if="redirectDialogVisible"
      :visible="redirectDialogVisible"
      :model-value="currentRedirect"
      :provider-code-locked="true"
      :locked-provider-code="lockedProviderCode"
      @close="closeRedirectDialog"
      @submit="submitRedirectForm"
    />
  </ConsoleLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, RefreshRight } from '@element-plus/icons-vue'
import ConsoleLayout from '../../layout/ConsoleLayout.vue'
import ProviderFormDialog from '../../components/provider/ProviderFormDialog.vue'
import ProviderRedirectExpandRow from '../../components/provider/ProviderRedirectExpandRow.vue'
import ModelRedirectFormDialog from '../../components/model/ModelRedirectFormDialog.vue'
import { addProvider, deleteProvider, fetchProviderPage, toggleProvider, updateProvider } from '../../api/provider-config'
import {
  addModelRedirect,
  deleteModelRedirect,
  toggleModelRedirect,
  updateModelRedirect,
} from '../../api/model-redirect-config'
import type { PageResult } from '../../types/common'
import type {
  ProviderConfigAddReq,
  ProviderConfigQueryReq,
  ProviderConfigRsp,
  ProviderConfigUpdateReq,
} from '../../types/provider'
import type {
  ModelRedirectConfigAddReq,
  ModelRedirectConfigRsp,
  ModelRedirectConfigUpdateReq,
} from '../../types/model'

type ExpandRowExpose = {
  refresh: () => Promise<void> | void
}

/* ==================== Provider 列表 ==================== */

const providerQuery = reactive<ProviderConfigQueryReq>({
  providerCode: '',
  providerType: '',
  page: 1,
  pageSize: 10,
})

const enabledFilter = ref<'all' | 'true' | 'false'>('all')
const providerPage = reactive<PageResult<ProviderConfigRsp>>({ list: [], total: 0, page: 1, pageSize: 10 })
const providerLoading = ref(false)
const providerLoadError = ref(false)

const hasActiveFilters = computed(
  () => Boolean(providerQuery.providerCode || providerQuery.providerType || enabledFilter.value !== 'all'),
)

async function loadProviders() {
  providerLoading.value = true
  providerLoadError.value = false

  try {
    const result = await fetchProviderPage({
      ...providerQuery,
      enabled: enabledFilter.value === 'all' ? undefined : enabledFilter.value === 'true',
    })
    Object.assign(providerPage, result)
  } catch {
    providerLoadError.value = true
    Object.assign(providerPage, { list: [], total: 0, page: providerQuery.page, pageSize: providerQuery.pageSize })
  } finally {
    providerLoading.value = false
  }
}

function searchProviders() {
  providerQuery.page = 1
  loadProviders()
}

function resetProviderQuery() {
  providerQuery.providerCode = ''
  providerQuery.providerType = ''
  providerQuery.page = 1
  enabledFilter.value = 'all'
  loadProviders()
}

/* ==================== 展开行管理 ==================== */

// 记录当前展开的行 id，用于按需渲染
const expandedRows = ref(new Set<number>())
// 展开行组件引用，用于刷新子表格
const expandRowRefs = reactive<Record<number, ExpandRowExpose | undefined>>({})

function setExpandRowRef(providerId: number, el: ExpandRowExpose | null) {
  if (el) {
    expandRowRefs[providerId] = el
    return
  }
  delete expandRowRefs[providerId]
}

function handleExpandChange(row: ProviderConfigRsp, expandedRowsList: ProviderConfigRsp[]) {
  const isExpanded = expandedRowsList.some((r) => r.id === row.id)
  if (isExpanded) {
    expandedRows.value.add(row.id)
  } else {
    expandedRows.value.delete(row.id)
    delete expandRowRefs[row.id]
  }
}

/** 刷新指定 Provider 的展开行子表格 */
function refreshExpandRow(providerId: number) {
  expandRowRefs[providerId]?.refresh()
}

function refreshExpandRowByProviderCode(providerCode: string) {
  const provider = providerPage.list.find((p) => p.providerCode === providerCode)
  if (provider) {
    refreshExpandRow(provider.id)
  }
}

/* ==================== Provider 弹窗 ==================== */

const providerDialogVisible = ref(false)
const currentProvider = ref<ProviderConfigRsp | null>(null)

function openCreateProvider() {
  currentProvider.value = null
  providerDialogVisible.value = true
}

function openEditProvider(item: ProviderConfigRsp) {
  currentProvider.value = item
  providerDialogVisible.value = true
}

function closeProviderDialog() {
  providerDialogVisible.value = false
}

async function submitProviderForm(payload: ProviderConfigAddReq | ProviderConfigUpdateReq) {
  try {
    if ('id' in payload) {
      await updateProvider(payload)
      ElMessage.success('提供商更新成功')
    } else {
      await addProvider(payload)
      ElMessage.success('提供商新增成功')
    }
    providerDialogVisible.value = false
    await loadProviders()
  } catch {
    // 请求层已统一处理错误提示
  }
}

async function removeProvider(id: number) {
  try {
    await ElMessageBox.confirm('删除后将不可恢复，是否继续？', '删除提供商', { type: 'warning' })
    await deleteProvider(id)
    ElMessage.success('提供商删除成功')
    await loadProviders()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
  }
}

async function handleToggleProvider(item: ProviderConfigRsp) {
  const action = item.enabled ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确定要${action}提供商「${item.displayName || item.providerCode}」吗？`,
      `${action}提供商`,
      { type: 'warning' },
    )
    await toggleProvider(item.id, item.versionNo)
    ElMessage.success(`提供商已${action}`)
    await loadProviders()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
  }
}

/* ==================== 路由规则弹窗 ==================== */

const redirectDialogVisible = ref(false)
const currentRedirect = ref<ModelRedirectConfigRsp | null>(null)
const lockedProviderCode = ref('')
// 记录当前操作的 Provider id，用于刷新展开行
const activeProviderId = ref<number | null>(null)

function openCreateRedirect(providerCode: string) {
  // 从 providerCode 找到对应的 Provider id，用于后续刷新
  const provider = providerPage.list.find((p) => p.providerCode === providerCode)
  activeProviderId.value = provider?.id ?? null

  currentRedirect.value = null
  lockedProviderCode.value = providerCode
  redirectDialogVisible.value = true
}

function openEditRedirect(item: ModelRedirectConfigRsp) {
  // 找到对应的 Provider id
  const provider = providerPage.list.find((p) => p.providerCode === item.providerCode)
  activeProviderId.value = provider?.id ?? null

  currentRedirect.value = item
  lockedProviderCode.value = item.providerCode
  redirectDialogVisible.value = true
}

function closeRedirectDialog() {
  redirectDialogVisible.value = false
}

async function submitRedirectForm(payload: ModelRedirectConfigAddReq | ModelRedirectConfigUpdateReq) {
  try {
    if ('id' in payload) {
      await updateModelRedirect(payload)
      ElMessage.success('路由规则更新成功')
    } else {
      await addModelRedirect(payload)
      ElMessage.success('路由规则新增成功')
    }
    redirectDialogVisible.value = false
    // 刷新对应展开行
    if (activeProviderId.value !== null) {
      refreshExpandRow(activeProviderId.value)
    }
  } catch {
    // 请求层已统一处理错误提示
  }
}

async function toggleRedirect(item: ModelRedirectConfigRsp) {
  const action = item.enabled ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确定要${action}路由规则「${item.aliasName}」吗？`,
      `${action}路由规则`,
      { type: 'warning' },
    )
    await toggleModelRedirect(item.id, item.versionNo)
    ElMessage.success(`路由规则已${action}`)
    // 刷新对应展开行
    const provider = providerPage.list.find((p) => p.providerCode === item.providerCode)
    if (provider) refreshExpandRow(provider.id)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
  }
}

async function removeRedirect(item: ModelRedirectConfigRsp) {
  try {
    await ElMessageBox.confirm('删除后将不可恢复，是否继续？', '删除路由规则', { type: 'warning' })
    await deleteModelRedirect(item.id)
    ElMessage.success('路由规则删除成功')
    refreshExpandRowByProviderCode(item.providerCode)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
  }
}

/* ==================== 初始化 ==================== */

onMounted(loadProviders)
</script>
