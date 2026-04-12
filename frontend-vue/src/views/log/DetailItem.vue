<template>
  <div class="detail-item">
    <div class="detail-item__label">{{ label }}</div>
    <div class="detail-item__value" :class="{ 'is-highlight': highlight }">
      <span>{{ displayValue }}</span>
      <button v-if="copyable && hasValue" class="detail-item__copy" type="button" @click="copyValue">复制</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'

const props = withDefaults(
  defineProps<{
    label: string
    value?: string | number | boolean | null
    highlight?: boolean
    copyable?: boolean
  }>(),
  {
    value: null,
    highlight: false,
    copyable: false,
  },
)

const hasValue = computed(() => props.value !== null && props.value !== undefined && props.value !== '')
const displayValue = computed(() => (hasValue.value ? String(props.value) : '-'))

async function copyValue() {
  if (!hasValue.value) {
    return
  }
  try {
    await navigator.clipboard.writeText(String(props.value))
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}
</script>

<style scoped>
.detail-item {
  min-width: 0;
}

.detail-item__label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.detail-item__value {
  min-height: 22px;
  line-height: 22px;
  color: var(--text-primary);
  word-break: break-all;
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-item__value.is-highlight {
  color: var(--el-color-danger);
  font-weight: 500;
}

.detail-item__copy {
  border: none;
  background: transparent;
  color: var(--el-color-primary);
  cursor: pointer;
  padding: 0;
  font-size: 12px;
}
</style>
