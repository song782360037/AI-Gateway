<template>
  <div class="expand-table-wrapper">
    <div class="expand-panel">
      <div class="expand-panel__head">
        <div class="expand-panel__title-wrap">
          <span class="expand-panel__title">模型路由规则</span>
          <span class="expand-panel__provider">{{ providerCode }}</span>
          <span v-if="!loading" class="expand-table-header__count">{{ routes.length }} 条</span>
        </div>
        <el-button type="primary" size="small" @click="emit('addRedirect', providerCode)">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>添加路由规则
        </el-button>
      </div>

      <div class="expand-panel__body">
        <el-table
          v-loading="loading"
          :data="routes"
          stripe
          size="small"
          element-loading-text="加载中..."
        >
          <el-table-column prop="aliasName" label="对外模型" min-width="140" />
          <el-table-column prop="targetModel" label="实际模型" min-width="200" />
          <el-table-column label="状态" min-width="80">
            <template #default="{ row }">
              <el-tag
                :class="row.enabled ? 'status-chip status-chip--success' : 'status-chip status-chip--muted'"
                :type="row.enabled ? 'success' : 'info'"
                size="small"
              >
                {{ row.enabled ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="priority" label="优先级" min-width="80" />
          <el-table-column label="操作" fixed="right" width="200">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="emit('editRedirect', row)">编辑</el-button>
              <el-button
                link
                :type="row.enabled ? 'warning' : 'success'"
                size="small"
                @click="emit('toggleRedirect', row)"
              >
                {{ row.enabled ? '禁用' : '启用' }}
              </el-button>
              <el-button link type="danger" size="small" @click="emit('deleteRedirect', row)">删除</el-button>
            </template>
          </el-table-column>

          <template #empty>
            <div class="table-empty-state expand-empty-state">
              <strong>暂无路由规则</strong>
              <p>点击"添加路由规则"为该 Provider 配置模型映射</p>
            </div>
          </template>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { fetchModelRedirectsByProvider } from '../../api/model-redirect-config'
import type { ModelRedirectConfigRsp } from '../../types/model'

const props = defineProps<{
  providerCode: string
}>()

const emit = defineEmits<{
  addRedirect: [providerCode: string]
  editRedirect: [item: ModelRedirectConfigRsp]
  toggleRedirect: [item: ModelRedirectConfigRsp]
  deleteRedirect: [item: ModelRedirectConfigRsp]
}>()

const routes = ref<ModelRedirectConfigRsp[]>([])
const loading = ref(false)

/** 加载当前 Provider 的路由规则 */
async function loadRoutes(): Promise<void> {
  loading.value = true
  try {
    const result = await fetchModelRedirectsByProvider(props.providerCode)
    routes.value = result.list
  } catch {
    routes.value = []
  } finally {
    loading.value = false
  }
}

/** 供父组件调用，操作完成后刷新列表 */
function refresh() {
  return loadRoutes()
}

watch(
  () => props.providerCode,
  () => {
    loadRoutes()
  },
  { immediate: true },
)

defineExpose({ refresh })
</script>
