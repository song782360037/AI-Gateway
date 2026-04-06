<template>
  <el-dialog
    :model-value="visible"
    :title="isEdit ? '编辑提供商' : '新增提供商'"
    width="780px"
    destroy-on-close
    class="admin-dialog"
    modal-class="admin-dialog-overlay"
    @close="emit('close')"
  >
    <div class="dialog-intro">
      <p class="eyebrow">提供商配置</p>
      <p>维护提供商的基础连接信息、密钥参数和运行时调度配置。</p>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <!-- 基础信息 -->
      <section class="dialog-section">
        <div class="dialog-section__head">
          <h4>基础信息</h4>
          <p>定义提供商的唯一标识、类型、显示名称和接口地址。</p>
        </div>
        <div class="form-grid">
          <el-form-item label="提供商唯一标识" prop="providerCode">
            <el-input v-model="form.providerCode" placeholder="如 openai-main" />
          </el-form-item>
          <el-form-item label="提供商类型" prop="providerType">
            <el-select v-model="form.providerType" placeholder="请选择提供商类型" style="width: 100%">
              <el-option
                v-for="item in providerTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="显示名称" prop="displayName">
            <el-input v-model="form.displayName" placeholder="如 OpenAI 主通道" />
          </el-form-item>
          <el-form-item label="接口地址" prop="baseUrl">
            <el-input v-model="form.baseUrl" placeholder="https://api.openai.com" />
          </el-form-item>
        </div>
      </section>

      <!-- 密钥与调度 -->
      <section class="dialog-section">
        <div class="dialog-section__head">
          <h4>密钥与调度</h4>
          <p>配置 API 密钥、请求超时和优先级，决定实际调用行为。</p>
        </div>
        <div class="form-grid">
          <el-form-item :label="isEdit ? 'API 密钥（留空表示不修改）' : 'API 密钥'" prop="apiKey">
            <el-input v-model="form.apiKey" type="password" show-password placeholder="新增必填，编辑留空不修改" />
          </el-form-item>
          <div class="form-grid__inline">
            <el-form-item label="超时（秒）" prop="timeoutSeconds">
              <el-input-number v-model="form.timeoutSeconds" :min="1" :step="10" style="width: 100%" />
            </el-form-item>
            <el-form-item label="优先级" prop="priority">
              <el-input-number v-model="form.priority" :step="1" style="width: 100%" />
            </el-form-item>
          </div>
        </div>

        <div class="dialog-switch-row">
          <div>
            <p class="dialog-switch-row__title">启用状态</p>
            <p class="dialog-switch-row__desc">关闭后该提供商不会继续参与运行时路由。</p>
          </div>
          <el-switch v-model="form.enabled" inline-prompt active-text="开" inactive-text="关" />
        </div>
      </section>

      <!-- 支持协议 -->
      <section class="dialog-section">
        <div class="dialog-section__head">
          <h4>支持协议</h4>
          <p>选择该提供商支持的下游请求协议。不勾选表示支持所有协议。</p>
        </div>
        <el-checkbox-group v-model="form.supportedProtocols">
          <el-checkbox
            v-for="item in protocolOptions"
            :key="item.value"
            :value="item.value"
            :label="item.label"
          />
        </el-checkbox-group>
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

interface ProviderFormModel {
  id?: number
  versionNo?: number
  providerCode: string
  providerType: string
  displayName: string
  enabled: boolean
  baseUrl: string
  apiKey: string
  timeoutSeconds: number
  priority: number
  supportedProtocols: string[]
}

const providerTypeOptions = [
  { value: 'OPENAI', label: 'OpenAI Chat Completions' },
  { value: 'OPENAI_RESPONSES', label: 'OpenAI Responses API' },
  { value: 'ANTHROPIC', label: 'Anthropic (Claude)' },
  { value: 'GEMINI', label: 'Google Gemini' },
] as const

/** 下游协议选项 */
const protocolOptions = [
  { value: 'OPENAI_CHAT', label: 'OpenAI Chat' },
  { value: 'OPENAI_RESPONSES', label: 'OpenAI Responses' },
  { value: 'ANTHROPIC', label: 'Anthropic' },
  { value: 'GEMINI', label: 'Gemini' },
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
  providerCode: [{ required: true, message: '请输入提供商唯一标识', trigger: 'blur' }],
  providerType: [{ required: true, message: '请选择提供商类型', trigger: 'change' }],
  baseUrl: [{ required: true, message: '请输入接口地址', trigger: 'blur' }],
  apiKey: [{ validator: validateApiKey, trigger: 'blur' }],
}

watch(
  () => props.modelValue,
  (value) => {
    isEdit.value = !!value
    const nextForm = buildFormState(value)

    // 保留一份初始快照，编辑态点击"重置"时可以回到原始值
    initialSnapshot.value = nextForm
    Object.assign(form, nextForm)
  },
  { immediate: true },
)

function buildFormState(value?: ProviderConfigRsp | null): ProviderFormModel {
  if (!value) return createEmptyForm()

  return {
    id: value.id,
    versionNo: value.versionNo,
    providerCode: value.providerCode,
    providerType: value.providerType,
    displayName: value.displayName || '',
    enabled: value.enabled,
    baseUrl: value.baseUrl,
    apiKey: '',
    timeoutSeconds: value.timeoutSeconds,
    priority: value.priority,
    supportedProtocols: value.supportedProtocols ?? [],
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
    timeoutSeconds: 60,
    priority: 0,
    supportedProtocols: [],
  }
}

function validateApiKey(_rule: unknown, value: string, callback: (error?: Error) => void) {
  // 新增时必须填写 apiKey；编辑时允许留空表示不修改
  if (!isEdit.value && !value) {
    callback(new Error('新增时必须填写 API 密钥'))
    return
  }
  callback()
}

function resetForm() {
  Object.assign(form, initialSnapshot.value)
  formRef.value?.clearValidate()
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  const basePayload = {
    providerCode: form.providerCode,
    providerType: form.providerType,
    displayName: form.displayName,
    enabled: form.enabled,
    baseUrl: form.baseUrl,
    apiKey: form.apiKey,
    timeoutSeconds: form.timeoutSeconds,
    priority: form.priority,
    supportedProtocols: form.supportedProtocols,
  }

  emit('submit', isEdit.value ? { ...basePayload, id: form.id, versionNo: form.versionNo } : basePayload)
}
</script>

<style scoped>
/* 超时和优先级在同一行并排显示 */
.form-grid__inline {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
</style>
