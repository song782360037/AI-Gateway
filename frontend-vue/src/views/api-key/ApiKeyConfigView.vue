<template>
  <ConsoleLayout>
    <div class="page-card">
      <div class="card-header">
        <span class="card-header__title">API Key 列表</span>
        <div class="card-header__actions">
          <el-button type="primary" size="small" @click="openCreate">新增 Key</el-button>
          <el-button size="small" @click="loadData">刷新</el-button>
        </div>
      </div>

      <el-form :inline="true" :model="query" class="filter-bar">
        <el-form-item label="名称">
          <el-input v-model="query.name" placeholder="按名称搜索" clearable size="default" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" style="width: 120px" size="default" clearable>
            <el-option label="启用" value="ACTIVE" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="default" @click="search">查询</el-button>
          <el-button size="default" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="page.list" stripe element-loading-text="正在加载...">
        <el-table-column prop="keyPrefix" label="前缀" min-width="100" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="状态" min-width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="每日限额" min-width="100">
          <template #default="{ row }">{{ row.dailyLimit ?? '不限' }}</template>
        </el-table-column>
        <el-table-column label="RPM 限流" min-width="90">
          <template #default="{ row }">{{ row.rpmLimit ?? '默认' }}</template>
        </el-table-column>
        <el-table-column label="累计限额" min-width="100">
          <template #default="{ row }">{{ row.totalLimit ?? '不限' }}</template>
        </el-table-column>
        <el-table-column prop="usedCount" label="已使用" min-width="90">
          <template #default="{ row }">{{ row.usedCount?.toLocaleString() ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="过期时间" min-width="160">
          <template #default="{ row }">{{ row.expireTime ?? '永不过期' }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="140">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="removeItem(row.id)">删除</el-button>
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
              <strong>{{ hasActiveFilters ? '没有匹配的 API Key' : '暂无 API Key' }}</strong>
              <p>{{ hasActiveFilters ? '尝试重置筛选条件' : '请点击"新增 Key"创建' }}</p>
              <div class="table-empty-state__actions">
                <el-button v-if="hasActiveFilters" size="small" @click="resetQuery">重置筛选</el-button>
                <el-button type="primary" size="small" @click="openCreate">新增 Key</el-button>
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

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑 API Key' : '新增 API Key'"
      width="520px"
      class="admin-dialog"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="备注用途，如「生产环境」" maxlength="128" />
        </el-form-item>
        <el-form-item v-if="!isEdit" label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isEdit" label="状态" prop="status">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="每日限额">
          <el-input-number v-model="form.dailyLimit" :min="1" :precision="0" placeholder="不限" style="width: 100%" />
        </el-form-item>
        <el-form-item label="RPM 限流">
          <el-input-number v-model="form.rpmLimit" :min="1" :precision="0" placeholder="使用全局默认" style="width: 100%" />
        </el-form-item>
        <el-form-item label="小时限流">
          <el-input-number v-model="form.hourlyLimit" :min="1" :precision="0" placeholder="使用全局默认" style="width: 100%" />
        </el-form-item>
        <el-form-item label="累计限额">
          <el-input-number v-model="form.totalLimit" :min="1" :precision="0" placeholder="不限" style="width: 100%" />
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker
            v-model="form.expireTime"
            type="datetime"
            placeholder="留空表示永不过期"
            style="width: 100%"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            {{ isEdit ? '保存' : '创建' }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 创建成功展示 Key 的对话框 -->
    <el-dialog v-model="keyRevealVisible" title="API Key 创建成功" width="520px" class="admin-dialog" :close-on-click-modal="false">
      <div class="key-reveal-intro">
        <el-alert type="warning" :closable="false" show-icon>
          <template #title>
            完整 Key 仅显示一次，关闭后无法再次查看。请立即复制保存。
          </template>
        </el-alert>
      </div>
      <div class="key-reveal-box">
        <code class="key-reveal-value">{{ revealedKey }}</code>
        <el-button type="primary" size="small" @click="copyKey">复制</el-button>
      </div>
      <template #footer>
        <el-button type="primary" @click="keyRevealVisible = false">我已保存</el-button>
      </template>
    </el-dialog>
  </ConsoleLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import ConsoleLayout from '../../layout/ConsoleLayout.vue'
import { addApiKey, deleteApiKey, fetchApiKeyPage, updateApiKey } from '../../api/api-key-config'
import type { PageResult } from '../../types/common'
import type {
  ApiKeyConfigAddReq,
  ApiKeyConfigQueryReq,
  ApiKeyConfigRsp,
  ApiKeyConfigUpdateReq,
} from '../../types/api-key-config'

const query = reactive<ApiKeyConfigQueryReq>({ name: '', status: undefined, page: 1, pageSize: 20 })
const page = reactive<PageResult<ApiKeyConfigRsp>>({ list: [], total: 0, page: 1, pageSize: 20 })
const loading = ref(false)
const loadError = ref(false)

// 对话框状态
const dialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref<number>(0)
const editingVersion = ref<number>(0)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  status: 'ACTIVE' as string,
  dailyLimit: undefined as number | undefined,
  rpmLimit: undefined as number | undefined,
  hourlyLimit: undefined as number | undefined,
  totalLimit: undefined as number | undefined,
  expireTime: undefined as string | undefined,
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}

// Key 展示
const keyRevealVisible = ref(false)
const revealedKey = ref('')

const hasActiveFilters = computed(() => Boolean(query.name || query.status))

async function loadData() {
  loading.value = true
  loadError.value = false
  try {
    const result = await fetchApiKeyPage(query)
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
  query.name = ''
  query.status = undefined
  query.page = 1
  await loadData()
}

function openCreate() {
  isEdit.value = false
  dialogVisible.value = true
}

function openEdit(row: ApiKeyConfigRsp) {
  isEdit.value = true
  editingId.value = row.id
  editingVersion.value = row.versionNo
  form.name = row.name
  form.status = row.status
  form.dailyLimit = row.dailyLimit ?? undefined
  form.rpmLimit = row.rpmLimit ?? undefined
  form.hourlyLimit = row.hourlyLimit ?? undefined
  form.totalLimit = row.totalLimit ?? undefined
  form.expireTime = row.expireTime ?? undefined
  dialogVisible.value = true
}

function resetForm() {
  form.name = ''
  form.status = 'ACTIVE'
  form.dailyLimit = undefined
  form.rpmLimit = undefined
  form.hourlyLimit = undefined
  form.totalLimit = undefined
  form.expireTime = undefined
  formRef.value?.resetFields()
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (isEdit.value) {
        const payload: ApiKeyConfigUpdateReq = {
          id: editingId.value,
          versionNo: editingVersion.value,
          name: form.name,
          status: form.status,
          dailyLimit: form.dailyLimit ?? null,
          rpmLimit: form.rpmLimit ?? null,
          hourlyLimit: form.hourlyLimit ?? null,
          totalLimit: form.totalLimit ?? null,
          expireTime: form.expireTime ?? null,
        }
        await updateApiKey(payload)
        ElMessage.success('API Key 更新成功')
      } else {
        const payload: ApiKeyConfigAddReq = {
          name: form.name,
          status: form.status,
          dailyLimit: form.dailyLimit ?? null,
          rpmLimit: form.rpmLimit ?? null,
          hourlyLimit: form.hourlyLimit ?? null,
          totalLimit: form.totalLimit ?? null,
          expireTime: form.expireTime ?? null,
        }
        const created = await addApiKey(payload)
        ElMessage.success('API Key 创建成功')
        // 展示完整 Key
        if (created?.apiKey) {
          revealedKey.value = created.apiKey
          keyRevealVisible.value = true
        }
      }
      dialogVisible.value = false
      await loadData()
    } catch {
      // 错误已由拦截器处理
    } finally {
      submitting.value = false
    }
  })
}

async function removeItem(id: number) {
  try {
    await ElMessageBox.confirm('删除后将不可恢复，是否继续？', '删除 API Key', { type: 'warning' })
    await deleteApiKey(id)
    ElMessage.success('API Key 删除成功')
    await loadData()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
  }
}

async function copyKey() {
  try {
    await navigator.clipboard.writeText(revealedKey.value)
    ElMessage.success('已复制到剪贴板')
  } catch {
    // 回退方案
    const textarea = document.createElement('textarea')
    textarea.value = revealedKey.value
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    ElMessage.success('已复制到剪贴板')
  }
}

onMounted(loadData)
</script>

<style scoped>
.key-reveal-intro {
  margin-bottom: 16px;
}

.key-reveal-box {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 6px;
  border: 1px solid #e4e7ed;
}

.key-reveal-value {
  flex: 1;
  font-family: 'Courier New', Courier, monospace;
  font-size: 14px;
  color: #303133;
  word-break: break-all;
}
</style>
