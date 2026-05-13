<template>
  <el-dialog
    :model-value="visible"
    title="全局自定义请求头"
    width="680px"
    destroy-on-close
    class="admin-dialog"
    modal-class="admin-dialog-overlay"
    @close="emit('close')"
  >
    <div class="dialog-intro">
      <p class="eyebrow">请求头配置</p>
      <p>
        设置全局通用的自定义请求头，所有提供商共用。特定提供商可单独设置同名头覆盖全局配置。
        不允许设置认证相关头（Authorization、x-api-key、x-goog-api-key、anthropic-version）。
      </p>
    </div>

    <div v-loading="loading">
      <div v-for="(item, index) in headersList" :key="index" class="custom-header-row">
        <el-input
          v-model="item.key"
          placeholder="Header 名称"
          class="custom-header-input"
          @change="validateHeaderKey(index)"
        />
        <el-input
          v-model="item.value"
          placeholder="Header 值"
          class="custom-header-input"
          @blur="validateHeaderValue(index)"
        />
        <el-button
          type="danger"
          plain
          size="small"
          @click="removeHeader(index)"
        >
          删除
        </el-button>
      </div>
      <el-button type="primary" plain size="small" @click="addHeader">
        + 添加请求头
      </el-button>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="emit('close')">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchGlobalCustomHeaders, updateGlobalCustomHeaders } from '../../api/provider-config'
import { PROTECTED_HEADERS, VALID_HEADER_NAME_REGEX, hasCrlf } from '../../constants/customHeaders'

/** 自定义请求头列表项 */
interface HeaderItem {
  key: string
  value: string
}

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  close: []
}>()

const loading = ref(false)
const saving = ref(false)
const versionNo = ref(0)
const headersList = reactive<HeaderItem[]>([])

watch(
  () => props.visible,
  (val) => {
    if (val) {
      loadHeaders()
    }
  },
  { immediate: true },
)

async function loadHeaders() {
  loading.value = true
  try {
    const result = await fetchGlobalCustomHeaders()
    versionNo.value = result.versionNo
    headersList.splice(0, headersList.length)
    if (result.customHeaders) {
      for (const [key, value] of Object.entries(result.customHeaders)) {
        headersList.push({ key, value })
      }
    }
  } catch {
    ElMessage.error('加载全局请求头失败')
  } finally {
    loading.value = false
  }
}

function addHeader() {
  headersList.push({ key: '', value: '' })
}

function removeHeader(index: number) {
  headersList.splice(index, 1)
}

/** 校验请求头名称是否为受保护头或非法名称 */
function validateHeaderKey(index: number) {
  const item = headersList[index]
  if (!item || !item.key) return
  const trimmed = item.key.trim()
  if (!trimmed) return
  if (!VALID_HEADER_NAME_REGEX.test(trimmed)) {
    ElMessage.warning(`请求头名称包含非法字符: ${item.key}`)
    item.key = ''
    return
  }
  if (PROTECTED_HEADERS.has(trimmed.toLowerCase())) {
    ElMessage.warning(`不允许设置认证相关头: ${trimmed}`)
    item.key = ''
    return
  }
  // 校验值中不包含换行符
  if (item.value && hasCrlf(item.value)) {
    ElMessage.warning(`请求头值不允许包含换行符: ${trimmed}`)
    item.value = ''
  }
}

/** 将列表形式转换回 Map */
function headersListToMap(): Record<string, string> {
  const result: Record<string, string> = {}
  for (const item of headersList) {
    if (item.key.trim()) {
      result[item.key.trim()] = item.value
    }
  }
  return result
}

/** 校验请求头值是否包含换行符 */
function validateHeaderValue(index: number) {
  const item = headersList[index]
  if (!item || !item.value) return
  if (hasCrlf(item.value)) {
    ElMessage.warning('请求头值不允许包含换行符')
    item.value = ''
  }
}

async function save() {
  // 校验无受保护头和非法字符
  for (const item of headersList) {
    if (!item.key) continue
    const trimmed = item.key.trim()
    if (!trimmed) continue
    if (!VALID_HEADER_NAME_REGEX.test(trimmed)) {
      ElMessage.error(`请求头名称包含非法字符: ${trimmed}`)
      return
    }
    if (PROTECTED_HEADERS.has(trimmed.toLowerCase())) {
      ElMessage.error(`不允许设置认证相关头: ${trimmed}`)
      return
    }
    // 校验值中不包含换行符，防止 HTTP 头注入
    if (item.value && hasCrlf(item.value)) {
      ElMessage.error(`请求头值不允许包含换行符: ${trimmed}`)
      return
    }
  }

  saving.value = true
  try {
    await updateGlobalCustomHeaders({
      versionNo: versionNo.value,
      customHeaders: headersListToMap(),
    })
    ElMessage.success('全局请求头更新成功')
    emit('close')
  } catch {
    // 请求层已统一处理错误提示
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
/* 自定义请求头行布局 */
.custom-header-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.custom-header-input {
  flex: 1;
}
</style>
