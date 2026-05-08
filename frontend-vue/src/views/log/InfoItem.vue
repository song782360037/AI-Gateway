<template>
  <div class="info-item">
    <div class="info-item__label">{{ label }}</div>
    <div class="info-item__value" :class="{ 'is-highlight': highlight }">
      <span>{{ displayValue }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    label: string
    value?: string | number | boolean | null
    highlight?: boolean
  }>(),
  {
    value: null,
    highlight: false,
  },
)

const hasValue = computed(
  () => props.value !== null && props.value !== undefined && props.value !== '',
)
const displayValue = computed(() => (hasValue.value ? String(props.value) : '-'))
</script>

<style scoped>
.info-item {
  min-width: 0;
}

.info-item__label {
  font-size: 11px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.info-item__value {
  min-height: 20px;
  line-height: 20px;
  color: var(--text-primary);
  word-break: break-all;
  font-size: 13px;
}

.info-item__value.is-highlight {
  color: var(--el-color-danger);
  font-weight: 500;
}
</style>
