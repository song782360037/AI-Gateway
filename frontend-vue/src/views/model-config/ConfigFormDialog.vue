<template>
  <el-dialog
    :model-value="visible"
    :title="isEdit ? '编辑 Auto 配置' : '新增 Auto 配置'"
    width="560px"
    class="admin-dialog"
    @update:model-value="$emit('update:visible', $event)"
    @closed="resetForm"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
      <el-form-item label="路由键" prop="routeKey">
        <el-input v-model.trim="form.routeKey" placeholder="default / coding" maxlength="64" />
      </el-form-item>
      <el-form-item label="配置名称" prop="displayName">
        <el-input v-model.trim="form.displayName" placeholder="如：默认智能路由" maxlength="128" />
      </el-form-item>
      <el-form-item label="策略" prop="selectionStrategy">
        <el-select v-model="form.selectionStrategy" style="width: 100%">
          <el-option label="智能评分策略" value="SMART_SCORE" />
        </el-select>
      </el-form-item>
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
          placeholder="说明该 auto 场景的用途"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="$emit('update:visible', false)">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ isEdit ? '保存' : '创建' }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules, FormItemRule } from 'element-plus'
import { addAutoRouteConfig, updateAutoRouteConfig } from '../../api/auto-route-config'
import type {
  AutoRouteConfigAddReq,
  AutoRouteConfigRsp,
  AutoRouteConfigUpdateReq,
} from '../../types/auto-route'

const props = defineProps<{
  visible: boolean
  isEdit: boolean
  editData?: AutoRouteConfigRsp
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'submit'): void
}>()

const formRef = ref<FormInstance>()
const submitting = ref(false)

// 表单数据，带默认值
const form = reactive({
  routeKey: 'default',
  displayName: '',
  description: '',
  enabled: true,
  selectionStrategy: 'SMART_SCORE',
})

// 路由键格式校验
const routeKeyRule: FormItemRule = {
  validator: (_rule, value: string, callback) => {
    if (!value) {
      callback(new Error('请输入路由键'))
      return
    }
    if (!/^[a-z0-9_-]{1,64}$/.test(value)) {
      callback(new Error('只能包含小写字母、数字、短横线和下划线'))
      return
    }
    callback()
  },
  trigger: 'blur',
}

const rules: FormRules = {
  routeKey: [routeKeyRule],
  displayName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  selectionStrategy: [{ required: true, message: '请选择策略', trigger: 'change' }],
}

// 编辑模式时填充表单
watch(
  () => props.visible,
  (val) => {
    if (!val || !props.isEdit || !props.editData) return
    form.routeKey = props.editData.routeKey
    form.displayName = props.editData.displayName
    form.description = props.editData.description ?? ''
    form.enabled = props.editData.enabled
    form.selectionStrategy = props.editData.selectionStrategy
  },
)

function resetForm() {
  form.routeKey = 'default'
  form.displayName = ''
  form.description = ''
  form.enabled = true
  form.selectionStrategy = 'SMART_SCORE'
  formRef.value?.resetFields()
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    if (props.isEdit && props.editData) {
      const payload: AutoRouteConfigUpdateReq = {
        id: props.editData.id,
        versionNo: props.editData.versionNo,
        ...form,
      }
      await updateAutoRouteConfig(payload)
      ElMessage.success('Auto 配置更新成功')
    } else {
      const payload: AutoRouteConfigAddReq = { ...form }
      await addAutoRouteConfig(payload)
      ElMessage.success('Auto 配置创建成功')
    }
    emit('update:visible', false)
    emit('submit')
  } catch {
    // 请求拦截器统一提示错误
  } finally {
    submitting.value = false
  }
}
</script>
