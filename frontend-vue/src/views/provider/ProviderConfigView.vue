<template>
  <ConsoleLayout>
    <div class="page-card">
      <div class="card-header">
        <span class="card-header__title">接入通道列表</span>
        <div class="card-header__actions">
          <el-button type="primary" size="small" @click="openCreate">新增通道</el-button>
          <el-button size="small" @click="loadData">刷新</el-button>
        </div>
      </div>

      <el-form :inline="true" :model="query" class="filter-bar">
        <el-form-item label="Provider 编码">
          <el-input v-model="query.providerCode" placeholder="openai-main" clearable size="default" />
        </el-form-item>
        <el-form-item label="Provider 类型">
          <el-input v-model="query.providerType" placeholder="OPENAI" clearable size="default" />
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
        <el-table-column prop="providerCode" label="Provider 编码" min-width="140" />
        <el-table-column prop="providerType" label="类型" min-width="100" />
        <el-table-column prop="displayName" label="显示名称" min-width="140" />
        <el-table-column label="状态" min-width="80">
          <template #default="scope">
            <el-tag :type="scope.row.enabled ? 'success' : 'info'" size="small">
              {{ scope.row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" min-width="80" />
        <el-table-column prop="apiKeyMasked" label="密钥摘要" min-width="120" />
        <el-table-column label="操作" fixed="right" width="140">
          <template #default="scope">
            <el-button link type="primary" size="small" @click="openEdit(scope.row)">编辑</el-button>
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
              <strong>{{ hasActiveFilters ? '没有匹配的接入通道' : '暂无接入通道' }}</strong>
              <p>{{ hasActiveFilters ? '尝试重置筛选条件' : '请点击"新增通道"添加配置' }}</p>
              <div class="table-empty-state__actions">
                <el-button v-if="hasActiveFilters" size="small" @click="resetQuery">重置筛选</el-button>
                <el-button type="primary" size="small" @click="openCreate">新增通道</el-button>
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

    <ProviderFormDialog
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
import ProviderFormDialog from '../../components/provider/ProviderFormDialog.vue'
import { addProvider, deleteProvider, fetchProviderPage, updateProvider } from '../../api/provider-config'
import type { PageResult } from '../../types/common'
import type { ProviderConfigAddReq, ProviderConfigQueryReq, ProviderConfigRsp, ProviderConfigUpdateReq } from '../../types/provider'

const query = reactive<ProviderConfigQueryReq>({
  providerCode: '',
  providerType: '',
  page: 1,
  pageSize: 10,
})

const enabledFilter = ref<'all' | 'true' | 'false'>('all')
const page = reactive<PageResult<ProviderConfigRsp>>({ list: [], total: 0, page: 1, pageSize: 10 })
const dialogVisible = ref(false)
const currentItem = ref<ProviderConfigRsp | null>(null)
const loading = ref(false)
const loadError = ref(false)

const hasActiveFilters = computed(() => Boolean(query.providerCode || query.providerType || enabledFilter.value !== 'all'))

async function loadData() {
  loading.value = true
  loadError.value = false

  try {
    const result = await fetchProviderPage({
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
  query.providerCode = ''
  query.providerType = ''
  query.page = 1
  enabledFilter.value = 'all'
  await loadData()
}

function openCreate() {
  currentItem.value = null
  dialogVisible.value = true
}

function openEdit(item: ProviderConfigRsp) {
  currentItem.value = item
  dialogVisible.value = true
}

function closeDialog() {
  dialogVisible.value = false
}

async function submitForm(payload: ProviderConfigAddReq | ProviderConfigUpdateReq) {
  try {
    if ('id' in payload) {
      await updateProvider(payload)
      ElMessage.success('接入通道更新成功')
    } else {
      await addProvider(payload)
      ElMessage.success('接入通道新增成功')
    }
    dialogVisible.value = false
    await loadData()
  } catch {
    // 请求层已统一处理错误提示
  }
}

async function removeItem(id: number) {
  try {
    await ElMessageBox.confirm('删除后将不可恢复，是否继续？', '删除接入通道', { type: 'warning' })
    await deleteProvider(id)
    ElMessage.success('接入通道删除成功')
    await loadData()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
  }
}

onMounted(loadData)
</script>
