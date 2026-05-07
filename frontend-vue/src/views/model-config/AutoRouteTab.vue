<template>
  <div>
    <div style="margin-bottom: 16px">
      <div class="card-header__actions">
        <el-button type="primary" size="small" @click="openCreate">新增 Auto 配置</el-button>
        <el-button size="small" @click="loadData">刷新</el-button>
      </div>
    </div>

    <el-alert class="route-hint" type="info" :closable="false" show-icon>
      <template #title>
        请求模型名为 auto 时使用 routeKey=default；请求 auto:coding 时使用 routeKey=coding。
      </template>
    </el-alert>

    <el-form :inline="true" :model="query" class="filter-bar">
      <el-form-item label="路由键">
        <el-input v-model.trim="query.routeKey" placeholder="default / coding" clearable />
      </el-form-item>
      <el-form-item label="名称">
        <el-input v-model.trim="query.displayName" placeholder="按配置名称搜索" clearable />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.enabled" placeholder="全部" style="width: 120px" clearable>
          <el-option label="启用" :value="true" />
          <el-option label="禁用" :value="false" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="page.list" stripe element-loading-text="正在加载...">
      <el-table-column prop="routeKey" label="路由键" min-width="130" align="center">
        <template #default="{ row }">
          <el-tag type="primary" effect="plain">{{ row.routeKey }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="displayName" label="配置名称" min-width="160" align="center" />
      <el-table-column label="策略" min-width="110" align="center">
        <template #default="{ row }">{{ formatStrategy(row.selectionStrategy) }}</template>
      </el-table-column>
      <el-table-column prop="candidateCount" label="候选数" min-width="90" align="center" />
      <el-table-column label="状态" min-width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" min-width="170" align="center">
        <template #default="{ row }">{{ row.updateTime ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="260" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openCandidates(row)"
            >候选模型</el-button
          >
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button
            link
            :type="row.enabled ? 'warning' : 'success'"
            size="small"
            @click="toggleConfig(row)"
          >
            {{ row.enabled ? '禁用' : '启用' }}
          </el-button>
          <el-button link type="danger" size="small" @click="removeConfig(row)">删除</el-button>
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
            <strong>{{ hasActiveFilters ? '没有匹配的 Auto 配置' : '暂无 Auto 配置' }}</strong>
            <p>{{ hasActiveFilters ? '尝试重置筛选条件' : '请点击"新增 Auto 配置"创建' }}</p>
            <div class="table-empty-state__actions">
              <el-button v-if="hasActiveFilters" size="small" @click="resetQuery"
                >重置筛选</el-button
              >
              <el-button type="primary" size="small" @click="openCreate">新增 Auto 配置</el-button>
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

    <!-- 配置表单弹窗 -->
    <ConfigFormDialog
      v-model:visible="configDialogVisible"
      :is-edit="isConfigEdit"
      :edit-data="editingConfig"
      @submit="loadData"
    />

    <!-- 候选模型抽屉（内含候选模型表单弹窗） -->
    <CandidateDrawer
      v-model:visible="candidateDrawerVisible"
      :config-data="selectedConfig"
      @data-changed="loadData"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ConfigFormDialog from './ConfigFormDialog.vue'
import CandidateDrawer from './CandidateDrawer.vue'
import {
  deleteAutoRouteConfig,
  fetchAutoRoutePage,
  toggleAutoRouteConfig,
} from '../../api/auto-route-config'
import type { PageResult } from '../../types/common'
import type { AutoRouteConfigQueryReq, AutoRouteConfigRsp } from '../../types/auto-route'

const query = reactive<AutoRouteConfigQueryReq>({
  routeKey: '',
  displayName: '',
  enabled: undefined,
  page: 1,
  pageSize: 20,
})
const page = reactive<PageResult<AutoRouteConfigRsp>>({ list: [], total: 0, page: 1, pageSize: 20 })
const loading = ref(false)
const loadError = ref(false)

// 配置弹窗状态
const configDialogVisible = ref(false)
const isConfigEdit = ref(false)
const editingConfig = ref<AutoRouteConfigRsp>()

// 候选模型抽屉状态
const candidateDrawerVisible = ref(false)
const selectedConfig = ref<AutoRouteConfigRsp>()

const hasActiveFilters = computed(() =>
  Boolean(query.routeKey || query.displayName || query.enabled !== undefined),
)

async function loadData() {
  loading.value = true
  loadError.value = false
  try {
    const result = await fetchAutoRoutePage(query)
    Object.assign(page, result)
  } catch {
    loadError.value = true
    Object.assign(page, { list: [], total: 0, page: query.page, pageSize: query.pageSize })
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  loadData()
}

function resetQuery() {
  query.routeKey = ''
  query.displayName = ''
  query.enabled = undefined
  query.page = 1
  loadData()
}

function openCreate() {
  isConfigEdit.value = false
  editingConfig.value = undefined
  configDialogVisible.value = true
}

function openEdit(row: AutoRouteConfigRsp) {
  isConfigEdit.value = true
  editingConfig.value = row
  configDialogVisible.value = true
}

function openCandidates(row: AutoRouteConfigRsp) {
  selectedConfig.value = row
  candidateDrawerVisible.value = true
}

async function toggleConfig(row: AutoRouteConfigRsp) {
  const action = row.enabled ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(
      `${action}后运行时路由会立即刷新，是否继续？`,
      `${action} Auto 配置`,
      { type: 'warning' },
    )
    await toggleAutoRouteConfig({ id: row.id, versionNo: row.versionNo })
    ElMessage.success(`Auto 配置已${action}`)
    await loadData()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
  }
}

async function removeConfig(row: AutoRouteConfigRsp) {
  try {
    await ElMessageBox.confirm(
      '删除后将同步删除候选模型并刷新运行时配置，是否继续？',
      '删除 Auto 配置',
      { type: 'warning' },
    )
    await deleteAutoRouteConfig({ id: row.id, versionNo: row.versionNo })
    ElMessage.success('Auto 配置删除成功')
    await loadData()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
  }
}

function formatStrategy(strategy: string) {
  if (strategy === 'SMART_SCORE' || strategy === 'PRIORITY') {
    return '智能评分'
  }
  return strategy
}

onMounted(loadData)
</script>

<style scoped>
.route-hint {
  margin-bottom: 16px;
}
</style>
