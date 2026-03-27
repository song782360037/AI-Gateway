<template>
  <el-dialog
    :model-value="visible"
    :title="isEdit ? '编辑 Redirect' : '新增 Redirect'"
    width="760px"
    destroy-on-close
    @close="emit('close')"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <div class="form-grid">
        <el-form-item label="aliasName" prop="aliasName">
          <el-input v-model="form.aliasName" placeholder="gpt-4o" />
        </el-form-item>
        <el-form-item label="providerCode" prop="providerCode">
          <el-input v-model="form.providerCode" placeholder="openai-main" />
        </el-form-item>
        <el-form-item label="targetModel" prop="targetModel">
          <el-input v-model="form.targetModel" placeholder="gpt-4o-2024-11-20" />
        </el-form-item>
        <el-form-item label="routeStrategy" prop="routeStrategy">
          <el-input v-model="form.routeStrategy" placeholder="PRIORITY" />
        </el-form-item>
        <el-form-item label="priority" prop="priority">
          <el-input-number v-model="form.priority" :step="1" />
        </el-form-item>
        <el-form-item label="weight" prop="weight">
          <el-input-number v-model="form.weight" :min="0" :step="10" />
        </el-form-item>
      </div>

      <el-form-item label="enabled" prop="enabled">
        <el-switch v-model="form.enabled" inline-prompt active-text="开" inactive-text="关" />
      </el-form-item>

      <el-form-item label="matchConditionJson" prop="matchConditionJson">
        <el-input v-model="form.matchConditionJson" type="textarea" :rows="3" placeholder='{"tenant":"default"}' />
      </el-form-item>

      <el-form-item label="extConfigJson" prop="extConfigJson">
        <el-input v-model="form.extConfigJson" type="textarea" :rows="3" placeholder='{"note":"primary route"}' />
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
import type { ModelRedirectConfigRsp } from '../../types/model'
import { formatJsonText, normalizeJsonText } from '../../utils/json'

interface RedirectFormModel {
  id?: number
  versionNo?: number
  aliasName: string
  providerCode: string
  targetModel: string
  enabled: boolean
  priority: number
  routeStrategy: string
  weight: number
  matchConditionJson: string
  extConfigJson: string
}

const props = defineProps<{
  visible: boolean
  modelValue?: ModelRedirectConfigRsp | null
}>()

const emit = defineEmits<{
  close: []
  submit: [payload: RedirectFormModel]
}>()

const formRef = ref<FormInstance>()
const form = reactive<RedirectFormModel>(createEmptyForm())
const isEdit = ref(false)

const rules: FormRules<RedirectFormModel> = {
  aliasName: [{ required: true, message: '请输入 aliasName', trigger: 'blur' }],
  providerCode: [{ required: true, message: '请输入 providerCode', trigger: 'blur' }],
  targetModel: [{ required: true, message: '请输入 targetModel', trigger: 'blur' }],
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
      aliasName: value.aliasName,
      providerCode: value.providerCode,
      targetModel: value.targetModel,
      enabled: value.enabled,
      priority: value.priority,
      routeStrategy: value.routeStrategy,
      weight: value.weight,
      matchConditionJson: formatJsonText(value.matchConditionJson),
      extConfigJson: formatJsonText(value.extConfigJson),
    })
  },
  { immediate: true },
)

function createEmptyForm(): RedirectFormModel {
  return {
    aliasName: '',
    providerCode: '',
    targetModel: '',
    enabled: true,
    priority: 0,
    routeStrategy: 'PRIORITY',
    weight: 100,
    matchConditionJson: '',
    extConfigJson: '',
  }
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
    matchConditionJson: normalizeJsonText(form.matchConditionJson),
    extConfigJson: normalizeJsonText(form.extConfigJson),
  })
}
</script>
