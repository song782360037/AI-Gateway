<template>
  <el-dialog
    :model-value="visible"
    :title="isEdit ? '编辑模型路由规则' : '新增模型路由规则'"
    width="600px"
    destroy-on-close
    class="admin-dialog"
    modal-class="admin-dialog-overlay"
    @close="emit('close')"
  >
    <div class="dialog-intro">
      <p class="eyebrow">模型路由规则</p>
      <p>定义对外模型名称到目标提供商及实际模型的映射关系。</p>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <section class="dialog-section">
        <div class="dialog-section__head">
          <h4>路由映射</h4>
          <p>配置对外暴露的模型名称、目标提供商和实际转发的模型标识。</p>
        </div>
        <div class="form-grid">
          <el-form-item label="对外模型名称" prop="aliasName">
            <el-input v-model="form.aliasName" placeholder="如 gpt-4o" />
          </el-form-item>
          <el-form-item label="目标提供商" prop="providerCode">
            <el-input
              v-model="form.providerCode"
              placeholder="如 openai-main"
              :disabled="providerCodeLocked"
            />
          </el-form-item>
        </div>
        <el-form-item label="目标模型标识" prop="targetModel">
          <el-input v-model="form.targetModel" placeholder="如 gpt-4o-2024-11-20" />
        </el-form-item>
      </section>

      <section class="dialog-section">
        <div class="dialog-switch-row">
          <div>
            <p class="dialog-switch-row__title">启用状态</p>
            <p class="dialog-switch-row__desc">关闭后该规则不会进入运行时候选列表。</p>
          </div>
          <el-switch v-model="form.enabled" inline-prompt active-text="开" inactive-text="关" />
        </div>
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
import type { ModelRedirectConfigRsp } from '../../types/model'

interface RedirectFormModel {
  id?: number
  versionNo?: number
  aliasName: string
  providerCode: string
  targetModel: string
  enabled: boolean
}

const props = defineProps<{
  visible: boolean
  modelValue?: ModelRedirectConfigRsp | null
  /** 是否锁定 providerCode 字段（从展开行使用时自动锁定） */
  providerCodeLocked?: boolean
  /** 锁定时的 providerCode 预填值 */
  lockedProviderCode?: string
}>()

const emit = defineEmits<{
  close: []
  submit: [payload: RedirectFormModel]
}>()

const formRef = ref<FormInstance>()
const form = reactive<RedirectFormModel>(createEmptyForm())
const initialSnapshot = ref<RedirectFormModel>(createEmptyForm())
const isEdit = ref(false)

const rules: FormRules<RedirectFormModel> = {
  aliasName: [{ required: true, message: '请输入对外模型名称', trigger: 'blur' }],
  providerCode: [{ required: true, message: '请输入目标提供商', trigger: 'blur' }],
  targetModel: [{ required: true, message: '请输入目标模型标识', trigger: 'blur' }],
}

watch(
  [() => props.modelValue, () => props.lockedProviderCode, () => props.visible],
  ([value]) => {
    isEdit.value = !!value
    const nextForm = buildFormState(value)

    // 记录表单初始值，保证编辑态点击"重置"后回到原始配置
    initialSnapshot.value = { ...nextForm }
    Object.assign(form, nextForm)
  },
  { immediate: true },
)

function buildFormState(value?: ModelRedirectConfigRsp | null): RedirectFormModel {
  if (!value) {
    return {
      ...createEmptyForm(),
      // 锁定模式下预填 providerCode
      providerCode: props.lockedProviderCode ?? '',
    }
  }

  return {
    id: value.id,
    versionNo: value.versionNo,
    aliasName: value.aliasName,
    providerCode: value.providerCode,
    targetModel: value.targetModel,
    enabled: value.enabled,
  }
}

function createEmptyForm(): RedirectFormModel {
  return {
    aliasName: '',
    providerCode: '',
    targetModel: '',
    enabled: true,
  }
}

function resetForm() {
  Object.assign(form, initialSnapshot.value)
  formRef.value?.clearValidate()
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  const basePayload = {
    aliasName: form.aliasName,
    providerCode: form.providerCode,
    targetModel: form.targetModel,
    enabled: form.enabled,
  }

  emit('submit', isEdit.value ? { ...basePayload, id: form.id, versionNo: form.versionNo } : basePayload)
}
</script>
