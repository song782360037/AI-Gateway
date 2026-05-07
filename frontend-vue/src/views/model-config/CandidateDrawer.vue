<template>
  <!-- 候选模型抽屉 -->
  <el-drawer
    :model-value="visible"
    :title="drawerTitle"
    size="720px"
    @update:model-value="$emit('update:visible', $event)"
    @closed="resetState"
  >
    <div class="candidate-toolbar">
      <el-button type="primary" size="small" :disabled="!configData" @click="openCreate"
        >新增候选模型</el-button
      >
      <el-button size="small" :loading="detailLoading" :disabled="!configData" @click="reloadDetail"
        >刷新</el-button
      >
    </div>

    <el-table v-loading="detailLoading" :data="candidates" stripe>
      <el-table-column prop="providerCode" label="Provider" min-width="140" align="center" />
      <el-table-column prop="targetModel" label="目标模型" min-width="170" align="center" />
      <el-table-column prop="priority" label="优先级" min-width="90" align="center" />
      <el-table-column prop="weight" label="权重" min-width="80" align="center" />
      <el-table-column label="能力" min-width="210" align="center">
        <template #default="{ row }">
          <div class="candidate-tags">
            <el-tag v-if="row.supportsVision" size="small" effect="plain">视觉</el-tag>
            <el-tag v-if="row.supportsTools" size="small" effect="plain">工具</el-tag>
            <el-tag v-if="row.supportsReasoning" size="small" effect="plain">推理</el-tag>
            <el-tag v-if="row.supportsJson" size="small" effect="plain">JSON</el-tag>
            <el-tag v-if="row.supportsStream" size="small" effect="plain">流式</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="评分" min-width="170" align="center">
        <template #default="{ row }">
          <span>质{{ row.qualityScore }} / 快{{ row.latencyScore }} / 省{{ row.costScore }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="210" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button
            link
            :type="row.enabled ? 'warning' : 'success'"
            size="small"
            @click="toggleCandidate(row)"
          >
            {{ row.enabled ? '禁用' : '启用' }}
          </el-button>
          <el-button link type="danger" size="small" @click="removeCandidate(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <div class="table-empty-state">
          <strong>暂无候选模型</strong>
          <p>请添加至少一个 Provider + 目标模型，auto 路由才能生效</p>
          <div class="table-empty-state__actions">
            <el-button type="primary" size="small" @click="openCreate">新增候选模型</el-button>
          </div>
        </div>
      </template>
    </el-table>
  </el-drawer>

  <!-- 候选模型表单弹窗 -->
  <el-dialog
    v-model="dialogVisible"
    :title="isEdit ? '编辑候选模型' : '新增候选模型'"
    width="760px"
    class="admin-dialog"
    destroy-on-close
    @closed="resetForm"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
      <el-form-item label="Provider" prop="providerCode">
        <el-select
          v-model="form.providerCode"
          filterable
          style="width: 100%"
          placeholder="请选择提供商"
        >
          <el-option
            v-for="provider in providerOptions"
            :key="provider.providerCode"
            :label="
              provider.displayName
                ? `${provider.displayName}（${provider.providerCode}）`
                : provider.providerCode
            "
            :value="provider.providerCode"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="目标模型" prop="targetModel">
        <el-input v-model.trim="form.targetModel" placeholder="如：gpt-4o-mini" maxlength="128" />
      </el-form-item>
      <el-form-item label="优先级" prop="priority">
        <el-input-number
          v-model="form.priority"
          :min="0"
          :max="9999"
          :precision="0"
          :step="1"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="权重" prop="weight">
        <el-input-number
          v-model="form.weight"
          :min="1"
          :max="10000"
          :precision="0"
          style="width: 100%"
        />
      </el-form-item>

      <el-divider content-position="left">能力约束</el-divider>
      <div class="form-grid">
        <el-form-item label="视觉输入">
          <el-switch v-model="form.supportsVision" />
        </el-form-item>
        <el-form-item label="工具调用">
          <el-switch v-model="form.supportsTools" />
        </el-form-item>
        <el-form-item label="强制工具">
          <el-switch v-model="form.supportsToolChoiceRequired" />
        </el-form-item>
        <el-form-item label="推理能力">
          <el-switch v-model="form.supportsReasoning" />
        </el-form-item>
        <el-form-item label="JSON 输出">
          <el-switch v-model="form.supportsJson" />
        </el-form-item>
        <el-form-item label="流式输出">
          <el-switch v-model="form.supportsStream" />
        </el-form-item>
      </div>
      <div class="form-grid">
        <el-form-item label="输入上限">
          <el-input-number
            v-model="form.maxInputTokens"
            :min="0"
            :precision="0"
            :step="1000"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="输出上限">
          <el-input-number
            v-model="form.maxOutputTokens"
            :min="0"
            :precision="0"
            :step="1000"
            style="width: 100%"
          />
        </el-form-item>
      </div>

      <el-divider content-position="left">评分维度</el-divider>
      <div class="form-grid">
        <el-form-item label="质量评分">
          <el-input-number
            v-model="form.qualityScore"
            :min="0"
            :max="100"
            :precision="0"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="延迟评分">
          <el-input-number
            v-model="form.latencyScore"
            :min="0"
            :max="100"
            :precision="0"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="成本评分">
          <el-input-number
            v-model="form.costScore"
            :min="0"
            :max="100"
            :precision="0"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="工具评分">
          <el-input-number
            v-model="form.toolScore"
            :min="0"
            :max="100"
            :precision="0"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="视觉评分">
          <el-input-number
            v-model="form.visionScore"
            :min="0"
            :max="100"
            :precision="0"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="推理评分">
          <el-input-number
            v-model="form.reasoningScore"
            :min="0"
            :max="100"
            :precision="0"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="可靠评分">
          <el-input-number
            v-model="form.reliabilityScore"
            :min="0"
            :max="100"
            :precision="0"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="评分偏置">
          <el-input-number
            v-model="form.scoreBias"
            :min="-100"
            :max="100"
            :precision="0"
            style="width: 100%"
          />
        </el-form-item>
      </div>

      <el-form-item label="状态">
        <el-switch v-model="form.enabled" active-text="启用" inactive-text="禁用" />
      </el-form-item>
      <el-form-item label="说明">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          maxlength="512"
          show-word-limit
          placeholder="可填写适用场景、成本或性能说明"
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
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  addAutoRouteCandidate,
  deleteAutoRouteCandidate,
  getAutoRouteConfig,
  toggleAutoRouteCandidate,
  updateAutoRouteCandidate,
} from '../../api/auto-route-config'
import { fetchProviderPage } from '../../api/provider-config'
import type {
  AutoRouteCandidateAddReq,
  AutoRouteCandidateRsp,
  AutoRouteCandidateUpdateReq,
  AutoRouteConfigRsp,
} from '../../types/auto-route'
import type { ProviderConfigRsp } from '../../types/provider'

const props = defineProps<{
  visible: boolean
  configData?: AutoRouteConfigRsp
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'data-changed'): void
}>()

const detailLoading = ref(false)
const candidates = ref<AutoRouteCandidateRsp[]>([])
const providerOptions = ref<ProviderConfigRsp[]>([])

// 候选模型表单相关状态
const dialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref(0)
const editingVersion = ref(0)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const defaultForm = () => ({
  providerCode: '',
  targetModel: '',
  priority: 0,
  weight: 100,
  supportsVision: false,
  supportsTools: false,
  supportsToolChoiceRequired: false,
  supportsReasoning: false,
  supportsJson: true,
  supportsStream: true,
  maxInputTokens: undefined as number | undefined,
  maxOutputTokens: undefined as number | undefined,
  qualityScore: 50,
  latencyScore: 50,
  costScore: 50,
  toolScore: 50,
  visionScore: 50,
  reasoningScore: 50,
  reliabilityScore: 50,
  scoreBias: 0,
  enabled: true,
  description: '',
})

const form = reactive(defaultForm())

const rules: FormRules = {
  providerCode: [{ required: true, message: '请选择提供商', trigger: 'change' }],
  targetModel: [{ required: true, message: '请输入目标模型', trigger: 'blur' }],
  priority: [{ required: true, message: '请输入优先级', trigger: 'blur' }],
  weight: [{ required: true, message: '请输入权重', trigger: 'blur' }],
}

const drawerTitle = computed(() =>
  props.configData ? `${props.configData.displayName} - 候选模型` : '候选模型',
)

// 抽屉打开时加载 Provider 列表和候选模型详情
watch(
  () => props.visible,
  async (val) => {
    if (!val || !props.configData) return
    await Promise.all([loadProviders(), reloadDetail()])
  },
)

async function reloadDetail() {
  if (!props.configData) return
  detailLoading.value = true
  try {
    const detail = await getAutoRouteConfig(props.configData.id)
    candidates.value = detail.candidates ?? []
  } finally {
    detailLoading.value = false
  }
}

async function loadProviders() {
  try {
    const result = await fetchProviderPage({ enabled: true, page: 1, pageSize: 100 })
    providerOptions.value = result.list
  } catch {
    providerOptions.value = []
  }
}

function openCreate() {
  if (!props.configData) return
  isEdit.value = false
  dialogVisible.value = true
}

function openEdit(row: AutoRouteCandidateRsp) {
  isEdit.value = true
  editingId.value = row.id
  editingVersion.value = row.versionNo
  Object.assign(form, {
    providerCode: row.providerCode,
    targetModel: row.targetModel,
    priority: row.priority,
    weight: row.weight,
    supportsVision: row.supportsVision,
    supportsTools: row.supportsTools,
    supportsToolChoiceRequired: row.supportsToolChoiceRequired,
    supportsReasoning: row.supportsReasoning,
    supportsJson: row.supportsJson,
    supportsStream: row.supportsStream,
    maxInputTokens: row.maxInputTokens,
    maxOutputTokens: row.maxOutputTokens,
    qualityScore: row.qualityScore,
    latencyScore: row.latencyScore,
    costScore: row.costScore,
    toolScore: row.toolScore,
    visionScore: row.visionScore,
    reasoningScore: row.reasoningScore,
    reliabilityScore: row.reliabilityScore,
    scoreBias: row.scoreBias,
    enabled: row.enabled,
    description: row.description ?? '',
  })
  dialogVisible.value = true
}

function resetForm() {
  Object.assign(form, defaultForm())
  formRef.value?.resetFields()
}

function resetState() {
  candidates.value = []
}

async function handleSubmit() {
  if (!formRef.value || !props.configData) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    if (isEdit.value) {
      const payload: AutoRouteCandidateUpdateReq = {
        id: editingId.value,
        versionNo: editingVersion.value,
        ...form,
      }
      await updateAutoRouteCandidate(payload)
      ElMessage.success('候选模型更新成功')
    } else {
      const payload: AutoRouteCandidateAddReq = {
        configId: props.configData.id,
        ...form,
      }
      await addAutoRouteCandidate(payload)
      ElMessage.success('候选模型创建成功')
    }
    dialogVisible.value = false
    await reloadDetail()
    emit('data-changed')
  } catch {
    // 请求拦截器统一提示错误
  } finally {
    submitting.value = false
  }
}

async function toggleCandidate(row: AutoRouteCandidateRsp) {
  const action = row.enabled ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`${action}后运行时路由会立即刷新，是否继续？`, `${action}候选模型`, {
      type: 'warning',
    })
    await toggleAutoRouteCandidate({ id: row.id, versionNo: row.versionNo })
    ElMessage.success(`候选模型已${action}`)
    await reloadDetail()
    emit('data-changed')
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
  }
}

async function removeCandidate(row: AutoRouteCandidateRsp) {
  try {
    await ElMessageBox.confirm('删除后运行时路由会立即刷新，是否继续？', '删除候选模型', {
      type: 'warning',
    })
    await deleteAutoRouteCandidate({ id: row.id, versionNo: row.versionNo })
    ElMessage.success('候选模型删除成功')
    await reloadDetail()
    emit('data-changed')
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
  }
}
</script>

<style scoped>
.candidate-toolbar {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-bottom: 16px;
}

.candidate-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 4px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 16px;
}
</style>
