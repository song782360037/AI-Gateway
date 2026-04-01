<template>
  <ConsoleLayout>
    <div class="page-card">
      <div class="card-header">
        <span class="card-header__title">模型路由规则</span>
        <div class="card-header__actions">
          <el-button type="primary" size="small" @click="openCreate">新增规则</el-button>
          <el-button size="small" @click="loadData">刷新</el-button>
        </div>
      </div>

      <el-form :inline="true" :model="query" class="filter-bar">
        <el-form-item label="对外模型">
          <el-input v-model="query.aliasName" placeholder="gpt-4o" clearable size="default" />
        </el-form-item>
        <el-form-item label="目标 Provider">
          <el-input v-model="query.providerCode" placeholder="openai-main" clearable size="default" />
        </el-form-item>
        <el-form-item label="目标模型">
          <el-input v-model="query.targetModel" placeholder="gpt-4o-2024-11-20" clearable size="default" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-select v-model="enabledFilter" placeholder="全部" style="width: 120px" size="default">
            <el-option label="全部" value="all" />
            <el-option label="启用" value="true" />
            <el-option label="禁用" value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="default" @click="search">查询</el-button>
          <el-button size="default" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table
        v-loading="loading"
        :data="page.list"
        stripe
        element-loading-text="正在加载..."
      >
        <el-table-column prop="aliasName" label="对外模型" min-width="140" />
        <el-table-column prop="providerCode" label="目标 Provider" min-width="140" />
        <el-table-column prop="targetModel" label="实际模型" min-width="200" />
        <el-table-column label="状态" min-width="80">
          <template #default="scope">
            <el-tag :type="scope.row.enabled ? 'success' : 'info'" size="small">
              {{ scope.row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" min-width="80" />
        <el-table-column label="操作" fixed="right" width="200">
          <template #default="scope">
            <el-button link type="primary" size="small" @click="openEdit(scope.row)">编辑</el-button>
            <el-button
              link
              :type="scope.row.enabled ? 'warning' : 'success'"
              size="small"
              @click="toggleItem(scope.row)"
            >
              {{ scope.row.enabled ? '禁用' : '启用' }}
            </el-button>
            <el-button link type="danger" size="small" @click="removeItem(scope.row.id)">删除</el-button>
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
              <strong>{{ hasActiveFilters ? '没有匹配的模型路由规则' : '暂无模型路由规则' }}</strong>
              <p>{{ hasActiveFilters ? '尝试重置筛选条件' : '请点击"新增规则"添加配置' }}</p>
              <div class="table-empty-state__actions">
                <el-button v-if="hasActiveFilters" size="small" @click="resetQuery">重置筛选</el-button>
                <el-button type="primary" size="small" @click="openCreate">新增规则</el-button>
              </div>
            </template>
          </div>
        </template>
      </el-table>

      <div class="pager-bar">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          layout="total, prev, pager, next"
          :total="page.total"
          @current-change="loadData"
        />
      </div>
    </div>

    <ModelRedirectFormDialog
      v-if="dialogVisible"
      :visible="dialogVisible"
      :model-value="currentItem"
      @close="closeDialog"
      @submit="submitForm"
    />
  </ConsoleLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ConsoleLayout from '../../layout/ConsoleLayout.vue'
import ModelRedirectFormDialog from '../../components/model/ModelRedirectFormDialog.vue'
import { addModelRedirect, deleteModelRedirect, fetchModelRedirectPage, toggleModelRedirect, updateModelRedirect } from '../../api/model-redirect-config'
import type { PageResult } from '../../types/common'
import type {
  ModelRedirectConfigAddReq,
  ModelRedirectConfigQueryReq,
  ModelRedirectConfigRsp,
  ModelRedirectConfigUpdateReq,
} from '../../types/model'

const query = reactive<ModelRedirectConfigQueryReq>({
  aliasName: '',
  providerCode: '',
  targetModel: '',
  page: 1,
  pageSize: 10,
})

const enabledFilter = ref<'all' | 'true' | 'false'>('all')
const page = reactive<PageResult<ModelRedirectConfigRsp>>({ list: [], total: 0, page: 1, pageSize: 10 })
const dialogVisible = ref(false)
const currentItem = ref<ModelRedirectConfigRsp | null>(null)
const loading = ref(false)
const loadError = ref(false)

const hasActiveFilters = computed(() => Boolean(query.aliasName || query.providerCode || query.targetModel || enabledFilter.value !== 'all'))

async function loadData() {
  loading.value = true
  loadError.value = false

  try {
    const result = await fetchModelRedirectPage({
      ...query,
      enabled: enabledFilter.value === 'all' ? undefined : enabledFilter.value === 'true',
    })
    Object.assign(page, result)
  } catch {
    loadError.value = true
    Object.assign(page, { list: [], total: 0, page: query.page, pageSize: query.pageSize })
  } finally {
    loading.value = false
  }
}

async function search() {
  query.page = 1
  await loadData()
}

async function resetQuery() {
  query.aliasName = ''
  query.providerCode = ''
  query.targetModel = ''
  query.page = 1
  enabledFilter.value = 'all'
  await loadData()
}

function openCreate() {
  currentItem.value = null
  dialogVisible.value = true
}

function openEdit(item: ModelRedirectConfigRsp) {
  currentItem.value = item
  dialogVisible.value = true
}

function closeDialog() {
  dialogVisible.value = false
}

async function submitForm(payload: ModelRedirectConfigAddReq | ModelRedirectConfigUpdateReq) {
  try {
    if ('id' in payload) {
      await updateModelRedirect(payload)
      ElMessage.success('模型路由规则更新成功')
    } else {
      await addModelRedirect(payload)
      ElMessage.success('模型路由规则新增成功')
    }
    dialogVisible.value = false
    await loadData()
  } catch {
    // 请求层已统一处理错误提示
  }
}

async function toggleItem(item: ModelRedirectConfigRsp) {
  const action = item.enabled ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确定要${action}路由规则「${item.aliasName}」吗？`,
      `${action}路由规则`,
      { type: 'warning' },
    )
    await toggleModelRedirect(item.id, item.versionNo)
    ElMessage.success(`路由规则已${action}`)
    await loadData()
  } catch (error) {
    // 用户点击取消或关闭弹窗时不做处理
    if (error === 'cancel' || error === 'close') return
  }
}

async function removeItem(id: number) {
  try {
    await ElMessageBox.confirm('删除后将不可恢复，是否继续？', '删除模型路由规则', { type: 'warning' })
    await deleteModelRedirect(id)
    ElMessage.success('模型路由规则删除成功')
    await loadData()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
  }
}

onMounted(loadData)
</script>
