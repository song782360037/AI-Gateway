<template>
  <el-dialog
    :model-value="visible"
    :title="isEdit ? '编辑 Provider' : '新增 Provider'"
    width="760px"
    destroy-on-close
    @close="emit('close')"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <div class="form-grid">
        <el-form-item label="providerCode" prop="providerCode">
          <el-input v-model="form.providerCode" placeholder="openai-main" />
        </el-form-item>
        <el-form-item label="providerType" prop="providerType">
          <el-input v-model="form.providerType" placeholder="OPENAI" />
        </el-form-item>
        <el-form-item label="displayName" prop="displayName">
          <el-input v-model="form.displayName" placeholder="OpenAI 主通道" />
        </el-form-item>
        <el-form-item label="baseUrl" prop="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="https://api.openai.com" />
        </el-form-item>
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

      <el-form-item label="enabled" prop="enabled">
        <el-switch v-model="form.enabled" inline-prompt active-text="开" inactive-text="关" />
      </el-form-item>

      <el-form-item label="extConfigJson" prop="extConfigJson">
        <el-input v-model="form.extConfigJson" type="textarea" :rows="4" placeholder='{"region":"us-east-1"}' />
      </el-form-item>
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
import { formatJsonText, normalizeJsonText } from '../../utils/json'

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

const isEdit = ref(false)

const rules: FormRules<ProviderFormModel> = {
  providerCode: [{ required: true, message: '请输入 providerCode', trigger: 'blur' }],
  providerType: [{ required: true, message: '请输入 providerType', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请输入 baseUrl', trigger: 'blur' }],
  apiKey: [{ validator: validateApiKey, trigger: 'blur' }],
}

watch(
  () => props.modelValue,
  (value) => {
    isEdit.value = !!value
    Object.assign(form, createEmptyForm())

    if (!value) {
      return
    }

    Object.assign(form, {
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
    })
  },
  { immediate: true },
)

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

function resetForm() {
  Object.assign(form, createEmptyForm())
  formRef.value?.clearValidate()
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  emit('submit', {
    ...form,
    extConfigJson: normalizeJsonText(form.extConfigJson),
  })
}
</script>
