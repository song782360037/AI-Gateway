<template>
  <ConsoleLayout>
    <template #hero-actions>
      <el-button type="warning" plain @click="openCreate">新增 Provider</el-button>
      <el-button type="primary" @click="loadData">刷新列表</el-button>
    </template>

    <section class="single-panel">
      <article class="panel-frame focus-panel">
        <div class="panel-head">
          <div>
            <p class="eyebrow">provider registry</p>
            <h3>Provider 配置</h3>
          </div>
          <div class="table-meta">
            <span>{{ page.total }} records</span>
          </div>
        </div>

        <el-form :inline="true" :model="query" class="filter-bar">
          <el-form-item label="编码">
            <el-input v-model="query.providerCode" placeholder="openai-main" clearable />
          </el-form-item>
          <el-form-item label="类型">
            <el-input v-model="query.providerType" placeholder="OPENAI" clearable />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="enabledFilter" placeholder="全部" clearable style="width: 140px">
              <el-option label="全部" value="all" />
              <el-option label="启用" value="true" />
              <el-option label="禁用" value="false" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadData">查询</el-button>
            <el-button @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-table :data="page.list" class="neon-table" stripe>
          <el-table-column prop="providerCode" label="编码" min-width="160" />
          <el-table-column prop="providerType" label="类型" min-width="120" />
          <el-table-column prop="displayName" label="名称" min-width="160" />
          <el-table-column label="状态" min-width="100">
            <template #default="scope">
              <el-tag :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? '启用' : '禁用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="priority" label="优先级" min-width="100" />
          <el-table-column prop="apiKeyMasked" label="密钥" min-width="140" />
          <el-table-column label="操作" fixed="right" min-width="160">
            <template #default="scope">
              <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
              <el-button link type="danger" @click="removeItem(scope.row.id)">删除</el-button>
            </template>
          </el-table-column>
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
      </article>
    </section>

    <ProviderFormDialog
      :visible="dialogVisible"
      :model-value="currentItem"
      @close="closeDialog"
      @submit="submitForm"
    />
  </ConsoleLayout>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
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

async function loadData() {
  const result = await fetchProviderPage({
    ...query,
    enabled: enabledFilter.value === 'all' ? undefined : enabledFilter.value === 'true',
  })
  Object.assign(page, result)
}

function resetQuery() {
  query.providerCode = ''
  query.providerType = ''
  query.page = 1
  enabledFilter.value = 'all'
  loadData()
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
  if ('id' in payload) {
    await updateProvider(payload)
    ElMessage.success('Provider 更新成功')
  } else {
    await addProvider(payload)
    ElMessage.success('Provider 新增成功')
  }

  dialogVisible.value = false
  await loadData()
}

async function removeItem(id: number) {
  await ElMessageBox.confirm('删除后将不可恢复，是否继续？', '删除 Provider', {
    type: 'warning',
  })
  await deleteProvider(id)
  ElMessage.success('Provider 删除成功')
  await loadData()
}

onMounted(loadData)
</script>
