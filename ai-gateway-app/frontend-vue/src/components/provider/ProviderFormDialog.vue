<template>
  <el-dialog
    :model-value="visible"
    :title="isEdit ? '编辑提供商' : '新增提供商'"
    width="860px"
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
            <el-select
              v-model="form.providerType"
              placeholder="请选择提供商类型"
              style="width: 100%"
            >
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

      <!-- 调度配置 -->
      <section class="dialog-section">
        <div class="dialog-section__head">
          <h4>调度配置</h4>
          <p>配置 Key 选择策略、请求超时和优先级，决定实际调用行为。API Key 在创建后通过 Key 管理面板添加。</p>
        </div>
        <div class="form-grid">
          <el-form-item label="Key 选择策略" prop="keySelectionStrategy">
            <el-select v-model="form.keySelectionStrategy" style="width: 100%">
              <el-option
                v-for="item in keySelectionStrategyOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="超时（秒）" prop="timeoutSeconds">
            <el-input-number
              v-model="form.timeoutSeconds"
              :min="1"
              :step="10"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="优先级" prop="priority">
            <el-input-number v-model="form.priority" :step="1" style="width: 100%" />
          </el-form-item>
          <el-form-item label="启用状态">
            <el-switch v-model="form.enabled" inline-prompt active-text="开" inactive-text="关" />
          </el-form-item>
        </div>
      </section>

      <!-- 支持协议 -->
      <section class="dialog-section">
        <div class="dialog-section__head">
          <h4>支持协议</h4>
          <p>选择该提供商支持的下游请求协议。不勾选表示支持所有协议。</p>
        </div>
        <div class="protocol-card-grid">
          <div
            v-for="item in protocolOptions"
            :key="item.value"
            class="protocol-card"
            :class="{ 'protocol-card--active': form.supportedProtocols.includes(item.value) }"
            tabindex="0"
            role="checkbox"
            :aria-checked="form.supportedProtocols.includes(item.value)"
            @click="toggleProtocol(item.value)"
            @keyup.enter="toggleProtocol(item.value)"
            @keyup.space.prevent="toggleProtocol(item.value)"
          >
            <div class="protocol-card__check">
              <el-icon v-if="form.supportedProtocols.includes(item.value)" :size="16" color="var(--color-primary)">
                <Check />
              </el-icon>
              <div v-else class="protocol-unchecked-box" />
            </div>
            <div class="protocol-card__body">
              <span class="protocol-card__label">{{ item.label }}</span>
              <span class="protocol-card__desc">{{ item.desc }}</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 自定义请求头 -->
      <section class="dialog-section">
        <div class="dialog-section__head">
          <h4>自定义请求头</h4>
          <p>
            针对此提供商单独设置请求头，会覆盖全局同名头。
            不允许设置认证相关头（Authorization、x-api-key、x-goog-api-key、anthropic-version）。
          </p>
        </div>
        <div v-if="form.customHeadersList.length === 0" class="custom-header-empty">
          <el-icon :size="32" color="var(--el-text-color-placeholder)">
            <Link />
          </el-icon>
          <p>暂无自定义请求头，点击下方按钮添加</p>
        </div>
        <div
          v-for="(item, index) in form.customHeadersList"
          :key="index"
          class="custom-header-row"
        >
          <div class="custom-header-card">
            <el-input
              v-model="item.key"
              placeholder="Header 名称"
              class="custom-header-input"
              @change="validateHeaderKey(index)"
            />
            <span class="custom-header-divider">:</span>
            <el-input
              v-model="item.value"
              placeholder="Header 值"
              class="custom-header-input"
              @blur="validateHeaderKey(index)"
            />
            <el-tooltip content="删除此行">
              <el-button type="danger" link :icon="Delete" @click="removeHeader(index)" />
            </el-tooltip>
          </div>
        </div>
        <el-button type="primary" plain size="small" :icon="Plus" @click="addHeader">
          添加请求头
        </el-button>
      </section>

      <!-- Thinking 兼容模式 -->
      <section class="dialog-section">
        <div class="dialog-section__head">
          <h4>Thinking 兼容模式</h4>
          <p>
            控制发送给上游的 thinking 参数格式。第三方 API（如 MiMo）不支持扩展字段，需选择「简化模式」避免 400 错误。
          </p>
        </div>
        <div class="thinking-card-grid">
          <div
            v-for="item in thinkingCompatOptions"
            :key="item.value"
            class="thinking-card"
            :class="{ 'thinking-card--active': form.thinkingCompatMode === item.value }"
            tabindex="0"
            role="radio"
            :aria-checked="form.thinkingCompatMode === item.value"
            @click="form.thinkingCompatMode = item.value"
            @keyup.enter="form.thinkingCompatMode = item.value"
            @keyup.space.prevent="form.thinkingCompatMode = item.value"
          >
            <div class="thinking-card__check">
              <div class="thinking-check-circle">
                <div v-if="form.thinkingCompatMode === item.value" class="thinking-check-dot" />
              </div>
            </div>
            <div class="thinking-card__body">
              <strong>{{ item.label.split(' — ')[0] }}</strong>
              <p>{{ item.label.split(' — ')[1] }}</p>
            </div>
          </div>
        </div>
      </section>

      <!-- API Key 管理 -->
      <section class="dialog-section">
        <div class="dialog-section__head">
          <div style="display: flex; justify-content: space-between; align-items: center">
            <div>
              <h4>API Key 管理</h4>
              <p>为此提供商管理 API Key，至少保留一个启用的 Key。</p>
            </div>
            <el-button type="primary" size="small" @click="openAddKeyDialog">
              <el-icon style="margin-right: 4px"><Plus /></el-icon>添加 Key
            </el-button>
          </div>
        </div>
        <el-table
          :data="displayApiKeys"
          v-loading="apiKeyLoading"
          size="small"
          style="width: 100%"
          empty-text="暂无 API Key，请点击上方按钮添加"
        >
          <el-table-column prop="apiKeyMasked" label="API Key" min-width="160" />
          <el-table-column prop="remark" label="备注" min-width="120">
            <template #default="{ row }">
              {{ row.remark || '—' }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="70" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.enabled ? 'success' : 'info'">
                {{ row.enabled ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="weight" label="权重" width="60" align="center" />
          <el-table-column prop="sortOrder" label="排序" width="60" align="center" />
          <el-table-column label="操作" width="120" align="center">
            <template #default="{ row }">
              <el-tooltip content="编辑">
                <el-button link type="primary" :icon="Edit" @click="openEditKeyDialog(row)" />
              </el-tooltip>
              <el-tooltip :content="row.enabled ? '禁用' : '启用'">
                <el-button
                  link
                  :type="row.enabled ? 'warning' : 'success'"
                  :icon="row.enabled ? Remove : CirclePlus"
                  @click="handleToggleKey(row)"
                />
              </el-tooltip>
              <el-tooltip content="删除">
                <el-button link type="danger" :icon="Delete" @click="handleDeleteKey(row)" />
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </el-form>

    <!-- Key 添加/编辑弹窗（嵌套在 Provider 编辑弹窗内） -->
    <el-dialog
      v-model="keyDialogVisible"
      :title="editingKey ? '编辑 Key' : '添加 Key'"
      width="480px"
      destroy-on-close
      append-to-body
      class="admin-dialog"
      modal-class="admin-dialog-overlay"
    >
      <el-form :model="keyForm" :rules="keyRules" ref="keyFormRef" label-position="top">
        <el-form-item v-if="!editingKey" label="API Key" prop="apiKey">
          <el-input
            v-model="keyForm.apiKey"
            type="password"
            show-password
            placeholder="输入 API Key 明文"
          />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="keyForm.remark" placeholder="如：生产主 Key、备用 Key" />
        </el-form-item>
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px">
          <el-form-item label="权重" prop="weight">
            <el-input-number v-model="keyForm.weight" :min="1" :step="10" style="width: 100%" />
          </el-form-item>
          <el-form-item label="排序号" prop="sortOrder">
            <el-input-number v-model="keyForm.sortOrder" :min="0" :step="1" style="width: 100%" />
          </el-form-item>
        </div>
        <el-form-item v-if="editingKey" label="启用状态">
          <el-switch
            v-model="keyForm.enabled"
            inline-prompt
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="keyDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitKeyForm">保存</el-button>
      </template>
    </el-dialog>

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
import { computed, onMounted, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Edit, Remove, CirclePlus, Check, Link } from '@element-plus/icons-vue'
import type {
  ProviderConfigAddReq,
  ProviderConfigRsp,
  ProviderConfigUpdateReq,
  ThinkingCompatMode,
  KeySelectionStrategy,
  ProviderApiKeyRsp,
  ProviderApiKeyAddReq,
  ProviderApiKeyUpdateReq,
} from '../../types/provider'
import { PROTECTED_HEADERS, VALID_HEADER_NAME_REGEX, hasCrlf } from '../../constants/customHeaders'
import {
  fetchApiKeys,
  addApiKey,
  updateApiKey,
  deleteApiKey,
  toggleApiKey,
} from '../../api/provider-api-key'

/** 自定义请求头列表项 */
interface HeaderItem {
  key: string
  value: string
}

/** 新增模式下本地维护的 API Key 项 */
interface LocalApiKey {
  _tempId: number
  apiKey: string
  remark: string
  enabled: boolean
  weight: number
  sortOrder: number
}

interface ProviderFormModel {
  id?: number
  versionNo?: number
  providerCode: string
  providerType: string
  displayName: string
  enabled: boolean
  baseUrl: string
  keySelectionStrategy: KeySelectionStrategy
  timeoutSeconds: number
  priority: number
  supportedProtocols: string[]
  customHeadersList: HeaderItem[]
  thinkingCompatMode: ThinkingCompatMode
}

const providerTypeOptions = [
  { value: 'OPENAI', label: 'OpenAI Chat Completions' },
  { value: 'OPENAI_RESPONSES', label: 'OpenAI Responses API' },
  { value: 'ANTHROPIC', label: 'Anthropic (Claude)' },
  { value: 'GEMINI', label: 'Google Gemini' },
] as const

/** 下游协议选项 */
const protocolOptions = [
  { value: 'OPENAI_CHAT', label: 'OpenAI Chat', desc: '/v1/chat/completions' },
  { value: 'OPENAI_RESPONSES', label: 'OpenAI Responses', desc: '/v1/responses' },
  { value: 'ANTHROPIC', label: 'Anthropic', desc: '/v1/messages' },
  { value: 'GEMINI', label: 'Gemini', desc: '/v1beta/models/:generateContent' },
  { value: 'OPENAI_EMBEDDING', label: 'OpenAI Embedding', desc: '/v1/embeddings' },
  { value: 'RERANK', label: 'Rerank', desc: '/v1/rerank' },
] as const

/** thinking 兼容模式选项 */
const thinkingCompatOptions = [
  { value: 'full', label: '完整模式 — 输出 budget_tokens、summary 等官方字段（适用于 Claude、DeepSeek）' },
  { value: 'simplified', label: '简化模式 — 仅输出 type 字段（适用于 MiMo 等第三方兼容 API）' },
] as const

/** Key 选择策略选项 */
const keySelectionStrategyOptions = [
  { value: 'ROUND_ROBIN', label: '轮询 — 依次选择' },
  { value: 'RANDOM', label: '加权随机 — 按权重随机选择' },
  { value: 'FALLBACK', label: '降级 — 按排序号优先，失败后切换下一个' },
] as const

const props = defineProps<{
  visible: boolean
  modelValue?: ProviderConfigRsp | null
}>()

const emit = defineEmits<{
  close: []
  submit: [payload: ProviderConfigAddReq | ProviderConfigUpdateReq]
}>()

const formRef = ref<FormInstance>()
const form = reactive<ProviderFormModel>(createEmptyForm())
const initialSnapshot = ref<ProviderFormModel>(createEmptyForm())
const isEdit = ref(false)

const rules: FormRules<ProviderFormModel> = {
  providerCode: [{ required: true, message: '请输入提供商唯一标识', trigger: 'blur' }],
  providerType: [{ required: true, message: '请选择提供商类型', trigger: 'change' }],
  baseUrl: [{ required: true, message: '请输入接口地址', trigger: 'blur' }],
  thinkingCompatMode: [{ required: true, message: '请选择 Thinking 兼容模式', trigger: 'change' }],
}

function syncFormState(value?: ProviderConfigRsp | null) {
  isEdit.value = !!value
  const nextForm = buildFormState(value)

  // 保留一份初始快照，编辑态点击"重置"时可以回到原始值
  initialSnapshot.value = nextForm
  Object.assign(form, nextForm)

  // 切换模式时清空本地 Key 列表
  if (!value) {
    localApiKeys.value = []
  }

  if (value?.providerCode) {
    loadApiKeys(value.providerCode)
  } else {
    apiKeys.value = []
  }
}

onMounted(() => syncFormState(props.modelValue))

watch(() => props.modelValue, syncFormState)

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
    keySelectionStrategy: (value.keySelectionStrategy as KeySelectionStrategy) || 'ROUND_ROBIN',
    timeoutSeconds: value.timeoutSeconds,
    priority: value.priority,
    supportedProtocols: value.supportedProtocols ?? [],
    customHeadersList: mapToHeadersList(value.customHeaders),
    thinkingCompatMode: value.thinkingCompatMode || 'full',
  }
}

function createEmptyForm(): ProviderFormModel {
  return {
    providerCode: '',
    providerType: 'OPENAI',
    displayName: '',
    enabled: true,
    baseUrl: '',
    keySelectionStrategy: 'ROUND_ROBIN',
    timeoutSeconds: 60,
    priority: 0,
    supportedProtocols: [],
    customHeadersList: [],
    thinkingCompatMode: 'full',
  }
}

/** 将 Map 形式的 customHeaders 转换为列表形式 */
function mapToHeadersList(headers?: Record<string, string>): HeaderItem[] {
  if (!headers) return []
  return Object.entries(headers).map(([key, value]) => ({ key, value }))
}

/** 将列表形式转换回 Map，空列表返回空对象以区分"未传"和"清空" */
function headersListToMap(list: HeaderItem[]): Record<string, string> {
  const result: Record<string, string> = {}
  for (const item of list) {
    if (item.key.trim()) {
      result[item.key.trim()] = item.value
    }
  }
  return result
}

/** 切换支持协议的选中状态 */
function toggleProtocol(value: string) {
  const idx = form.supportedProtocols.indexOf(value)
  if (idx > -1) {
    form.supportedProtocols.splice(idx, 1)
  } else {
    form.supportedProtocols.push(value)
  }
}

/** 校验请求头键名合法性及值中是否包含换行符 */
function validateHeaderKey(index: number) {
  const item = form.customHeadersList[index]
  if (!item) return

  // 校验值中不包含换行符（独立于 key 校验，blur 时也能触发）
  if (item.value && hasCrlf(item.value)) {
    ElMessage.warning('请求头值不允许包含换行符')
    item.value = ''
  }

  if (!item.key) return
  const trimmed = item.key.trim()
  if (!trimmed) return
  if (!VALID_HEADER_NAME_REGEX.test(trimmed)) {
    ElMessage.warning(`请求头名称包含非法字符: ${item.key}，请修改`)
    return
  }
  if (PROTECTED_HEADERS.has(trimmed.toLowerCase())) {
    ElMessage.warning(`不允许设置认证相关头: ${trimmed}，请修改`)
  }
}

function addHeader() {
  form.customHeadersList.push({ key: '', value: '' })
}

function removeHeader(index: number) {
  form.customHeadersList.splice(index, 1)
}

function resetForm() {
  Object.assign(form, initialSnapshot.value)
  formRef.value?.clearValidate()
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  // 校验自定义头中无受保护头和非法字符
  for (const item of form.customHeadersList) {
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

  const basePayload = {
    providerCode: form.providerCode,
    providerType: form.providerType,
    displayName: form.displayName,
    enabled: form.enabled,
    baseUrl: form.baseUrl,
    keySelectionStrategy: form.keySelectionStrategy,
    timeoutSeconds: form.timeoutSeconds,
    priority: form.priority,
    supportedProtocols: form.supportedProtocols,
    customHeaders: headersListToMap(form.customHeadersList),
    thinkingCompatMode: form.thinkingCompatMode,
  }

  if (isEdit.value) {
    emit('submit', { ...basePayload, id: form.id, versionNo: form.versionNo })
  } else {
    const apiKeysPayload = localApiKeys.value.map((k) => ({
      providerCode: form.providerCode,
      apiKey: k.apiKey,
      remark: k.remark,
      enabled: k.enabled,
      weight: k.weight,
      sortOrder: k.sortOrder,
    }))
    emit('submit', { ...basePayload, apiKeys: apiKeysPayload })
  }
}

/* ==================== API Key 管理 ==================== */

const apiKeys = ref<ProviderApiKeyRsp[]>([])
const apiKeyLoading = ref(false)
const keyDialogVisible = ref(false)
const editingKey = ref<ProviderApiKeyRsp | null>(null)
const keyFormRef = ref<FormInstance>()
const keyForm = reactive({
  apiKey: '',
  remark: '',
  enabled: true,
  weight: 100,
  sortOrder: 0,
})
// 编辑模式下 apiKey 字段隐藏，不需要 required 校验
const keyRules = computed<FormRules>(() => ({
  apiKey: editingKey.value
    ? []
    : [{ required: true, message: '请输入 API Key', trigger: 'blur' }],
}))

/** 新增模式下本地维护的 API Key 列表 */
const localApiKeys = ref<LocalApiKey[]>([])
let localTempIdCounter = 0

/** 对 API Key 进行简易脱敏显示（前4位 + **** + 后4位） */
function maskApiKey(key: string): string {
  if (key.length <= 8) return '****' + key.slice(-4)
  return key.slice(0, 4) + '****' + key.slice(-4)
}

/** 表格展示用的统一数据源 */
const displayApiKeys = computed<ProviderApiKeyRsp[]>(() => {
  if (isEdit.value) return apiKeys.value
  return localApiKeys.value.map((k) => ({
    id: k._tempId,
    providerCode: form.providerCode,
    apiKeyMasked: maskApiKey(k.apiKey),
    remark: k.remark,
    enabled: k.enabled,
    weight: k.weight,
    sortOrder: k.sortOrder,
    versionNo: 0,
  }))
})

async function loadApiKeys(providerCode: string) {
  apiKeyLoading.value = true
  try {
    apiKeys.value = await fetchApiKeys(providerCode)
  } catch {
    ElMessage.error('加载 API Key 列表失败')
  } finally {
    apiKeyLoading.value = false
  }
}

function openAddKeyDialog() {
  editingKey.value = null
  keyForm.apiKey = ''
  keyForm.remark = ''
  keyForm.enabled = true
  keyForm.weight = 100
  keyForm.sortOrder = 0
  keyDialogVisible.value = true
}

function openEditKeyDialog(row: ProviderApiKeyRsp) {
  editingKey.value = row
  keyForm.apiKey = ''
  keyForm.remark = row.remark || ''
  keyForm.enabled = row.enabled
  keyForm.weight = row.weight
  keyForm.sortOrder = row.sortOrder
  keyDialogVisible.value = true
}

async function submitKeyForm() {
  const valid = await keyFormRef.value?.validate().catch(() => false)
  if (!valid) return

  if (isEdit.value) {
    // 编辑模式：调后端 API
    try {
      if (editingKey.value) {
        const req: ProviderApiKeyUpdateReq = {
          id: editingKey.value.id,
          versionNo: editingKey.value.versionNo,
          remark: keyForm.remark,
          enabled: keyForm.enabled,
          weight: keyForm.weight,
          sortOrder: keyForm.sortOrder,
        }
        await updateApiKey(req)
        ElMessage.success('Key 更新成功')
      } else {
        const req: ProviderApiKeyAddReq = {
          providerCode: form.providerCode,
          apiKey: keyForm.apiKey,
          remark: keyForm.remark,
          enabled: true,
          weight: keyForm.weight,
          sortOrder: keyForm.sortOrder,
        }
        await addApiKey(req)
        ElMessage.success('Key 添加成功')
      }
      keyDialogVisible.value = false
      await loadApiKeys(form.providerCode)
    } catch (e: any) {
      ElMessage.error(e?.message || '操作失败')
    }
  } else {
    // 新增模式：只操作本地列表
    if (editingKey.value) {
      const localKey = localApiKeys.value.find((k) => k._tempId === editingKey.value!.id)
      if (localKey) {
        localKey.remark = keyForm.remark
        localKey.enabled = keyForm.enabled
        localKey.weight = keyForm.weight
        localKey.sortOrder = keyForm.sortOrder
      }
      ElMessage.success('Key 更新成功')
    } else {
      localApiKeys.value.push({
        _tempId: ++localTempIdCounter,
        apiKey: keyForm.apiKey,
        remark: keyForm.remark,
        enabled: true,
        weight: keyForm.weight,
        sortOrder: keyForm.sortOrder,
      })
      ElMessage.success('Key 添加成功')
    }
    keyDialogVisible.value = false
  }
}

async function handleToggleKey(row: ProviderApiKeyRsp) {
  if (isEdit.value) {
    try {
      await toggleApiKey(row.id, row.versionNo, !row.enabled)
      ElMessage.success(row.enabled ? '已禁用' : '已启用')
      await loadApiKeys(form.providerCode)
    } catch (e: any) {
      ElMessage.error(e?.message || '操作失败')
    }
  } else {
    const localKey = localApiKeys.value.find((k) => k._tempId === row.id)
    if (localKey) {
      localKey.enabled = !localKey.enabled
      ElMessage.success(localKey.enabled ? '已启用' : '已禁用')
    }
  }
}

async function handleDeleteKey(row: ProviderApiKeyRsp) {
  if (isEdit.value) {
    try {
      await ElMessageBox.confirm('确定删除此 API Key？删除后不可恢复。', '确认删除', {
        type: 'warning',
      })
      await deleteApiKey(row.id)
      ElMessage.success('已删除')
      await loadApiKeys(form.providerCode)
    } catch (e: any) {
      if (e !== 'cancel') {
        ElMessage.error(e?.message || '删除失败')
      }
    }
  } else {
    localApiKeys.value = localApiKeys.value.filter((k) => k._tempId !== row.id)
    ElMessage.success('已删除')
  }
}
</script>

<style scoped>
/* 支持协议卡片网格 */
.protocol-card-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.protocol-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  border: 1.5px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-card);
  cursor: pointer;
  transition: all 0.2s ease;
}

.protocol-card:hover {
  border-color: var(--color-primary);
  background: rgba(var(--color-primary-rgb), 0.02);
}

.protocol-card--active {
  border-color: var(--color-primary);
  background: rgba(var(--color-primary-rgb), 0.04);
}

.protocol-card__check {
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 1px;
}

.protocol-unchecked-box {
  width: 14px;
  height: 14px;
  border: 1.5px solid var(--border-color);
  border-radius: 3px;
  transition: all 0.2s ease;
}

.protocol-card:hover .protocol-unchecked-box {
  border-color: var(--color-primary);
}

.protocol-card--active .protocol-unchecked-box {
  display: none;
}

.protocol-card__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.protocol-card__label {
  font-size: 13px;
  color: var(--text-primary);
  user-select: none;
  line-height: 1.4;
}

.protocol-card__desc {
  font-size: 11px;
  color: var(--text-tertiary, var(--text-secondary));
  user-select: none;
  line-height: 1.4;
  font-family: var(--font-mono, monospace);
}

/* 自定义请求头空状态 */
.custom-header-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 24px;
  margin-bottom: 12px;
  border: 1px dashed var(--border-light);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  font-size: 13px;
}

/* 自定义请求头卡片行 */
.custom-header-row {
  margin-bottom: 8px;
}

.custom-header-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  background: var(--bg-card);
  transition: border-color 0.2s;
}

.custom-header-card:hover {
  border-color: var(--border-base);
}

.custom-header-input {
  flex: 1;
}

:deep(.custom-header-input .el-input__wrapper) {
  box-shadow: none !important;
  background: transparent;
  padding: 0;
}

.custom-header-divider {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);
  flex-shrink: 0;
  user-select: none;
}

/* Thinking 兼容模式卡片 */
.thinking-card-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.thinking-card {
  display: flex;
  gap: 12px;
  padding: 16px;
  border: 1.5px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-card);
  cursor: pointer;
  transition: all 0.2s ease;
}

.thinking-card:hover {
  border-color: var(--color-primary);
  background: rgba(var(--color-primary-rgb), 0.02);
}

.thinking-card--active {
  border-color: var(--color-primary);
  background: rgba(var(--color-primary-rgb), 0.04);
}

.thinking-card__check {
  flex-shrink: 0;
  padding-top: 2px;
}

.thinking-check-circle {
  width: 16px;
  height: 16px;
  border: 1.5px solid var(--border-color);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.thinking-card:hover .thinking-check-circle {
  border-color: var(--color-primary);
}

.thinking-card--active .thinking-check-circle {
  border-color: var(--color-primary);
}

.thinking-check-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-primary);
}

.thinking-card__body strong {
  font-size: 14px;
  color: var(--text-primary);
  line-height: 1.4;
}

.thinking-card__body p {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 6px;
  line-height: 1.5;
}
</style>
