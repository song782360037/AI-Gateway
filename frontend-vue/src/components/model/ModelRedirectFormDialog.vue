<template>
  <el-dialog
    :model-value="visible"
    :title="isEdit ? '编辑模型路由规则' : '新增模型路由规则'"
    width="840px"
    destroy-on-close
    class="admin-dialog"
    modal-class="admin-dialog-overlay"
    @close="emit('close')"
  >
    <div class="dialog-intro">
      <p class="eyebrow">模型路由规则编辑</p>
      <p>维护对外模型、目标 Provider 与目标模型之间的后台路由规则。</p>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <section class="dialog-section">
        <div class="dialog-section__head">
          <h4>基础映射</h4>
          <p>定义对外模型、目标 Provider 和最终路由到的模型名称。</p>
        </div>
        <div class="form-grid">
          <el-form-item label="对外模型" prop="aliasName">
            <el-input v-model="form.aliasName" placeholder="gpt-4o" />
          </el-form-item>
          <el-form-item label="目标 Provider" prop="providerCode">
            <el-input
              v-model="form.providerCode"
              placeholder="openai-main"
              :disabled="providerCodeLocked"
            />
          </el-form-item>
          <el-form-item label="目标模型" prop="targetModel">
            <el-input v-model="form.targetModel" placeholder="gpt-4o-2024-11-20" />
          </el-form-item>
          <el-form-item label="路由策略（预留）" prop="routeStrategy">
            <el-select v-model="form.routeStrategy" disabled>
              <el-option v-for="item in routeStrategyOptions" :key="item" :label="item" :value="item" />
            </el-select>
            <p class="form-field-hint">当前版本仅支持优先级路由，其他策略暂未生效。</p>
          </el-form-item>
        </div>
      </section>

      <section class="dialog-section">
        <div class="dialog-section__head">
          <h4>调度参数</h4>
          <p>配置优先级与开关状态。weight 与其他路由策略为预留字段，当前版本仅按优先级路由。</p>
        </div>
        <div class="form-grid">
          <el-form-item label="priority" prop="priority">
            <el-input-number v-model="form.priority" :step="1" />
          </el-form-item>
          <el-form-item label="weight（预留）" prop="weight">
            <el-input-number v-model="form.weight" :min="0" :step="10" disabled />
          </el-form-item>
        </div>

        <div class="dialog-switch-row">
          <div>
            <p class="dialog-switch-row__title">启用状态</p>
            <p class="dialog-switch-row__desc">关闭后该规则不会进入运行时候选列表。</p>
          </div>
          <el-switch v-model="form.enabled" inline-prompt active-text="开" inactive-text="关" />
        </div>
      </section>

      <section class="dialog-section">
        <div class="dialog-section__head">
          <h4>条件与扩展</h4>
          <p>可选配置匹配条件和扩展信息，便于后续扩展更复杂的路由能力。</p>
        </div>
        <el-form-item label="matchConditionJson" prop="matchConditionJson">
          <el-input v-model="form.matchConditionJson" type="textarea" :rows="4" placeholder='{"tenant":"default"}' />
        </el-form-item>

        <el-form-item label="extConfigJson" prop="extConfigJson">
          <el-input v-model="form.extConfigJson" type="textarea" :rows="4" placeholder='{"note":"primary route"}' />
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
import type { ModelRedirectConfigRsp } from '../../types/model'
import { formatJsonText, isValidJsonText, normalizeJsonText } from '../../utils/json'

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

const routeStrategyOptions = ['PRIORITY', 'WEIGHT', 'FALLBACK'] as const

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
  aliasName: [{ required: true, message: '请输入 aliasName', trigger: 'blur' }],
  providerCode: [{ required: true, message: '请输入 providerCode', trigger: 'blur' }],
  targetModel: [{ required: true, message: '请输入 targetModel', trigger: 'blur' }],
  routeStrategy: [{ required: true, message: '请选择 routeStrategy', trigger: 'change' }],
  matchConditionJson: [{ validator: validateJsonText, trigger: 'blur' }],
  extConfigJson: [{ validator: validateJsonText, trigger: 'blur' }],
}

watch(
  [() => props.modelValue, () => props.lockedProviderCode, () => props.visible],
  ([value]) => {
    isEdit.value = !!value
    const nextForm = buildFormState(value)

    // 记录表单初始值，保证编辑态点击“重置”后回到原始配置，而不是回到默认空表单。
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
    priority: value.priority,
    routeStrategy: value.routeStrategy,
    weight: value.weight,
    matchConditionJson: formatJsonText(value.matchConditionJson),
    extConfigJson: formatJsonText(value.extConfigJson),
  }
}

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
    aliasName: form.aliasName,
    providerCode: form.providerCode,
    targetModel: form.targetModel,
    enabled: form.enabled,
    priority: form.priority,
    routeStrategy: form.routeStrategy,
    weight: form.weight,
    matchConditionJson: normalizeJsonText(form.matchConditionJson),
    extConfigJson: normalizeJsonText(form.extConfigJson),
  }

  emit('submit', isEdit.value ? { ...basePayload, id: form.id, versionNo: form.versionNo } : basePayload)
}
</script>

<style scoped>
.form-field-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-secondary, #909399);
  line-height: 1.4;
}
</style>
