<template>
  <div>
    <div class="card-header" style="margin-bottom: 16px">
      <div class="card-header__actions">
        <el-button type="primary" size="small" @click="openCreate">新增模型</el-button>
        <el-button size="small" :loading="syncing" @click="handleSync">从路由别名同步</el-button>
        <el-button size="small" @click="loadData">刷新</el-button>
      </div>
    </div>

    <el-alert class="model-hint" type="info" :closable="false" show-icon>
      <template #title>
        管理对外暴露的模型列表，启用后的模型将通过 GET /v1/models
        接口返回给客户端。点击"从路由别名同步"可一键导入已有的路由别名。
      </template>
    </el-alert>

    <el-form :inline="true" :model="query" class="filter-bar">
      <el-form-item label="模型标识">
        <el-input v-model.trim="query.modelId" placeholder="如 gpt-4o" clearable />
      </el-form-item>
      <el-form-item label="名称">
        <el-input v-model.trim="query.displayName" placeholder="按展示名称搜索" clearable />
      </el-form-item>
      <el-form-item label="所有者">
        <el-input v-model.trim="query.ownedBy" placeholder="如 openai" clearable />
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
      <el-table-column prop="modelId" label="模型标识" min-width="160" align="center">
        <template #default="{ row }">
          <el-tag type="primary" effect="plain">{{ row.modelId }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="displayName" label="展示名称" min-width="140" align="center" />
      <el-table-column prop="ownedBy" label="所有者" min-width="110" align="center">
        <template #default="{ row }">{{ row.ownedBy || '-' }}</template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" min-width="80" align="center" />
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
      <el-table-column label="操作" fixed="right" width="200" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button
            link
            :type="row.enabled ? 'warning' : 'success'"
            size="small"
            @click="toggleModel(row)"
          >
            {{ row.enabled ? '禁用' : '启用' }}
          </el-button>
          <el-button link type="danger" size="small" @click="removeModel(row)">删除</el-button>
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
            <strong>{{ hasActiveFilters ? '没有匹配的模型' : '暂无支持模型' }}</strong>
            <p>
              {{ hasActiveFilters ? '尝试重置筛选条件' : '请点击"新增模型"或"从路由别名同步"创建' }}
            </p>
            <div class="table-empty-state__actions">
              <el-button v-if="hasActiveFilters" size="small" @click="resetQuery"
                >重置筛选</el-button
              >
              <el-button type="primary" size="small" @click="openCreate">新增模型</el-button>
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑模型' : '新增模型'"
      width="520px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="90px"
        label-position="right"
      >
        <el-form-item label="模型标识" prop="modelId">
          <el-input
            v-model.trim="form.modelId"
            :disabled="isEdit"
            placeholder="如 gpt-4o、claude-3-opus"
            maxlength="128"
          />
        </el-form-item>
        <el-form-item label="展示名称" prop="displayName">
          <el-input v-model.trim="form.displayName" placeholder="如 GPT-4o" maxlength="128" />
        </el-form-item>
        <el-form-item label="所有者" prop="ownedBy">
          <el-input v-model.trim="form.ownedBy" placeholder="如 openai、anthropic" maxlength="64" />
        </el-form-item>
        <el-form-item label="排序权重" prop="sortOrder">
          <el-input-number
            v-model="form.sortOrder"
            :min="0"
            :max="9999"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item label="启用" prop="enabled">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">
          {{ submitting ? '保存中...' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  addSupportedModel,
  deleteSupportedModel,
  fetchSupportedModelPage,
  syncFromRedirect,
  toggleSupportedModel,
  updateSupportedModel,
} from '../../api/supported-model'
import type { PageResult } from '../../types/common'
import type {
  SupportedModelAddReq,
  SupportedModelQueryReq,
  SupportedModelRsp,
  SupportedModelUpdateReq,
} from '../../types/supported-model'

const query = reactive<SupportedModelQueryReq>({
  modelId: '',
  displayName: '',
  ownedBy: '',
  enabled: undefined,
  page: 1,
  pageSize: 20,
})
const page = reactive<PageResult<SupportedModelRsp>>({ list: [], total: 0, page: 1, pageSize: 20 })
const loading = ref(false)
const loadError = ref(false)
const syncing = ref(false)

// 弹窗状态
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editingRow = ref<SupportedModelRsp>()

const form = reactive<SupportedModelAddReq & { sortOrder: number }>({
  modelId: '',
  displayName: '',
  ownedBy: '',
  enabled: true,
  sortOrder: 0,
})

const formRules: FormRules = {
  modelId: [{ required: true, message: '请输入模型标识', trigger: 'blur' }],
  displayName: [{ required: true, message: '请输入展示名称', trigger: 'blur' }],
}

const hasActiveFilters = computed(() =>
  Boolean(query.modelId || query.displayName || query.ownedBy || query.enabled !== undefined),
)

async function loadData() {
  loading.value = true
  loadError.value = false
  try {
    const result = await fetchSupportedModelPage(query)
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
  query.modelId = ''
  query.displayName = ''
  query.ownedBy = ''
  query.enabled = undefined
  query.page = 1
  loadData()
}

function openCreate() {
  isEdit.value = false
  editingRow.value = undefined
  Object.assign(form, { modelId: '', displayName: '', ownedBy: '', enabled: true, sortOrder: 0 })
  dialogVisible.value = true
}

function openEdit(row: SupportedModelRsp) {
  isEdit.value = true
  editingRow.value = row
  Object.assign(form, {
    modelId: row.modelId,
    displayName: row.displayName,
    ownedBy: row.ownedBy,
    enabled: row.enabled,
    sortOrder: row.sortOrder,
  })
  dialogVisible.value = true
}

function resetForm() {
  formRef.value?.clearValidate()
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      if (isEdit.value && editingRow.value) {
        const updateReq: SupportedModelUpdateReq = {
          id: editingRow.value.id,
          versionNo: editingRow.value.versionNo,
          modelId: form.modelId,
          displayName: form.displayName,
          ownedBy: form.ownedBy || '',
          enabled: form.enabled,
          sortOrder: form.sortOrder,
        }
        await updateSupportedModel(updateReq)
        ElMessage.success('模型更新成功')
      } else {
        await addSupportedModel({
          modelId: form.modelId,
          displayName: form.displayName,
          ownedBy: form.ownedBy || '',
          enabled: form.enabled,
          sortOrder: form.sortOrder,
        })
        ElMessage.success('模型新增成功')
      }
      dialogVisible.value = false
      await loadData()
    } catch {
      // 错误信息由请求拦截器统一处理
    } finally {
      submitting.value = false
    }
  })
}

async function toggleModel(row: SupportedModelRsp) {
  const action = row.enabled ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`${action}后运行时配置会立即刷新，是否继续？`, `${action}模型`, {
      type: 'warning',
    })
    await toggleSupportedModel(row.id, row.versionNo)
    ElMessage.success(`模型已${action}`)
    await loadData()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
  }
}

async function removeModel(row: SupportedModelRsp) {
  try {
    await ElMessageBox.confirm('删除后运行时配置会立即刷新，是否继续？', '删除模型', {
      type: 'warning',
    })
    await deleteSupportedModel(row.id, row.versionNo)
    ElMessage.success('模型删除成功')
    await loadData()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
  }
}

async function handleSync() {
  try {
    await ElMessageBox.confirm(
      '将从路由别名中导入不重复的 EXACT 精确匹配别名作为模型标识，是否继续？',
      '从路由别名同步',
      { type: 'info' },
    )
    syncing.value = true
    try {
      const count = await syncFromRedirect()
      ElMessage.success(`同步完成，共导入 ${count} 条模型`)
      await loadData()
    } catch {
      // 错误信息由请求拦截器统一处理
    } finally {
      syncing.value = false
    }
  } catch {
    // 用户取消
  }
}

onMounted(loadData)
</script>

<style scoped>
.model-hint {
  margin-bottom: 16px;
}
</style>
