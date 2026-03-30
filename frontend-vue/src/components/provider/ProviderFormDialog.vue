<template>
  <el-dialog
    :model-value="visible"
    :title="isEdit ? '编辑接入通道' : '新增接入通道'"
    width="840px"
    destroy-on-close
    class="admin-dialog"
    modal-class="admin-dialog-overlay"
    @close="emit('close')"
  >
    <div class="dialog-intro">
      <p class="eyebrow">接入通道编辑</p>
      <p>维护接入通道的基础信息、密钥参数和运行时扩展配置。</p>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <section class="dialog-section">
        <div class="dialog-section__head">
          <h4>基础信息</h4>
          <p>定义接入通道的编码、类型、名称和基础访问地址。</p>
        </div>
        <div class="form-grid">
          <el-form-item label="providerCode" prop="providerCode">
            <el-input v-model="form.providerCode" placeholder="openai-main" />
          </el-form-item>
          <el-form-item label="providerType" prop="providerType">
            <el-select v-model="form.providerType" placeholder="请选择接入通道类型">
              <el-option
                v-for="item in providerTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
                :disabled="'disabled' in item"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="displayName" prop="displayName">
            <el-input v-model="form.displayName" placeholder="OpenAI 主通道" />
          </el-form-item>
          <el-form-item label="baseUrl" prop="baseUrl">
            <el-input v-model="form.baseUrl" placeholder="https://api.openai.com" />
          </el-form-item>
        </div>
      </section>

      <section class="dialog-section">
        <div class="dialog-section__head">
          <h4>运行参数</h4>
          <p>配置密钥、版本、超时时间和优先级，决定实际调用行为。</p>
        </div>
        <div class="form-grid">
          <el-form-item :label="isEdit ? 'apiKey（留空表示不修改）' : 'apiKey'" prop="apiKey">
            <el-input v-model="form.apiKey" type="password" show-password placeholder="新增必填，编辑留空表示不修改" />
          </el-form-item>
          <el-form-item label="apiVersion" prop="apiVersion">
            <el-input v-model="form.apiVersion" placeholder="2024-10-01" />
          </el-form-item>
          <el-form-item label="timeoutSeconds" prop="timeoutSeconds">
            <el-input-number v-model="form.timeoutSeconds" :min="1" :step="10" />
          </el-form-item>
          <el-form-item label="priority" prop="priority">
            <el-input-number v-model="form.priority" :step="1" />
          </el-form-item>
        </div>

        <div class="dialog-switch-row">
          <div>
            <p class="dialog-switch-row__title">启用状态</p>
            <p class="dialog-switch-row__desc">关闭后该 Provider 不会继续参与运行时路由。</p>
          </div>
          <el-switch v-model="form.enabled" inline-prompt active-text="开" inactive-text="关" />
        </div>
      </section>

      <section class="dialog-section">
        <div class="dialog-section__head">
          <h4>扩展配置</h4>
          <p>用于存放区域、标签或其他 Provider 特有扩展参数。</p>
        </div>
        <el-form-item label="extConfigJson" prop="extConfigJson">
          <el-input v-model="form.extConfigJson" type="textarea" :rows="5" placeholder='{"region":"us-east-1"}' />
        </el-form-item>
      </section>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="emit('close')">取消</el-button>
        <el-button type="warning" plain @click="resetForm">重置</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { ProviderConfigRsp } from '../../types/provider'
import { formatJsonText, isValidJsonText, normalizeJsonText } from '../../utils/json'

interface ProviderFormModel {
  id?: number
  versionNo?: number
  providerCode: string
  providerType: string
  displayName: string
  enabled: boolean
  baseUrl: string
  apiKey: string
  apiVersion: string
  timeoutSeconds: number
  priority: number
  extConfigJson: string
}

const providerTypeOptions = [
  { value: 'OPENAI', label: 'OPENAI' },
  { value: 'ANTHROPIC', label: 'ANTHROPIC（暂未实现）', disabled: true },
  { value: 'GEMINI', label: 'GEMINI（暂未实现）', disabled: true },
] as const

const props = defineProps<{
  visible: boolean
  modelValue?: ProviderConfigRsp | null
}>()

const emit = defineEmits<{
  close: []
  submit: [payload: ProviderFormModel]
}>()

const formRef = ref<FormInstance>()
const form = reactive<ProviderFormModel>(createEmptyForm())
const initialSnapshot = ref<ProviderFormModel>(createEmptyForm())
const isEdit = ref(false)

const rules: FormRules<ProviderFormModel> = {
  providerCode: [{ required: true, message: '请输入 providerCode', trigger: 'blur' }],
  providerType: [{ required: true, message: '请选择 providerType', trigger: 'change' }],
  baseUrl: [{ required: true, message: '请输入 baseUrl', trigger: 'blur' }],
  apiKey: [{ validator: validateApiKey, trigger: 'blur' }],
  extConfigJson: [{ validator: validateJsonText, trigger: 'blur' }],
}

watch(
  () => props.modelValue,
  (value) => {
    isEdit.value = !!value
    const nextForm = buildFormState(value)

    // 保留一份初始快照，编辑态点击“重置”时可以回到原始值，而不是回到新增默认值。
    initialSnapshot.value = nextForm
    Object.assign(form, nextForm)
  },
  { immediate: true },
)

function buildFormState(value?: ProviderConfigRsp | null): ProviderFormModel {
  if (!value) {
    return createEmptyForm()
  }

  return {
    id: value.id,
    versionNo: value.versionNo,
    providerCode: value.providerCode,
    providerType: value.providerType,
    displayName: value.displayName || '',
    enabled: value.enabled,
    baseUrl: value.baseUrl,
    apiKey: '',
    apiVersion: value.apiVersion || '',
    timeoutSeconds: value.timeoutSeconds,
    priority: value.priority,
    extConfigJson: formatJsonText(value.extConfigJson),
  }
}

function createEmptyForm(): ProviderFormModel {
  return {
    providerCode: '',
    providerType: 'OPENAI',
    displayName: '',
    enabled: true,
    baseUrl: '',
    apiKey: '',
    apiVersion: '',
    timeoutSeconds: 60,
    priority: 0,
    extConfigJson: '',
  }
}

function validateApiKey(_rule: unknown, value: string, callback: (error?: Error) => void) {
  // 新增时必须填写 apiKey；编辑时允许留空表示不修改。
  if (!isEdit.value && !value) {
    callback(new Error('新增时必须填写 apiKey'))
    return
  }
  callback()
}

function validateJsonText(_rule: unknown, value: string, callback: (error?: string | Error) => void) {
  if (isValidJsonText(value)) {
    callback()
    return
  }

  callback(new Error('请输入合法的 JSON'))
}

function resetForm() {
  Object.assign(form, initialSnapshot.value)
  formRef.value?.clearValidate()
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  const basePayload = {
    providerCode: form.providerCode,
    providerType: form.providerType,
    displayName: form.displayName,
    enabled: form.enabled,
    baseUrl: form.baseUrl,
    apiKey: form.apiKey,
    apiVersion: form.apiVersion,
    timeoutSeconds: form.timeoutSeconds,
    priority: form.priority,
    extConfigJson: normalizeJsonText(form.extConfigJson),
  }

  emit('submit', isEdit.value ? { ...basePayload, id: form.id, versionNo: form.versionNo } : basePayload)
}
</script>
