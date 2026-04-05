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
        ref="providerTableRef"
        v-loading="providerLoading"
        :data="providerPage.list"
        stripe
        row-key="id"
        element-loading-text="正在加载..."
        @expand-change="handleExpandChange"
      >
        <!-- 拖拽排序手柄列 -->
        <el-table-column width="40" align="center" class-name="drag-handle-col">
          <template #default>
            <el-icon class="drag-handle"><Rank /></el-icon>
          </template>
        </el-table-column>

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
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, RefreshRight, Rank } from '@element-plus/icons-vue'
import Sortable from 'sortablejs'
import ConsoleLayout from '../../layout/ConsoleLayout.vue'
import ProviderFormDialog from '../../components/provider/ProviderFormDialog.vue'
import ProviderRedirectExpandRow from '../../components/provider/ProviderRedirectExpandRow.vue'
import ModelRedirectFormDialog from '../../components/model/ModelRedirectFormDialog.vue'
import { addProvider, batchUpdatePriority, deleteProvider, fetchProviderPage, toggleProvider, updateProvider } from '../../api/provider-config'
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
    // 数据加载后重新绑定拖拽（tbody DOM 可能在 loading 状态下被替换）
    nextTick(() => initSortable())
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

/* ==================== 拖拽排序 ==================== */

const providerTableRef = ref<InstanceType<typeof import('element-plus')['ElTable']>>()
let sortableInstance: Sortable | null = null

/** 排序持久化中标记，防止重复提交 */
const sortSaving = ref(false)

/** 初始化 SortableJS 拖拽排序 */
function initSortable() {
  const table = providerTableRef.value
  if (!table) return

  // 获取 el-table 的 tbody DOM 节点
  const el = table.$el as HTMLElement
  const tbody = el.querySelector('.el-table__body-wrapper tbody') as HTMLElement
  if (!tbody) return

  // 销毁旧实例，防止重复绑定
  sortableInstance?.destroy()

  sortableInstance = Sortable.create(tbody, {
    handle: '.drag-handle',
    // 只允许数据行参与拖拽，排除展开内容行
    draggable: '.el-table__row',
    animation: 200,
    ghostClass: 'sortable-ghost',
    chosenClass: 'sortable-chosen',
    dragClass: 'sortable-drag',
    onEnd: async (evt) => {
      const { oldIndex, newIndex } = evt
      // 未发生位置变化或正在保存中时跳过
      if (oldIndex === undefined || newIndex === undefined || oldIndex === newIndex) return
      if (sortSaving.value) return

      const list = [...providerPage.list]
      const [moved] = list.splice(oldIndex, 1)
      list.splice(newIndex, 0, moved)

      // 基于当前页首个元素的 priority 做偏移，避免跨页优先级冲突
      // 后端按 priority DESC 排序，当前页首条 priority 最高
      const maxPriority = providerPage.list[0]?.priority ?? list.length
      const priorityItems = list.map((item, idx) => ({
        id: item.id,
        versionNo: item.versionNo,
        priority: maxPriority - idx,
      }))

      // 先乐观更新本地列表顺序
      providerPage.list.splice(0, providerPage.list.length, ...list)

      // 异步持久化到后端
      sortSaving.value = true
      try {
        await batchUpdatePriority(priorityItems)
        // 成功后刷新列表以获取最新 versionNo
        await loadProviders()
        ElMessage.success('优先级排序已更新')
      } catch {
        // 持久化失败时回滚到服务器状态
        await loadProviders()
      } finally {
        sortSaving.value = false
      }
    },
  })
}

// 组件卸载时清理 Sortable 实例
onUnmounted(() => {
  sortableInstance?.destroy()
  sortableInstance = null
})
</script>
